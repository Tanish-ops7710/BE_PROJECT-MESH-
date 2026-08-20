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
import androidx.core.content.ContextCompat
import com.demo.upimesh.data.api.NetworkModule
import com.demo.upimesh.data.local.AppDatabase
import com.demo.upimesh.data.local.LocalTransactionEntity
import com.demo.upimesh.data.local.NotificationEntity
import com.demo.upimesh.data.local.SmsEntity
import com.demo.upimesh.model.MeshPacket
import com.demo.upimesh.model.TransactionStatus
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.UUID

class BluetoothTransferService(private val context: Context) {
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val gson = Gson()
    private val serviceUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val scope = CoroutineScope(Dispatchers.IO)
    private val database = AppDatabase.getDatabase(context)
    private val discovered = mutableListOf<BluetoothDevice>()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val _status = MutableStateFlow("Ready")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _selectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val selectedDevice: StateFlow<BluetoothDevice?> = _selectedDevice.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var serverThread: AcceptThread? = null
    private var connectedSocket: BluetoothSocket? = null
    private var receiverThread: ConnectedThread? = null
    private var discoveryReceiver: BroadcastReceiver? = null
    private val pendingAckTransactions = mutableMapOf<String, String>()
    private var discoverableModeStarted = false

    fun updateStatus(message: String) {
        _status.value = message
    }

    fun hasRequiredPermissions(context: Context): Boolean {
        return getRequiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun ensurePermissions(): Boolean {
        val missing = getRequiredPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            _status.value = "Bluetooth permissions are required."
            return false
        }
        return true
    }

    fun startDiscovery() {
        if (!ensurePermissions()) return
        if (adapter == null || adapter.isEnabled.not()) {
            _status.value = "Bluetooth is off"
            return
        }
        discovered.clear()
        _discoveredDevices.value = emptyList()
        logEvent("Device discovery started")
        registerReceiver()
        populateBondedDevices()
        requestDiscoverableMode()
        adapter.startDiscovery()
        serverThread?.cancel()
        serverThread = AcceptThread().also { it.start() }
    }

    fun connectToDevice(device: BluetoothDevice) {
        if (!ensurePermissions()) return
        if (adapter == null || adapter.isEnabled.not()) {
            _status.value = "Bluetooth is off"
            return
        }
        _selectedDevice.value = device
        logEvent("Connecting...")
        adapter.cancelDiscovery()
        Thread {
            var socket: BluetoothSocket? = null
            try {
                if (device.bondState != BluetoothDevice.BOND_BONDED) {
                    device.createBond()
                    Thread.sleep(1500)
                }

                socket = device.createRfcommSocketToServiceRecord(serviceUuid)
                adapter?.cancelDiscovery()
                socket?.connect()
                connectedSocket = socket
                receiverThread = ConnectedThread(socket!!, isServer = false)
                receiverThread?.start()
                _isConnected.value = true
                logEvent("Connected")
            } catch (e: Exception) {
                _isConnected.value = false
                logEvent("Connection Failed")
                try {
                    socket?.close()
                } catch (ioe: IOException) {
                    ioe.printStackTrace()
                }
                socket = null
            }
        }.start()
    }

    fun sendPendingTransaction() {
        scope.launch {
            val transaction = getPendingTransaction() ?: return@launch
            val packet = buildPacket(transaction)
            pendingAckTransactions[packet.packetId] = transaction.transactionId
            database.appDao().updateTransactionStatusById(transaction.transactionId, TransactionStatus.PENDING_BLUETOOTH)
            database.appDao().insertSms(SmsEntity(body = "Bluetooth packet queued for ${transaction.receiverVpa}", type = "DEBIT"))

            val socket = ensureConnectedSocket()
            if (socket == null) {
                database.appDao().updateTransactionStatusById(transaction.transactionId, TransactionStatus.PENDING_BLUETOOTH)
                logEvent("Connection Failed")
                return@launch
            }

            if (receiverThread == null) {
                receiverThread = ConnectedThread(socket, isServer = false)
                receiverThread?.start()
            }

            logEvent("Packet serialized")
            receiverThread?.write(packet)
            logEvent("Packet sent")
            _status.value = "Packet sent"
        }
    }

    fun sendDemoTransaction(senderVpa: String, receiverVpa: String, amount: String, note: String) {
        scope.launch {
            val pendingTransaction = getPendingTransaction() ?: createPendingTransaction(senderVpa, receiverVpa, amount, note)
            val transaction = pendingTransaction ?: return@launch
            val packet = buildPacket(transaction)
            pendingAckTransactions[packet.packetId] = transaction.transactionId
            database.appDao().updateTransactionStatusById(transaction.transactionId, TransactionStatus.PENDING_BLUETOOTH)
            database.appDao().insertSms(SmsEntity(body = "Bluetooth packet queued for ${transaction.receiverVpa}", type = "DEBIT"))

            val socket = ensureConnectedSocket()
            if (socket == null) {
                database.appDao().updateTransactionStatusById(transaction.transactionId, TransactionStatus.PENDING_BLUETOOTH)
                logEvent("Connection Failed")
                return@launch
            }

            if (receiverThread == null) {
                receiverThread = ConnectedThread(socket, isServer = false)
                receiverThread?.start()
            }

            logEvent("Packet serialized")
            receiverThread?.write(packet)
            logEvent("Packet sent")
            _status.value = "Packet sent"
        }
    }

    private fun registerReceiver() {
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
                            if (it.address != null && discovered.none { existing -> existing.address == it.address }) {
                                discovered.add(it)
                                _discoveredDevices.value = discovered.sortedWith(compareByDescending { device ->
                                    when {
                                        device.name?.contains("Pixel", ignoreCase = true) == true -> 3
                                        device.name?.contains("Galaxy", ignoreCase = true) == true -> 2
                                        device.name?.contains("Android", ignoreCase = true) == true -> 1
                                        else -> 0
                                    }
                                }).toList()
                                logEvent("Device discovered: ${it.name ?: it.address}")
                            }
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        if (discoverableModeStarted) {
                            adapter?.startDiscovery()
                        }
                    }
                }
            }
        }
        context.registerReceiver(discoveryReceiver, filter)
    }

    private inner class AcceptThread : Thread() {
        private val serverSocket: BluetoothServerSocket? = try {
            adapter?.listenUsingRfcommWithServiceRecord("upi-mesh-transfer", serviceUuid)
        } catch (_: Exception) {
            null
        }

        override fun run() {
            while (!isInterrupted) {
                val socket = try {
                    serverSocket?.accept()
                } catch (_: Exception) {
                    break
                }
                if (socket != null) {
                    connectedSocket = socket
                    receiverThread = ConnectedThread(socket, isServer = true)
                    receiverThread?.start()
                    _isConnected.value = true
                    logEvent("Connected")
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

    private fun ensureConnectedSocket(): BluetoothSocket? {
        if (connectedSocket?.isConnected == true) {
            return connectedSocket
        }

        val selectedDevice = _selectedDevice.value
        if (selectedDevice == null) {
            logEvent("No device selected for Bluetooth transfer")
            return null
        }

        var socket: BluetoothSocket? = null
        return try {
            adapter?.cancelDiscovery()
            if (selectedDevice.bondState != BluetoothDevice.BOND_BONDED) {
                selectedDevice.createBond()
                Thread.sleep(1500)
            }

            socket = selectedDevice.createRfcommSocketToServiceRecord(serviceUuid)
            adapter?.cancelDiscovery()
            socket?.connect()
            connectedSocket = socket
            receiverThread = ConnectedThread(socket!!, isServer = false)
            receiverThread?.start()
            _isConnected.value = true
            logEvent("Connected")
            socket
        } catch (e: Exception) {
            _isConnected.value = false
            logEvent("Connection Failed")
            try {
                socket?.close()
            } catch (ioe: IOException) {
                ioe.printStackTrace()
            }
            socket = null
            null
        }
    }

    private fun populateBondedDevices() {
        try {
            adapter?.bondedDevices?.forEach { device ->
                if (device.address != null && discovered.none { existing -> existing.address == device.address }) {
                    discovered.add(device)
                }
            }
            _discoveredDevices.value = discovered.toList()
        } catch (_: Exception) {
        }
    }

    private fun requestDiscoverableMode() {
        if (discoverableModeStarted) return
        try {
            discoverableModeStarted = true
            val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
            }
            discoverableIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(discoverableIntent)
        } catch (_: Exception) {
            discoverableModeStarted = false
        }
    }

    fun retryDiscoveryAfterPermissionGrant() {
        if (ensurePermissions()) {
            startDiscovery()
        }
    }

    private suspend fun getPendingTransaction(): LocalTransactionEntity? {
        return database.appDao().getTransactionsByStatus(TransactionStatus.PENDING_BLUETOOTH).first().firstOrNull()
            ?: database.appDao().getTransactionsByStatus(TransactionStatus.PENDING).first().firstOrNull()
    }

    private suspend fun createPendingTransaction(senderVpa: String, receiverVpa: String, amount: String, note: String): LocalTransactionEntity? {
        val tx = LocalTransactionEntity(
            transactionId = UUID.randomUUID().toString(),
            packetId = UUID.randomUUID().toString(),
            senderId = senderVpa,
            receiverId = receiverVpa,
            senderVpa = senderVpa,
            receiverVpa = receiverVpa,
            amount = amount,
            timestamp = System.currentTimeMillis(),
            status = TransactionStatus.PENDING_BLUETOOTH,
            retryCount = 0,
            digitalSignature = "bt-${UUID.randomUUID()}",
            createdAt = System.currentTimeMillis(),
            note = note
        )
        database.appDao().insertTransaction(tx)
        return tx
    }

    private fun buildPacket(transaction: LocalTransactionEntity): MeshPacket {
        val payload = gson.toJson(transaction)
        return MeshPacket(
            packetId = transaction.packetId,
            senderVpa = transaction.senderVpa,
            ciphertext = payload,
            encryptedKey = "bt-key",
            iv = "bt-iv",
            ttl = 1,
            signature = transaction.digitalSignature,
            createdTimestamp = System.currentTimeMillis()
        )
    }

    private fun logEvent(message: String) {
        Log.d("BluetoothTransfer", message)
        _status.value = message
    }

    private inner class ConnectedThread(
        private val socket: BluetoothSocket,
        private val isServer: Boolean
    ) : Thread() {
        private val inputStream = socket.inputStream
        private val outputStream = socket.outputStream
        private val dataInput = DataInputStream(inputStream)
        private val dataOutput = DataOutputStream(outputStream)

        override fun run() {
            while (!isInterrupted) {
                try {
                    val size = dataInput.readInt()
                    val payload = ByteArray(size)
                    dataInput.readFully(payload)
                    val packetJson = String(payload, StandardCharsets.UTF_8)
                    logEvent("Packet received")
                    val packet = gson.fromJson(packetJson, MeshPacket::class.java)
                    logEvent("Packet deserialized")
                    if (packet.ciphertext.startsWith("ACK:")) {
                        handleAckPacket(packet)
                    } else {
                        handleIncomingPacket(packet)
                    }
                } catch (_: Exception) {
                    break
                }
            }
        }

        fun write(packet: MeshPacket) {
            try {
                val json = gson.toJson(packet)
                val bytes = json.toByteArray(StandardCharsets.UTF_8)
                synchronized(dataOutput) {
                    dataOutput.writeInt(bytes.size)
                    dataOutput.write(bytes)
                    dataOutput.flush()
                }
            } catch (_: Exception) {
            }
        }

        private fun handleIncomingPacket(packet: MeshPacket) {
            scope.launch {
                val existing = database.appDao().getTransactionByPacketId(packet.packetId)
                if (existing != null) {
                    logEvent("Packet already stored: ${packet.packetId}")
                    sendAck(packet)
                    return@launch
                }

                val receivedTx = try {
                    gson.fromJson(packet.ciphertext, LocalTransactionEntity::class.java)
                } catch (_: Exception) {
                    null
                }

                val tx = receivedTx ?: LocalTransactionEntity(
                    transactionId = UUID.randomUUID().toString(),
                    packetId = packet.packetId,
                    senderId = packet.senderVpa ?: "",
                    receiverId = "local-device",
                    senderVpa = packet.senderVpa ?: "",
                    receiverVpa = "local-device",
                    amount = "0",
                    timestamp = System.currentTimeMillis(),
                    status = TransactionStatus.PENDING_UPLOAD,
                    retryCount = 0,
                    digitalSignature = packet.signature ?: "",
                    createdAt = System.currentTimeMillis(),
                    note = "Received via Bluetooth"
                )

                val storedTx = tx.copy(
                    transactionId = if (tx.transactionId.isBlank()) UUID.randomUUID().toString() else tx.transactionId,
                    packetId = packet.packetId,
                    status = TransactionStatus.PENDING_UPLOAD,
                    note = "Received via Bluetooth"
                )

                database.appDao().insertTransaction(storedTx)
                database.appDao().insertSms(SmsEntity(body = "Received Bluetooth payment ${packet.packetId.take(8)}", type = "DEBIT"))
                database.appDao().insertNotification(NotificationEntity(title = "Offline payment received", message = "A new offline payment is waiting to be uploaded", type = "RECEIVED"))
                logEvent("Transaction stored")
                logEvent("ACK sent")
                sendAck(packet)
                uploadTransactionIfPossible(storedTx)
            }
        }

        private fun handleAckPacket(packet: MeshPacket) {
            val ackedPacketId = packet.ciphertext.removePrefix("ACK:")
            scope.launch {
                val transactionId = pendingAckTransactions[ackedPacketId]
                if (transactionId != null) {
                    database.appDao().updateTransactionStatusById(transactionId, TransactionStatus.DELIVERED)
                    pendingAckTransactions.remove(ackedPacketId)
                    logEvent("ACK received")
                    _status.value = "Delivered"
                    uploadTransactionForSender(transactionId)
                }
            }
        }

        private fun sendAck(packet: MeshPacket) {
            val ack = MeshPacket(
                packetId = UUID.randomUUID().toString(),
                senderVpa = "local-device",
                ciphertext = "ACK:${packet.packetId}",
                encryptedKey = "ACK",
                iv = "ACK",
                ttl = 0,
                signature = "ack",
                createdTimestamp = System.currentTimeMillis()
            )
            logEvent("ACK sent")
            write(ack)
        }

        private fun uploadTransactionIfPossible(transaction: LocalTransactionEntity) {
            scope.launch {
                logEvent("Upload started")
                database.appDao().updateTransactionStatusById(transaction.transactionId, TransactionStatus.PENDING_UPLOAD)
                try {
                    val apiService = NetworkModule.createApiService()
                    val meshPacket = MeshPacket(
                        packetId = transaction.packetId,
                        senderVpa = transaction.senderVpa,
                        ciphertext = gson.toJson(transaction),
                        encryptedKey = "bt-key",
                        iv = "bt-iv",
                        ttl = 1,
                        signature = transaction.digitalSignature,
                        createdTimestamp = transaction.createdAt
                    )
                    val response = apiService.ingestPacket(meshPacket)
                    if (response.isSuccessful) {
                        database.appDao().updateTransactionStatusById(transaction.transactionId, TransactionStatus.COMPLETED)
                        logEvent("Upload completed")
                    } else {
                        database.appDao().updateTransactionStatusById(transaction.transactionId, TransactionStatus.PENDING_UPLOAD)
                    }
                } catch (_: Exception) {
                    database.appDao().updateTransactionStatusById(transaction.transactionId, TransactionStatus.PENDING_UPLOAD)
                }
            }
        }

        private fun uploadTransactionForSender(transactionId: String) {
            scope.launch {
                val transaction = database.appDao().getTransactionById(transactionId)
                if (transaction == null) return@launch
                logEvent("Upload started")
                database.appDao().updateTransactionStatusById(transactionId, TransactionStatus.PENDING_UPLOAD)
                try {
                    val apiService = NetworkModule.createApiService()
                    val meshPacket = MeshPacket(
                        packetId = transaction.packetId,
                        senderVpa = transaction.senderVpa,
                        ciphertext = gson.toJson(transaction),
                        encryptedKey = "bt-key",
                        iv = "bt-iv",
                        ttl = 1,
                        signature = transaction.digitalSignature,
                        createdTimestamp = transaction.createdAt
                    )
                    val response = apiService.ingestPacket(meshPacket)
                    if (response.isSuccessful) {
                        database.appDao().updateTransactionStatusById(transactionId, TransactionStatus.COMPLETED)
                        logEvent("Upload completed")
                    } else {
                        database.appDao().updateTransactionStatusById(transactionId, TransactionStatus.PENDING_UPLOAD)
                    }
                } catch (_: Exception) {
                    database.appDao().updateTransactionStatusById(transactionId, TransactionStatus.PENDING_UPLOAD)
                }
            }
        }
    }
}
