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
    companion object {
        private const val TAG = "BluetoothTransfer"
        val TRANSFER_SERVICE_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val serviceUuid = TRANSFER_SERVICE_UUID
    private val scope = CoroutineScope(Dispatchers.IO)
    private val database = AppDatabase.getDatabase(context)
    private val discovered = mutableListOf<BluetoothDevice>()
    private val seenPacketIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val _status = MutableStateFlow("Ready")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _selectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val selectedDevice: StateFlow<BluetoothDevice?> = _selectedDevice.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _incomingRequestDevice = MutableStateFlow<BluetoothDevice?>(null)
    val incomingRequestDevice: StateFlow<BluetoothDevice?> = _incomingRequestDevice.asStateFlow()

    private val _needsRefresh = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val needsRefresh: kotlinx.coroutines.flow.SharedFlow<Unit> = _needsRefresh

    // Emitted when Phone B finishes uploading a received packet — triggers a server sync
    // so the real settled transaction (with correct amount/VPA) appears in history.
    private val _needsSync = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val needsSync: kotlinx.coroutines.flow.SharedFlow<Unit> = _needsSync

    private var serverThread: AcceptThread? = null
    private var connectedSocket: BluetoothSocket? = null
    private var receiverThread: ConnectedThread? = null
    private var discoveryReceiver: BroadcastReceiver? = null
    private val pendingAckTransactions = mutableMapOf<String, String>()
    private var discoverableModeStarted = false

    fun acceptIncomingConnection() {
        val device = _incomingRequestDevice.value ?: return
        val deviceName = try {
            device.name?.takeIf { it.isNotBlank() } ?: device.address
        } catch (_: SecurityException) {
            device.address
        }
        _incomingRequestDevice.value = null
        _selectedDevice.value = device
        _isConnected.value = true
        logEvent("Accepted connection from $deviceName")
        
        // Send ACK back to Phone A
        val ack = MeshPacket(
            packetId = UUID.randomUUID().toString(),
            senderVpa = "HANDSHAKE",
            ciphertext = "ACK_CONNECTION_ACCEPTED",
            encryptedKey = "NONE",
            iv = "NONE",
            ttl = 0,
            signature = "HANDSHAKE"
        )
        receiverThread?.write(ack)
        Log.d(TAG, "ACK_SENT: Sent ACK_CONNECTION_ACCEPTED to $deviceName")
    }

    fun rejectIncomingConnection() {
        val device = _incomingRequestDevice.value
        val deviceName = try {
            device?.name?.takeIf { it.isNotBlank() } ?: (device?.address ?: "Unknown")
        } catch (_: SecurityException) {
            device?.address ?: "Unknown"
        }
        _incomingRequestDevice.value = null
        logEvent("Declined connection request from $deviceName")
        
        val ack = MeshPacket(
            packetId = UUID.randomUUID().toString(),
            senderVpa = "HANDSHAKE",
            ciphertext = "ACK_CONNECTION_REJECTED",
            encryptedKey = "NONE",
            iv = "NONE",
            ttl = 0,
            signature = "HANDSHAKE"
        )
        receiverThread?.write(ack)
        Log.d(TAG, "ACK_SENT: Sent ACK_CONNECTION_REJECTED to $deviceName")
        
        try {
            connectedSocket?.close()
        } catch (_: IOException) {}
        connectedSocket = null
        receiverThread = null
        _isConnected.value = false
        _selectedDevice.value = null
    }

    /**
     * Call when the BT screen opens to reset any stale connection state from a previous session.
     * The singleton persists across navigations, so _isConnected may still be true.
     */
    fun resetForNewSession() {
        _isConnected.value = false
        _selectedDevice.value = null
        _incomingRequestDevice.value = null
        _status.value = "Ready"
        try { connectedSocket?.close() } catch (_: IOException) {}
        connectedSocket = null
        receiverThread?.interrupt()
        receiverThread = null
        pendingAckTransactions.clear()
        seenPacketIds.clear()
        Log.d(TAG, "RESET: Connection state cleared for new session")
    }

    init {
        startServerListening()
    }

    fun startServerListening() {
        if (!hasRequiredPermissions(context)) return
        if (adapter == null || !adapter.isEnabled) return
        if (serverThread == null || !serverThread!!.isAlive) {
            serverThread?.cancel()
            serverThread = AcceptThread().also { it.start() }
        }
    }

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
        startServerListening()
    }

    fun connectToDevice(device: BluetoothDevice) {
        if (!ensurePermissions()) return
        if (adapter == null || adapter.isEnabled.not()) {
            _status.value = "Bluetooth is off"
            return
        }
        _selectedDevice.value = device
        val deviceLabel = try { device.name?.takeIf { it.isNotBlank() } ?: device.address } catch (_: SecurityException) { device.address }
        logEvent("Connecting to $deviceLabel...")
        adapter.cancelDiscovery()
        Thread {
            var socket: BluetoothSocket? = null
            try {
                if (device.bondState != BluetoothDevice.BOND_BONDED) {
                    device.createBond()
                    Thread.sleep(1000)
                }

                socket = device.createRfcommSocketToServiceRecord(serviceUuid)
                adapter?.cancelDiscovery()
                
                // Close any existing socket before opening a new one
                try {
                    connectedSocket?.close()
                } catch (_: IOException) {}
                receiverThread?.interrupt()
                
                socket?.connect()
                connectedSocket = socket
                
                // Start connected thread to listen for handshake ACK
                val connThread = ConnectedThread(socket!!, isServer = false)
                receiverThread = connThread
                connThread.start()
                
                logEvent("RFCOMM socket connected. Sending handshake HELLO to $deviceLabel...")
                val helloPacket = MeshPacket(
                    packetId = UUID.randomUUID().toString(),
                    senderVpa = "HANDSHAKE",
                    ciphertext = "HELLO_PHASE1",
                    encryptedKey = "NONE",
                    iv = "NONE",
                    ttl = 1,
                    signature = "HANDSHAKE"
                )
                connThread.write(helloPacket)
            } catch (e: Exception) {
                Log.e(TAG, "RFCOMM connect failed: ${e.message}", e)
                _isConnected.value = false
                _selectedDevice.value = null
                logEvent("Failed to connect to $deviceLabel")
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
            logEvent("Packet Created")
            _status.value = "Packet Created"
            val packet = buildPacket(transaction)
            pendingAckTransactions[packet.packetId] = transaction.transactionId
            database.appDao().updateTransactionStatusById(transaction.transactionId, TransactionStatus.PENDING_BLUETOOTH)
            database.appDao().insertSms(SmsEntity(body = "Bluetooth packet queued for ${transaction.receiverVpa}", type = "DEBIT"))

            // Use the already-connected socket/thread — do NOT call ensureConnectedSocket()
            // which would overwrite the live ConnectedThread and break the connection.
            val activeThread = receiverThread
            if (activeThread == null || !connectedSocket?.isConnected.let { it == true } || !_isConnected.value) {
                database.appDao().updateTransactionStatusById(transaction.transactionId, TransactionStatus.PENDING_BLUETOOTH)
                logEvent("No active BT connection — packet queued")
                _status.value = "Not Connected"
                return@launch
            }

            activeThread.write(packet)
            logEvent("Packet Sent")
            _status.value = "Packet Sent"
            
            // Two-Stage Timeout logic for EXPIRED
            scope.launch {
                // Stage 1: Wait 10s for Packet ACK
                kotlinx.coroutines.delay(10_000)
                var currentTx = database.appDao().getTransactionById(transaction.transactionId)
                if (currentTx != null && currentTx.status == TransactionStatus.PENDING_BLUETOOTH) {
                    database.appDao().updateTransactionStatusById(transaction.transactionId, TransactionStatus.EXPIRED)
                    pendingAckTransactions.remove(packet.packetId)
                    logEvent("Payment Expired: Packet ACK Timeout")
                    _status.value = "Payment Expired"
                    refundLocalBalance(context, transaction)
                    return@launch
                }

                // Stage 2: Wait 30s for Settlement ACK
                kotlinx.coroutines.delay(30_000)
                currentTx = database.appDao().getTransactionById(transaction.transactionId)
                if (currentTx != null && currentTx.status == TransactionStatus.DELIVERED) {
                    database.appDao().updateTransactionStatusById(transaction.transactionId, TransactionStatus.EXPIRED)
                    pendingAckTransactions.remove(packet.packetId)
                    logEvent("Payment Expired: Settlement Timeout")
                    _status.value = "Payment Expired"
                    refundLocalBalance(context, transaction)
                }
            }
        }
    }

    fun sendDemoTransaction(senderVpa: String, receiverVpa: String, amount: String, note: String, pin: String = "") {
        scope.launch {
            val pendingTransaction = getPendingTransaction() ?: createPendingTransaction(senderVpa, receiverVpa, amount, note)
            val transaction = pendingTransaction ?: return@launch
            logEvent("Packet Created")
            _status.value = "Packet Created"

            val amountDecimal = amount.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
            if (amountDecimal > java.math.BigDecimal.ZERO) {
                val prefs = context.getSharedPreferences("upi_mesh_prefs", android.content.Context.MODE_PRIVATE)
                val currentBalStr = prefs.getString("user_balance_$senderVpa", "0") ?: "0"
                val currentBal = java.math.BigDecimal(currentBalStr)
                val newBal = currentBal.subtract(amountDecimal)
                prefs.edit().putString("user_balance_$senderVpa", newBal.toPlainString()).apply()
                _needsRefresh.tryEmit(Unit)
            }

            val packet = buildPacket(transaction, pin)
            pendingAckTransactions[packet.packetId] = transaction.transactionId
            database.appDao().updateTransactionStatusById(transaction.transactionId, TransactionStatus.PENDING_BLUETOOTH)
            database.appDao().insertSms(SmsEntity(body = "Bluetooth packet queued for ${transaction.receiverVpa}", type = "DEBIT"))

            // Use the already-connected socket/thread — do NOT call ensureConnectedSocket()
            // which would overwrite the live ConnectedThread and break the connection.
            val activeThread = receiverThread
            if (activeThread == null || !connectedSocket?.isConnected.let { it == true } || !_isConnected.value) {
                database.appDao().updateTransactionStatusById(transaction.transactionId, TransactionStatus.PENDING_BLUETOOTH)
                logEvent("No active BT connection — packet queued")
                _status.value = "Not Connected"
                return@launch
            }

            activeThread.write(packet)
            logEvent("Packet Sent")
            _status.value = "Packet Sent"

            // Two-Stage Timeout logic for EXPIRED
            scope.launch {
                // Stage 1: Wait 15s for Packet ACK
                kotlinx.coroutines.delay(15_000)
                var currentTx = database.appDao().getTransactionById(transaction.transactionId)
                if (currentTx != null && currentTx.status == TransactionStatus.PENDING_BLUETOOTH) {
                    database.appDao().updateTransactionStatusById(transaction.transactionId, TransactionStatus.EXPIRED)
                    pendingAckTransactions.remove(packet.packetId)
                    logEvent("Payment Expired: Packet ACK Timeout")
                    _status.value = "Payment Expired"
                    refundLocalBalance(context, transaction)
                    return@launch
                }

                // Stage 2: Wait 60s for Settlement ACK (upload + backend may take time)
                kotlinx.coroutines.delay(60_000)
                currentTx = database.appDao().getTransactionById(transaction.transactionId)
                if (currentTx != null && currentTx.status == TransactionStatus.DELIVERED) {
                    database.appDao().updateTransactionStatusById(transaction.transactionId, TransactionStatus.EXPIRED)
                    pendingAckTransactions.remove(packet.packetId)
                    logEvent("Payment Expired: Settlement Timeout")
                    _status.value = "Payment Expired"
                    refundLocalBalance(context, transaction)
                }
            }
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
        // Phase 1 fix: API 33+ requires an export flag, otherwise throws SecurityException
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(discoveryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(discoveryReceiver, filter)
        }
    }

    private inner class AcceptThread : Thread() {
        private var serverSocket: BluetoothServerSocket? = null

        override fun run() {
            try {
                serverSocket = adapter?.listenUsingRfcommWithServiceRecord("upi-mesh-transfer", serviceUuid)
                Log.d(TAG, "SERVER_STARTED: RFCOMM server socket created on UUID $serviceUuid")
                logEvent("RFCOMM Server ready and listening")
            } catch (e: Exception) {
                Log.e(TAG, "SERVER_ERROR: Failed to open RFCOMM server socket: ${e.message}", e)
                return
            }

            while (!isInterrupted) {
                Log.d(TAG, "WAITING_FOR_CONNECTION: Server socket listening for incoming connection...")
                val socket = try {
                    serverSocket?.accept()
                } catch (e: Exception) {
                    if (!isInterrupted) {
                        Log.d(TAG, "SERVER_ACCEPT_CLOSED: ${e.message}")
                    }
                    break
                }
                if (socket != null) {
                    if (connectedSocket != null && connectedSocket?.isConnected == true) {
                        Log.d(TAG, "Already connected to another device, rejecting incoming socket")
                        try { socket.close() } catch (_: IOException) {}
                        continue
                    }

                    connectedSocket = socket
                    val remoteDevice = socket.remoteDevice
                    val deviceName = try {
                        remoteDevice.name?.takeIf { it.isNotBlank() } ?: remoteDevice.address
                    } catch (_: SecurityException) {
                        remoteDevice.address
                    }
                    
                    // Do NOT set _isConnected to true here. 
                    // It will be set to true when the user clicks 'Accept' in the UI.
                    
                    Log.d(TAG, "CONNECTION_ACCEPTED: Incoming RFCOMM connection accepted from $deviceName (${remoteDevice.address})")
                    logEvent("Incoming RFCOMM connection accepted from $deviceName")

                    receiverThread = ConnectedThread(socket, isServer = true)
                    receiverThread?.start()
                }
            }
        }

        fun cancel() {
            try {
                serverSocket?.close()
            } catch (_: IOException) {
            }
            interrupt()
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

    private fun buildPacket(transaction: LocalTransactionEntity, pin: String = ""): MeshPacket {
        // PRIMARY: Use encryptPaymentOffline — this is RSA-OAEP encrypted to the SERVER's public key.
        // The backend at /api/bridge/ingest can decrypt this and settle the payment.
        // wrapPayload() uses the SENDER's device key which the backend CANNOT decrypt → causes EXPIRED.
        try {
            val amountDecimal = transaction.amount.toBigDecimalOrNull()
            if (amountDecimal != null) {
                val ciphertext = CryptoUtils.encryptPaymentOffline(
                    context = context,
                    senderVpa = transaction.senderVpa,
                    receiverVpa = transaction.receiverVpa,
                    amount = amountDecimal,
                    pin = pin.ifBlank { "0000" },  // use provided pin; 0000 fallback lets backend use pinHash validation
                    packetId = transaction.packetId,
                    timestamp = transaction.createdAt
                )
                if (ciphertext != null) {
                    return MeshPacket(
                        packetId = transaction.packetId,
                        senderVpa = transaction.senderVpa,
                        ciphertext = ciphertext,
                        encryptedKey = "HYBRID_RSA_OAEP_AES_GCM",
                        iv = "EMBEDDED_IN_CIPHERTEXT",
                        ttl = 5,
                        signature = transaction.digitalSignature,
                        createdTimestamp = transaction.createdAt
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "encryptPaymentOffline failed, falling back to wrapPayload: ${e.message}")
        }

        // FALLBACK: wrapPayload (used only if server key not yet cached — e.g. first run with no internet)
        return try {
            CryptoUtils.wrapPayload(
                context = context,
                senderVpa = transaction.senderVpa,
                receiverVpa = transaction.receiverVpa,
                amount = transaction.amount,
                note = transaction.note ?: "",
                packetId = transaction.packetId,
                timestamp = transaction.createdAt
            )
        } catch (e: Exception) {
            MeshPacket(
                packetId = transaction.packetId,
                senderVpa = transaction.senderVpa,
                ciphertext = gson.toJson(transaction),
                encryptedKey = "bt-key",
                iv = "bt-iv",
                ttl = 5,
                signature = transaction.digitalSignature,
                createdTimestamp = transaction.createdAt
            )
        }
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
                    if (packet.ciphertext.startsWith("ACK:") || packet.ciphertext.startsWith("ACK_")) {
                        handleAckPacket(packet)
                    } else {
                        handleIncomingPacket(packet)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "ConnectedThread read error: ${e.message}", e)
                    if (!isInterrupted) {
                        logEvent("BT read error: ${e.message}")
                    }
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
                // Phase 1 Handshake / Request handling
                if (packet.ciphertext == "HELLO_PHASE1") {
                    val remoteDevice = socket.remoteDevice
                    val deviceName = try {
                        remoteDevice.name?.takeIf { it.isNotBlank() } ?: remoteDevice.address
                    } catch (_: SecurityException) {
                        remoteDevice.address
                    }
                    Log.d(TAG, "HELLO_RECEIVED: Incoming connection request from $deviceName ($remoteDevice.address)")
                    logEvent("Incoming connection request from $deviceName")
                    
                    // Post system notification with PendingIntent to open app
                    NotificationUtils.sendNotification(
                        context,
                        "UPI Connection Request",
                        "Incoming connection request from $deviceName. Tap to respond."
                    )
                    
                    // Set state so the interactive Accept/Decline dialog appears in Compose UI
                    _incomingRequestDevice.value = remoteDevice
                    return@launch
                }

                // Deduplication
                if (!seenPacketIds.add(packet.packetId)) {
                    logEvent("Duplicate packet ${packet.packetId.take(8)} — dropped")
                    sendAck(packet)
                    return@launch
                }
                val existing = database.appDao().getTransactionByPacketId(packet.packetId)
                if (existing != null) {
                    logEvent("Packet already stored: ${packet.packetId}")
                    sendAck(packet)
                    return@launch
                }

                // Step 1: Store the raw MeshPacket so it can be uploaded as-is to the server
                val offlinePacket = com.demo.upimesh.data.local.OfflinePacketEntity(
                    packetId = packet.packetId,
                    senderVpa = packet.senderVpa ?: "",
                    receiverVpa = "", // server decrypts and resolves receiver
                    amount = "0",     // server decrypts and resolves amount
                    ciphertext = packet.ciphertext,
                    encryptedKey = packet.encryptedKey,
                    iv = packet.iv,
                    ttl = packet.ttl,
                    timestamp = packet.createdTimestamp
                )
                database.appDao().insertPacket(offlinePacket)

                // Step 2: Create a local transaction record for tracking status
                val storedTx = com.demo.upimesh.data.local.LocalTransactionEntity(
                    transactionId = UUID.randomUUID().toString(),
                    packetId = packet.packetId,
                    senderId = packet.senderVpa ?: "",
                    receiverId = "local-device",
                    senderVpa = packet.senderVpa ?: "",
                    receiverVpa = "pending-server-resolution",
                    amount = "pending",
                    timestamp = System.currentTimeMillis(),
                    status = TransactionStatus.PENDING_UPLOAD,
                    retryCount = 0,
                    digitalSignature = packet.signature ?: "",
                    createdAt = System.currentTimeMillis(),
                    note = "Received via Bluetooth — pending upload"
                )
                database.appDao().insertTransaction(storedTx)
                database.appDao().insertSms(SmsEntity(body = "Received Bluetooth payment ${packet.packetId.take(8)}", type = "CREDIT"))
                database.appDao().insertNotification(NotificationEntity(title = "Offline payment received", message = "Payment from ${packet.senderVpa} received. Uploading to server...", type = "RECEIVED"))
                
                NotificationUtils.sendNotification(
                    context,
                    "Payment Received",
                    "Packet received from ${packet.senderVpa ?: "sender"}. Uploading to server..."
                )
                logEvent("Packet Received")
                _status.value = "Packet Received"
                
                // Send Packet ACK to sender BEFORE upload
                sendAck(packet)
                
                // Notify ViewModel to refresh transaction list
                _needsRefresh.tryEmit(Unit)
                
                // Step 3: Upload the original raw MeshPacket to the server
                uploadOriginalPacket(packet, storedTx)
            }
        }

        private fun handleAckPacket(packet: MeshPacket) {
            val remoteDevice = socket.remoteDevice
            val deviceName = try {
                remoteDevice.name?.takeIf { it.isNotBlank() } ?: remoteDevice.address
            } catch (_: SecurityException) {
                remoteDevice.address
            }

            if (packet.ciphertext == "ACK_CONNECTION_ACCEPTED" || packet.ciphertext == "ACK_HELLO_PHASE1") {
                scope.launch {
                    Log.d(TAG, "ACK_RECEIVED: Connection ACCEPTED by $deviceName")
                    _isConnected.value = true
                    logEvent("Connected to $deviceName")
                    
                    // Show Notification on Phone A
                    NotificationUtils.sendNotification(
                        context = context,
                        title = "Connection Accepted",
                        message = "Connected to $deviceName"
                    )
                    
                    // Show Toast on Phone A
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(context, "Connected to $deviceName", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
                return
            }

            if (packet.ciphertext == "ACK_CONNECTION_REJECTED") {
                scope.launch {
                    Log.d(TAG, "ACK_RECEIVED: Connection DECLINED by $deviceName")
                    _isConnected.value = false
                    _selectedDevice.value = null
                    logEvent("Connection request declined by $deviceName")
                    try {
                        socket.close()
                    } catch (_: IOException) {}
                }
                return
            }

            if (packet.ciphertext.startsWith("ACK_SETTLED:")) {
                val ackedPacketId = packet.ciphertext.removePrefix("ACK_SETTLED:")
                scope.launch {
                    val transactionId = pendingAckTransactions[ackedPacketId]
                    if (transactionId != null) {
                        val tx = database.appDao().getTransactionById(transactionId)
                        if (tx?.status != TransactionStatus.SETTLED) {
                            database.appDao().updateTransactionStatusById(transactionId, TransactionStatus.SETTLED)
                            pendingAckTransactions.remove(ackedPacketId)
                            logEvent("Payment Settled")
                            _status.value = "Payment Settled"
                            NotificationUtils.sendNotification(context, "Payment Settled", "₹${tx?.amount ?: "0"} successfully transferred")
                            _needsRefresh.tryEmit(Unit)
                        }
                    }
                }
                return
            }

            if (packet.ciphertext.startsWith("ACK:")) {
                val ackedPacketId = packet.ciphertext.removePrefix("ACK:")
                scope.launch {
                    val transactionId = pendingAckTransactions[ackedPacketId]
                    if (transactionId != null) {
                        // Mark as delivered but keep in pendingAckTransactions for settlement
                        database.appDao().updateTransactionStatusById(transactionId, TransactionStatus.DELIVERED)
                        logEvent("Packet Accepted by Phone B")
                        _status.value = "Packet Accepted by Phone B"
                    }
                }
                return
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
                NotificationUtils.sendNotification(context, "Relay Node", "Uploading payment to server...")
                database.appDao().updateTransactionStatusById(transaction.transactionId, TransactionStatus.PENDING_UPLOAD)
                try {
                    val apiService = NetworkModule.createApiService()
                    val storedPkt = database.appDao().getPacketById(transaction.packetId)
                    val meshPacket = if (storedPkt != null) {
                        MeshPacket(
                            packetId = storedPkt.packetId,
                            senderVpa = storedPkt.senderVpa,
                            ciphertext = storedPkt.ciphertext,
                            encryptedKey = storedPkt.encryptedKey,
                            iv = storedPkt.iv,
                            ttl = storedPkt.ttl,
                            createdTimestamp = storedPkt.timestamp,
                            createdAt = storedPkt.timestamp
                        )
                    } else {
                        buildPacket(transaction)
                    }
                    val response = apiService.ingestPacket(meshPacket)
                    if (response.isSuccessful && (response.body()?.outcome == "SETTLED" || response.body()?.outcome == "DUPLICATE_DROPPED")) {
                        val serverPacketHash = response.body()?.packetHash
                        if (serverPacketHash != null) {
                            database.appDao().updateTransactionPacketId(transaction.transactionId, serverPacketHash)
                        }
                        database.appDao().updateTransactionStatusById(transaction.transactionId, TransactionStatus.SETTLED)
                        database.appDao().deletePacketById(transaction.packetId)
                        logEvent("Upload settled")
                        NotificationUtils.sendNotification(context, "Settlement Confirmed", "₹${transaction.amount} added to your account")
                        
                        _needsSync.tryEmit(Unit)
                        
                        // Send Settlement ACK back to Phone A
                        val ack = MeshPacket(
                            packetId = UUID.randomUUID().toString(),
                            senderVpa = "local-device",
                            ciphertext = "ACK_SETTLED:${transaction.packetId}",
                            encryptedKey = "ACK",
                            iv = "ACK",
                            ttl = 0,
                            signature = "ack",
                            createdTimestamp = System.currentTimeMillis()
                        )
                        logEvent("Settlement ACK sent")
                        receiverThread?.write(ack)
                        
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
                    val storedPkt = database.appDao().getPacketById(transaction.packetId)
                    val meshPacket = if (storedPkt != null) {
                        MeshPacket(
                            packetId = storedPkt.packetId,
                            senderVpa = storedPkt.senderVpa,
                            ciphertext = storedPkt.ciphertext,
                            encryptedKey = storedPkt.encryptedKey,
                            iv = storedPkt.iv,
                            ttl = storedPkt.ttl,
                            createdTimestamp = storedPkt.timestamp,
                            createdAt = storedPkt.timestamp
                        )
                    } else {
                        buildPacket(transaction)
                    }
                    val response = apiService.ingestPacket(meshPacket)
                    if (response.isSuccessful && (response.body()?.outcome == "SETTLED" || response.body()?.outcome == "DUPLICATE_DROPPED")) {
                        val serverPacketHash = response.body()?.packetHash
                        if (serverPacketHash != null) {
                            database.appDao().updateTransactionPacketId(transactionId, serverPacketHash)
                        }
                        database.appDao().updateTransactionStatusById(transactionId, TransactionStatus.SETTLED)
                        database.appDao().deletePacketById(transaction.packetId)
                        logEvent("Upload settled")
                    } else {
                        database.appDao().updateTransactionStatusById(transactionId, TransactionStatus.PENDING_UPLOAD)
                    }
                } catch (_: Exception) {
                    database.appDao().updateTransactionStatusById(transactionId, TransactionStatus.PENDING_UPLOAD)
                }
            }
        }
    }

    private fun uploadOriginalPacket(packet: MeshPacket, localTx: LocalTransactionEntity) {
        scope.launch {
            logEvent("Uploading to server...")
            NotificationUtils.sendNotification(context, "Relay Node", "Uploading payment to server...")
            database.appDao().updateTransactionStatusById(localTx.transactionId, TransactionStatus.PENDING_UPLOAD)
            try {
                val apiService = NetworkModule.createApiService()
                val response = apiService.ingestPacket(packet)
                val body = response.body()
                if (response.isSuccessful) {
                    if (body?.outcome == "SETTLED" || body?.outcome == "DUPLICATE_DROPPED") {
                        // Delete the temporary "pending upload" records. A clean sync from
                        // the server will insert the real settled transaction with the correct
                        // amount and VPA — making Phone B's history look identical to Phone A.
                        database.appDao().deleteTransactionById(localTx.transactionId)
                        database.appDao().deletePacketById(packet.packetId)
                        logEvent("Upload settled")

                        NotificationUtils.sendNotification(
                            context,
                            "Settlement Confirmed",
                            "Payment from ${packet.senderVpa} settled successfully"
                        )

                        // Trigger a server sync so Phone B pulls the real transaction
                        // (with correct amount, senderVpa, receiverVpa) into its history
                        _needsSync.tryEmit(Unit)
                        _needsRefresh.tryEmit(Unit)

                        // Send Settlement ACK back to Phone A via the open socket
                        val ack = MeshPacket(
                            packetId = UUID.randomUUID().toString(),
                            senderVpa = "local-device",
                            ciphertext = "ACK_SETTLED:${packet.packetId}",
                            encryptedKey = "ACK",
                            iv = "ACK",
                            ttl = 0,
                            signature = "ack",
                            createdTimestamp = System.currentTimeMillis()
                        )
                        logEvent("Settlement ACK sent to Phone A")
                        receiverThread?.write(ack)
                    } else if (body?.outcome == "INVALID") {
                        val reason = body.reason ?: "Unknown"
                        Log.e(TAG, "Server rejected packet: $reason")
                        database.appDao().updateTransactionStatusById(localTx.transactionId, TransactionStatus.FAILED)
                        database.appDao().deletePacketById(packet.packetId)
                        NotificationUtils.sendNotification(context, "Upload Failed", "Server rejected packet: $reason")
                        _needsRefresh.tryEmit(Unit)
                    } else {
                        Log.e(TAG, "Unknown outcome: ${body?.outcome}")
                        database.appDao().updateTransactionStatusById(localTx.transactionId, TransactionStatus.PENDING_UPLOAD)
                        NotificationUtils.sendNotification(context, "Upload Failed", "Unknown outcome from server")
                    }
                } else {
                    val errBody = response.errorBody()?.string() ?: "Unknown error"
                    Log.e(TAG, "Upload failed: $errBody")
                    database.appDao().updateTransactionStatusById(localTx.transactionId, TransactionStatus.PENDING_UPLOAD)
                    NotificationUtils.sendNotification(context, "Upload Failed", "Server returned error code")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during upload", e)
                database.appDao().updateTransactionStatusById(localTx.transactionId, TransactionStatus.PENDING_UPLOAD)
                NotificationUtils.sendNotification(context, "Upload Failed", "Could not reach server: ${e.message}")
            }
        }
    }

    private fun refundLocalBalance(context: android.content.Context, transaction: LocalTransactionEntity) {
        val prefs = context.getSharedPreferences("upi_mesh_prefs", android.content.Context.MODE_PRIVATE)
        val senderVpa = transaction.senderVpa ?: ""
        if (senderVpa.isNotBlank()) {
            val currentBalStr = prefs.getString("user_balance_$senderVpa", "0") ?: "0"
            try {
                val currentBal = java.math.BigDecimal(currentBalStr)
                val txAmount = java.math.BigDecimal(transaction.amount)
                val refundedBal = currentBal.add(txAmount)
                prefs.edit().putString("user_balance_$senderVpa", refundedBal.toPlainString()).apply()
                
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(context, "Payment Expired: ₹${transaction.amount} refunded to your balance", android.widget.Toast.LENGTH_LONG).show()
                }
                
                NotificationUtils.sendNotification(
                    context,
                    "Payment Expired",
                    "₹${transaction.amount} refunded to your balance"
                )
            } catch (e: Exception) {}
        }
    }
}
