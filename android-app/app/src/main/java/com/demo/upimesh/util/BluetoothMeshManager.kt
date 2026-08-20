package com.demo.upimesh.util

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.demo.upimesh.model.MeshPacket
import com.google.gson.Gson
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Real Bluetooth Mesh Manager implementing:
 * - A → B → C → D store-and-forward mesh
 * - Deduplication via seenPacketIds (ConcurrentHashMap for thread safety)
 * - TTL/hop limit: packets are dropped or relayed based on remaining TTL
 * - Packet expiry: packets older than MAX_PACKET_AGE_MS are discarded
 * - Persist-before-forward: relay callback persists packet to Room before forwarding
 * - Duplicate prevention: packetId seen set prevents replay attacks
 */
class BluetoothMeshManager(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothMeshManager"
        private const val SERVICE_UUID_STR = "00001101-0000-1000-8000-00805F9B34FB"
        /** Maximum hops a packet can travel across the mesh. */
        const val MAX_TTL = 5
        /** Packets older than 30 minutes are considered expired and dropped. */
        private const val MAX_PACKET_AGE_MS = 30 * 60 * 1000L
        /** Maximum packet size in bytes — prevents malformed data from filling buffers. */
        private const val MAX_PACKET_SIZE_BYTES = 65536
    }

    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val gson = Gson()
    private val serviceUuid: UUID = UUID.fromString(SERVICE_UUID_STR)

    /**
     * Thread-safe set of already-seen packet IDs.
     * ConcurrentHashMap used as a Set to prevent race conditions between accept and client threads.
     */
    private val seenPacketIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val connectedThreads = mutableListOf<ConnectedThread>()
    private var packetCallback: ((MeshPacket) -> Unit)? = null
    private var relayPersistCallback: ((MeshPacket) -> Unit)? = null
    private var peerCallback: ((List<String>) -> Unit)? = null
    private var serverThread: AcceptThread? = null
    private var discoveryReceiver: BroadcastReceiver? = null

    /**
     * Start the mesh node.
     * @param packetCallback Called when this node is the intended final recipient or sender node.
     * @param relayPersistCallback Called when this node receives a packet to relay — use this to
     *   persist the packet to Room DB BEFORE forwarding (store-and-forward guarantee).
     * @param peerCallback Called whenever the list of connected peers changes.
     */
    fun start(
        packetCallback: (MeshPacket) -> Unit,
        relayPersistCallback: (MeshPacket) -> Unit,
        peerCallback: (List<String>) -> Unit
    ) {
        this.packetCallback = packetCallback
        this.relayPersistCallback = relayPersistCallback
        this.peerCallback = peerCallback
        if (adapter == null) return

        if (!hasRequiredBluetoothPermissions()) {
            Log.d(TAG, "Bluetooth permissions not granted; skipping startup")
            return
        }

        try {
            adapter.name = "UPI-Mesh-Node"
            registerDiscoveryReceiver()
            startListening()
            startDiscovery()
            connectToBondedDevices()
        } catch (e: SecurityException) {
            Log.d(TAG, "Bluetooth startup blocked: ${e.message}")
        } catch (e: Exception) {
            Log.d(TAG, "Bluetooth startup failed: ${e.message}")
        }
    }

    /**
     * Overload for backward compatibility: uses the same callback for both receipt and relay.
     */
    fun start(packetCallback: (MeshPacket) -> Unit, peerCallback: (List<String>) -> Unit) {
        start(packetCallback, packetCallback, peerCallback)
    }

    fun stop() {
        serverThread?.cancel()
        serverThread = null
        synchronized(connectedThreads) {
            connectedThreads.forEach { it.cancel() }
            connectedThreads.clear()
        }
        try {
            if (discoveryReceiver != null) {
                context.unregisterReceiver(discoveryReceiver)
            }
        } catch (_: IllegalArgumentException) {
        }
        discoveryReceiver = null
    }

    /**
     * Send a packet from this node into the mesh.
     * Marks the packetId as seen locally to prevent loopback.
     */
    fun sendPacket(packet: MeshPacket) {
        if (packet.packetId.isBlank()) return
        if (isExpired(packet)) {
            Log.d(TAG, "Refusing to send expired packet ${packet.packetId}")
            return
        }
        seenPacketIds.add(packet.packetId)
        synchronized(connectedThreads) {
            connectedThreads.toList().forEach { it.write(packet) }
        }
        Log.d(TAG, "Sent packet ${packet.packetId.take(8)} to ${connectedThreads.size} peer(s), TTL=${packet.ttl}")
    }

    fun startDiscovery() {
        if (!canUseBluetooth()) return
        try {
            if (adapter?.isDiscovering == true) adapter?.cancelDiscovery()
            adapter?.startDiscovery()
        } catch (e: SecurityException) {
            Log.d(TAG, "Discovery blocked: ${e.message}")
        }
    }

    private fun isExpired(packet: MeshPacket): Boolean {
        val age = System.currentTimeMillis() - packet.createdTimestamp
        return age > MAX_PACKET_AGE_MS
    }

    private fun registerDiscoveryReceiver() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        discoveryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        device?.let {
                            if (it.address != null && it.address != adapter?.address) {
                                val existing = synchronized(connectedThreads) {
                                    connectedThreads.any { thread -> thread.remoteAddress == it.address }
                                }
                                if (!existing) {
                                    connectToDevice(it)
                                }
                            }
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        peerCallback?.invoke(getPeerNames())
                        // Restart discovery for continuous mesh scanning
                        startDiscovery()
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(discoveryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(discoveryReceiver, filter)
        }
    }

    private fun startListening() {
        if (!canUseBluetooth()) return
        serverThread?.cancel()
        serverThread = AcceptThread().also { it.start() }
    }

    private fun connectToBondedDevices() {
        if (!canUseBluetooth()) return
        adapter?.bondedDevices?.forEach { device ->
            if (device.address != adapter?.address) connectToDevice(device)
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        if (!canUseBluetooth()) return
        Thread {
            var socket: BluetoothSocket? = null
            try {
                socket = device.createRfcommSocketToServiceRecord(serviceUuid)
                adapter?.cancelDiscovery()
                socket?.connect()
                val thread = ConnectedThread(socket!!)
                synchronized(connectedThreads) {
                    connectedThreads.add(thread)
                }
                thread.start()
                peerCallback?.invoke(getPeerNames())
                Log.d(TAG, "Connected to ${device.name ?: device.address}")
            } catch (e: Exception) {
                Log.d(TAG, "Unable to connect to ${device.name}: ${e.message}")
                try { socket?.close() } catch (_: IOException) {}
                socket = null
            }
        }.start()
    }

    private fun hasRequiredBluetoothPermissions(): Boolean {
        if (adapter == null) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scanGranted = context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            val connectGranted = context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            return scanGranted && connectGranted
        }
        return true
    }

    private fun canUseBluetooth(): Boolean {
        if (adapter == null || adapter?.isEnabled != true) return false
        return hasRequiredBluetoothPermissions()
    }

    private fun getPeerNames(): List<String> {
        return synchronized(connectedThreads) {
            connectedThreads.mapNotNull { it.remoteName }.distinct()
        }
    }

    private inner class AcceptThread : Thread() {
        private val serverSocket: BluetoothServerSocket? = try {
            if (canUseBluetooth()) {
                adapter?.listenUsingRfcommWithServiceRecord("upi-mesh", serviceUuid)
            } else null
        } catch (e: SecurityException) { null }

        override fun run() {
            while (!isInterrupted) {
                val socket = try {
                    serverSocket?.accept()
                } catch (e: IOException) {
                    break
                }
                socket?.let {
                    val thread = ConnectedThread(it)
                    synchronized(connectedThreads) {
                        connectedThreads.add(thread)
                    }
                    thread.start()
                    peerCallback?.invoke(getPeerNames())
                    Log.d(TAG, "Accepted connection from ${it.remoteDevice.name ?: it.remoteDevice.address}")
                }
            }
        }

        fun cancel() {
            try { serverSocket?.close() } catch (_: IOException) {}
            interrupt()
        }
    }

    private inner class ConnectedThread(var socket: BluetoothSocket?) : Thread() {
        private val inputStream = socket!!.inputStream
        private val outputStream = socket!!.outputStream
        private val dataInput = DataInputStream(inputStream)
        private val dataOutput = DataOutputStream(outputStream)
        val remoteAddress: String = socket!!.remoteDevice.address
        val remoteName: String? = socket!!.remoteDevice.name

        override fun run() {
            while (!isInterrupted) {
                try {
                    // Read framed packet: [4-byte length][payload]
                    val payloadSize = dataInput.readInt()
                    if (payloadSize <= 0 || payloadSize > MAX_PACKET_SIZE_BYTES) {
                        Log.w(TAG, "Invalid packet size $payloadSize from $remoteAddress — dropping")
                        break
                    }
                    val payloadBytes = ByteArray(payloadSize)
                    dataInput.readFully(payloadBytes)
                    val json = String(payloadBytes, StandardCharsets.UTF_8)
                    val packet = gson.fromJson(json, MeshPacket::class.java)

                    processIncomingPacket(packet)

                } catch (e: IOException) {
                    Log.d(TAG, "Connection to $remoteName closed: ${e.message}")
                    break
                } catch (e: Exception) {
                    Log.d(TAG, "Packet decode failed from $remoteName: ${e.message}")
                    break
                }
            }
            synchronized(connectedThreads) { connectedThreads.remove(this) }
            try { socket?.close() } catch (_: IOException) {}
            socket = null
            peerCallback?.invoke(getPeerNames())
        }

        /**
         * Full store-and-forward processing for a received packet:
         * 1. Deduplication — drop if already seen
         * 2. Expiry check — drop if packet is too old
         * 3. Persist to Room DB (store) BEFORE forwarding
         * 4. Notify local callback (this node may be receiver)
         * 5. Decrement TTL and relay to all OTHER connected peers (forward)
         */
        private fun processIncomingPacket(packet: MeshPacket) {
            val id = packet.packetId
            if (id.isBlank()) {
                Log.w(TAG, "Received packet with blank ID — dropping")
                return
            }

            // 1. Deduplication
            if (!seenPacketIds.add(id)) {
                Log.d(TAG, "Duplicate packet ${id.take(8)} from $remoteName — dropped")
                return
            }

            // 2. Expiry
            if (isExpired(packet)) {
                Log.d(TAG, "Expired packet ${id.take(8)} from $remoteName — dropped (age=${System.currentTimeMillis() - packet.createdTimestamp}ms)")
                return
            }

            Log.d(TAG, "Received new packet ${id.take(8)} from $remoteName TTL=${packet.ttl}")

            // 3. Store: persist received packet to Room DB before relaying
            relayPersistCallback?.invoke(packet)

            // 4. Deliver to local app layer (receiver check happens in callback)
            packetCallback?.invoke(packet)

            // 5. Forward: only relay if TTL > 1 (decrement TTL on relay)
            if (packet.ttl > 1) {
                val relayPacket = packet.copy(ttl = packet.ttl - 1)
                relayToOthers(relayPacket)
            } else {
                Log.d(TAG, "Packet ${id.take(8)} TTL exhausted — not relaying further")
            }
        }

        fun write(packet: MeshPacket) {
            try {
                val json = gson.toJson(packet)
                val payloadBytes = json.toByteArray(StandardCharsets.UTF_8)
                synchronized(dataOutput) {
                    dataOutput.writeInt(payloadBytes.size)
                    dataOutput.write(payloadBytes)
                    dataOutput.flush()
                }
            } catch (_: IOException) {}
        }

        private fun relayToOthers(packet: MeshPacket) {
            val peersToRelay = synchronized(connectedThreads) {
                connectedThreads.filter { it !== this }
            }
            peersToRelay.forEach { it.write(packet) }
            if (peersToRelay.isNotEmpty()) {
                Log.d(TAG, "Relayed packet ${packet.packetId.take(8)} (TTL=${packet.ttl}) to ${peersToRelay.size} peer(s)")
            }
        }

        fun cancel() {
            interrupt()
            try { socket?.close() } catch (_: IOException) {}
            socket = null
        }
    }
}
