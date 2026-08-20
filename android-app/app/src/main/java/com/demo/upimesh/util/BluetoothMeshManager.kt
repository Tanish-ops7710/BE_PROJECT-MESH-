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
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class BluetoothMeshManager(private val context: Context) {

    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val gson = Gson()
    private val serviceUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val seenPacketIds = mutableSetOf<String>()
    private val connectedThreads = mutableListOf<ConnectedThread>()

    private var packetCallback: ((MeshPacket) -> Unit)? = null
    private var peerCallback: ((List<String>) -> Unit)? = null
    private var serverThread: AcceptThread? = null
    private var discoveryReceiver: BroadcastReceiver? = null

    fun start(packetCallback: (MeshPacket) -> Unit, peerCallback: (List<String>) -> Unit) {
        this.packetCallback = packetCallback
        this.peerCallback = peerCallback
        if (adapter == null) return

        if (!hasRequiredBluetoothPermissions()) {
            Log.d("BluetoothMeshManager", "Bluetooth permissions not granted; skipping startup")
            return
        }

        try {
            adapter.name = "UPI-Mesh-Node"
            registerDiscoveryReceiver()
            startListening()
            startDiscovery()
            connectToBondedDevices()
        } catch (e: SecurityException) {
            Log.d("BluetoothMeshManager", "Bluetooth startup blocked: ${e.message}")
        } catch (e: Exception) {
            Log.d("BluetoothMeshManager", "Bluetooth startup failed: ${e.message}")
        }
    }

    fun stop() {
        serverThread?.cancel()
        serverThread = null
        connectedThreads.forEach { it.cancel() }
        connectedThreads.clear()
        try {
            if (discoveryReceiver != null) {
                context.unregisterReceiver(discoveryReceiver)
            }
        } catch (_: IllegalArgumentException) {
        }
        discoveryReceiver = null
    }

    fun sendPacket(packet: MeshPacket) {
        if (packet.packetId.isBlank()) return
        if (!seenPacketIds.contains(packet.packetId)) {
            seenPacketIds.add(packet.packetId)
        }
        synchronized(connectedThreads) {
            connectedThreads.toList().forEach { it.write(packet) }
        }
    }

    fun startDiscovery() {
        if (!canUseBluetooth()) return
        try {
            if (adapter?.isDiscovering == true) adapter?.cancelDiscovery()
            adapter?.startDiscovery()
        } catch (e: SecurityException) {
            Log.d("BluetoothMeshManager", "Discovery blocked: ${e.message}")
        }
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
                                val existing = connectedThreads.any { thread -> thread.remoteAddress == it.address }
                                if (!existing) {
                                    connectToDevice(it)
                                }
                            }
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        peerCallback?.invoke(getPeerNames())
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
            } catch (e: Exception) {
                Log.d("BluetoothMeshManager", "Unable to connect to ${device.name}: ${e.message}")
                try {
                    socket?.close()
                } catch (ioe: IOException) {
                    ioe.printStackTrace()
                }
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
            } else {
                null
            }
        } catch (e: SecurityException) {
            null
        }

        override fun run() {
            var socket: BluetoothSocket?
            while (true) {
                socket = try {
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
                }
            }
        }

        fun cancel() {
            try {
                serverSocket?.close()
            } catch (_: IOException) {
            }
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
                    val payloadSize = dataInput.readInt()
                    val payloadBytes = ByteArray(payloadSize)
                    dataInput.readFully(payloadBytes)
                    val json = String(payloadBytes, StandardCharsets.UTF_8)
                    val packet = gson.fromJson(json, MeshPacket::class.java)
                    if (packet.packetId.isNotBlank() && seenPacketIds.add(packet.packetId)) {
                        packetCallback?.invoke(packet)
                        relayPacket(packet)
                    }
                } catch (e: IOException) {
                    break
                } catch (e: Exception) {
                    Log.d("BluetoothMeshManager", "Packet decode failed: ${e.message}")
                    break
                }
            }
            synchronized(connectedThreads) {
                connectedThreads.remove(this)
            }
            try {
                socket?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            socket = null
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
            } catch (_: IOException) {
            }
        }

        private fun relayPacket(packet: MeshPacket) {
            synchronized(connectedThreads) {
                connectedThreads.filter { it !== this }.forEach { it.write(packet) }
            }
        }

        fun cancel() {
            interrupt()
            try {
                socket?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            socket = null
        }
    }
}
