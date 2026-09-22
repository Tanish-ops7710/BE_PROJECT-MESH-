package com.demo.upimesh.data.repository

import com.demo.upimesh.data.api.ApiService
import com.demo.upimesh.data.local.AppDao
import com.demo.upimesh.data.local.LocalTransactionEntity
import com.demo.upimesh.data.local.NotificationEntity
import com.demo.upimesh.data.local.OfflinePacketEntity
import com.demo.upimesh.data.local.SmsEntity
import android.content.Context
import com.demo.upimesh.model.*
import com.demo.upimesh.util.BluetoothMeshManager
import com.demo.upimesh.util.CryptoUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID

class UpiRepository(
    private var apiService: ApiService,
    private val appDao: AppDao,
    private val context: Context? = null
) {
    private var bluetoothMeshManager: BluetoothMeshManager? = null
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    fun updateApiService(newApiService: ApiService) {
        this.apiService = newApiService
    }

    fun initBluetoothMesh() {
        if (context == null) return
        try {
            if (bluetoothMeshManager == null) {
                bluetoothMeshManager = BluetoothMeshManager(context)
            }
            bluetoothMeshManager?.start(
                packetCallback = { packet ->
                    // This node received a packet — try to unwrap (if we are the receiver)
                    val payload = CryptoUtils.unwrapPayload(packet, context)
                    if (payload != null) {
                        repositoryScope.launch {
                            persistIncomingPacket(packet, payload)
                        }
                    }
                },
                relayPersistCallback = { packet ->
                    // Store-and-forward: persist the relayed packet to Room BEFORE forwarding
                    repositoryScope.launch {
                        try {
                            val existing = appDao.getPacketById(packet.packetId)
                            if (existing == null) {
                                appDao.insertPacket(
                                    OfflinePacketEntity(
                                        packetId = packet.packetId,
                                        senderVpa = packet.senderVpa,
                                        receiverVpa = "",
                                        amount = "0",
                                        ciphertext = packet.ciphertext,
                                        encryptedKey = packet.encryptedKey,
                                        iv = packet.iv,
                                        ttl = packet.ttl
                                    )
                                )
                                // Record in transactions as RELAYING
                                val txExisting = appDao.getTransactionByPacketId(packet.packetId)
                                if (txExisting == null) {
                                    val ts = System.currentTimeMillis()
                                    appDao.insertTransaction(
                                        LocalTransactionEntity(
                                            transactionId = UUID.randomUUID().toString(),
                                            packetId = packet.packetId,
                                            senderId = packet.senderVpa,
                                            receiverId = "",
                                            senderVpa = packet.senderVpa,
                                            receiverVpa = "",
                                            amount = "0",
                                            timestamp = ts,
                                            status = TransactionStatus.RELAYING,
                                            retryCount = 0,
                                            digitalSignature = "",
                                            createdAt = ts,
                                            note = "Relay hop"
                                        )
                                    )
                                }
                            }
                            // Auto-upload if this device has internet access
                            uploadPendingPackets()
                        } catch (e: Exception) {
                            // Persistence failure should not block relay
                        }
                    }
                },
                peerCallback = {}
            )
        } catch (e: Exception) {
            // Keep startup resilient when Bluetooth is unavailable or blocked.
        }
    }

    fun stopBluetoothMesh() {
        bluetoothMeshManager?.stop()
        bluetoothMeshManager = null
    }

    fun sendPacketOverBluetooth(packet: MeshPacket) {
        bluetoothMeshManager?.sendPacket(packet)
    }

    private suspend fun persistIncomingPacket(packet: MeshPacket, payload: CryptoUtils.ParsedPayload) {
        val existing = appDao.getTransactionByPacketId(packet.packetId)
        if (existing != null) return

        val tx = LocalTransactionEntity(
            transactionId = UUID.randomUUID().toString(),
            packetId = packet.packetId,
            senderId = payload.senderVpa,
            receiverId = payload.receiverVpa,
            senderVpa = payload.senderVpa,
            receiverVpa = payload.receiverVpa,
            amount = payload.amount,
            timestamp = payload.timestamp,
            status = TransactionStatus.PENDING_UPLOAD,
            retryCount = 0,
            digitalSignature = packet.signature ?: "",
            createdAt = payload.timestamp,
            note = payload.note
        )
        appDao.insertTransaction(tx)
        appDao.insertSms(
            SmsEntity(
                body = "Received offline payment from ${payload.senderVpa}: ₹${payload.amount}",
                type = "DEBIT"
            )
        )
    }
    val allPackets: Flow<List<OfflinePacketEntity>> = appDao.getAllPackets()
    val allTransactions: Flow<List<LocalTransactionEntity>> = appDao.getAllTransactions()
    val allSms: Flow<List<SmsEntity>> = appDao.getAllSms()
    val allNotifications: Flow<List<NotificationEntity>> = appDao.getAllNotifications()

    private fun buildDigitalSignature(senderId: String, receiverId: String, amount: BigDecimal, timestamp: Long, packetId: String): String {
        return UUID.nameUUIDFromBytes("$senderId:$receiverId:${amount.toPlainString()}:$timestamp:$packetId".toByteArray()).toString()
    }

    private suspend fun persistLocalTransaction(
        packetId: String,
        senderVpa: String,
        receiverVpa: String,
        amount: BigDecimal,
        note: String,
        status: String = TransactionStatus.PENDING_BLUETOOTH,
        retryCount: Int = 0
    ): LocalTransactionEntity {
        appDao.getTransactionByPacketId(packetId)?.let { existing ->
            return existing
        }

        val timestamp = System.currentTimeMillis()
        val tx = LocalTransactionEntity(
            transactionId = UUID.randomUUID().toString(),
            packetId = packetId,
            senderId = senderVpa,
            receiverId = receiverVpa,
            senderVpa = senderVpa,
            receiverVpa = receiverVpa,
            amount = amount.toPlainString(),
            timestamp = timestamp,
            status = status,
            retryCount = retryCount,
            digitalSignature = buildDigitalSignature(senderVpa, receiverVpa, amount, timestamp, packetId),
            createdAt = timestamp,
            note = note
        )
        appDao.insertTransaction(tx)
        return tx
    }

    suspend fun createAndQueuePacket(
        senderVpa: String,
        receiverVpa: String,
        amount: BigDecimal,
        pin: String,
        note: String
    ): Result<MeshPacket> {
        val localId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        // --- Step 1: Try to build the ciphertext entirely offline ---
        val offlineCiphertext: String? = if (context != null) {
            CryptoUtils.encryptPaymentOffline(context, senderVpa, receiverVpa, amount, pin, localId, timestamp)
        } else null

        // --- Step 2: If offline encryption succeeded, create packet locally ---
        if (offlineCiphertext != null) {
            val packet = MeshPacket(
                packetId = localId,
                senderVpa = senderVpa,
                ciphertext = offlineCiphertext,
                encryptedKey = "HYBRID_RSA_OAEP_AES_GCM",
                iv = "EMBEDDED_IN_CIPHERTEXT",
                ttl = 5,
                createdTimestamp = timestamp
            )
            appDao.insertPacket(
                OfflinePacketEntity(
                    packetId = packet.packetId,
                    senderVpa = senderVpa,
                    receiverVpa = receiverVpa,
                    amount = amount.toPlainString(),
                    ciphertext = packet.ciphertext,
                    encryptedKey = packet.encryptedKey,
                    iv = packet.iv,
                    ttl = packet.ttl
                )
            )
            persistLocalTransaction(packet.packetId, senderVpa, receiverVpa, amount, note, TransactionStatus.QUEUED_OFFLINE)
            sendPacketOverBluetooth(packet)
            appDao.insertSms(
                SmsEntity(
                    body = "Debit Alert: ₹$amount queued for $receiverVpa via Offline BLE Mesh. ID: ${localId.take(8)}",
                    type = "DEBIT"
                )
            )
            appDao.insertNotification(
                NotificationEntity(
                    title = "Offline Payment Created",
                    message = "₹$amount is encrypted and broadcasting via BLE mesh to $receiverVpa.",
                    type = "BLE_BROADCAST"
                )
            )
            repositoryScope.launch {
                uploadPendingPackets()
            }
            return Result.success(packet)
        }

        // --- Step 3: Offline key not cached yet — try server (needs internet) ---
        return try {
            val req = DemoSendRequest(senderVpa, receiverVpa, amount, pin, 5, "phone-alice")
            val resp = apiService.demoSend(req)
            if (resp.isSuccessful && resp.body() != null) {
                val result = resp.body()!!
                val packetId = result["packetId"]?.toString() ?: UUID.randomUUID().toString()
                val ttl = (result["ttl"] as? Double)?.toInt() ?: 5
                val packet = MeshPacket(
                    packetId = packetId,
                    senderVpa = senderVpa,
                    ciphertext = result["ciphertextPreview"]?.toString() ?: "ENCRYPTED",
                    encryptedKey = "RSA_OAEP_KEY",
                    iv = "AES_GCM_IV",
                    ttl = ttl
                )
                appDao.insertPacket(
                    OfflinePacketEntity(
                        packetId = packet.packetId,
                        senderVpa = senderVpa,
                        receiverVpa = receiverVpa,
                        amount = amount.toPlainString(),
                        ciphertext = packet.ciphertext,
                        encryptedKey = packet.encryptedKey,
                        iv = packet.iv,
                        ttl = packet.ttl
                    )
                )
                persistLocalTransaction(packet.packetId, senderVpa, receiverVpa, amount, note, TransactionStatus.QUEUED_OFFLINE)
                sendPacketOverBluetooth(packet)
                appDao.insertSms(
                    SmsEntity(
                        body = "Debit Alert: ₹$amount sent to $receiverVpa via BLE Mesh. ID: ${packet.packetId.take(8)}",
                        type = "DEBIT"
                    )
                )
                appDao.insertNotification(
                    NotificationEntity(
                        title = "Offline Mesh Packet Created",
                        message = "₹$amount is queued and broadcasting via BLE mesh.",
                        type = "BLE_BROADCAST"
                    )
                )
                Result.success(packet)
            } else {
                Result.failure(Exception("Server unavailable and no cached server key found. Login while online first to enable offline payments."))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Cannot create payment: no cached server key. Please login while online at least once to enable offline payments.", e))
        }
    }

    suspend fun getMeshState(): Result<MeshStateResponse> {
        return try {
            val resp = apiService.getMeshState()
            if (resp.isSuccessful && resp.body() != null) {
                Result.success(resp.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch mesh state"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun triggerGossip(): Result<GossipResultResponse> {
        return try {
            val resp = apiService.gossip()
            if (resp.isSuccessful && resp.body() != null) {
                Result.success(resp.body()!!)
            } else {
                Result.failure(Exception("Gossip failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadPendingPackets(): Result<Int> {
        return try {
            val pendingPackets = appDao.getAllPacketsList()
            var settledCount = 0
            for (pkt in pendingPackets) {
                try {
                    val meshPacket = MeshPacket(
                        packetId = pkt.packetId,
                        senderVpa = pkt.senderVpa,
                        ciphertext = pkt.ciphertext,
                        encryptedKey = pkt.encryptedKey,
                        iv = pkt.iv,
                        ttl = pkt.ttl,
                        createdTimestamp = pkt.timestamp,
                        createdAt = pkt.timestamp
                    )
                    val resp = apiService.ingestPacket(
                        packet = meshPacket,
                        bridgeNodeId = "android-device-bridge",
                        hopCount = kotlin.math.max(1, 5 - pkt.ttl)
                    )
                    if (resp.isSuccessful && resp.body() != null) {
                        val body = resp.body()!!
                        if (body.outcome == "SETTLED" || body.outcome == "DUPLICATE_DROPPED") {
                            appDao.updateTransactionStatus(pkt.packetId, TransactionStatus.SETTLED)
                            appDao.deletePacketById(pkt.packetId)
                            appDao.insertSms(
                                SmsEntity(
                                    body = "Payment Settled: ₹${pkt.amount} for ${pkt.receiverVpa}. Settled by Bank Server.",
                                    type = "SETTLEMENT"
                                )
                            )
                            appDao.insertNotification(
                                NotificationEntity(
                                    title = "Payment Settled",
                                    message = "₹${pkt.amount} transaction (ID: ${pkt.packetId.take(8)}) settled by Bank Server.",
                                    type = "SETTLEMENT"
                                )
                            )
                            settledCount++
                        }
                    }
                } catch (_: Exception) {
                    // Keep in queue for retry if network is unavailable
                }
            }
            syncTransactionsWithServer()
            Result.success(settledCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncTransactionsWithServer() {
        try {
            val resp = apiService.getTransactions()
            if (resp.isSuccessful && resp.body() != null) {
                val serverTxs = resp.body()!!
                val localTxs = appDao.getAllTransactionsList()
                
                // 1. Update existing local transactions
                for (localTx in localTxs) {
                    val matched = serverTxs.find { sTx ->
                        (sTx.packetId != null && sTx.packetId == localTx.packetId) ||
                        (sTx.senderVpa == localTx.senderVpa && sTx.receiverVpa == localTx.receiverVpa && sTx.amount.toPlainString() == localTx.amount)
                    }
                    if (matched != null && (matched.status.equals("SETTLED", ignoreCase = true) || matched.status.equals("COMPLETED", ignoreCase = true))) {
                        if (localTx.status != TransactionStatus.SETTLED || localTx.amount == "pending" || localTx.amount == "0" || localTx.receiverVpa.isBlank()) {
                            appDao.updateTransactionDetails(
                                transactionId = localTx.transactionId,
                                amount = matched.amount.toPlainString(),
                                senderVpa = matched.senderVpa ?: localTx.senderVpa,
                                receiverVpa = matched.receiverVpa ?: localTx.receiverVpa,
                                status = TransactionStatus.SETTLED
                            )
                            appDao.deletePacketById(localTx.packetId)
                        }
                    }
                }
                
                // 2. Insert missing transactions from server
                val localPacketIds = localTxs.mapNotNull { it.packetId }.toSet()
                for (sTx in serverTxs) {
                    if (sTx.packetId != null && !localPacketIds.contains(sTx.packetId)) {
                        val newTx = LocalTransactionEntity(
                            transactionId = sTx.transactionId,
                            packetId = sTx.packetId ?: UUID.randomUUID().toString(),
                            senderId = sTx.senderId,
                            receiverId = sTx.receiverId,
                            senderVpa = sTx.senderVpa ?: "",
                            receiverVpa = sTx.receiverVpa ?: "",
                            amount = sTx.amount.toPlainString(),
                            timestamp = sTx.timestamp,
                            status = if (sTx.status.equals("SETTLED", ignoreCase = true)) TransactionStatus.SETTLED else sTx.status,
                            digitalSignature = sTx.digitalSignature ?: "",
                            createdAt = sTx.createdAt,
                            note = "Synced from server"
                        )
                        appDao.insertTransaction(newTx)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun flushBridgeUploads(): Result<FlushResultResponse> {
        return try {
            uploadPendingPackets()
            val resp = apiService.flush()
            if (resp.isSuccessful && resp.body() != null) {
                val flushRes = resp.body()!!
                // Add settlement SMS
                appDao.insertSms(
                    SmsEntity(
                        body = "Bridge Ingestion Complete: ${flushRes.uploadsAttempted} packet(s) processed by Bank Server.",
                        type = "SETTLEMENT"
                    )
                )
                appDao.insertNotification(
                    NotificationEntity(
                        title = "Bank Settlement Complete",
                        message = "${flushRes.uploadsAttempted} mesh packet(s) settled via Bridge Node.",
                        type = "SETTLEMENT"
                    )
                )
                appDao.getTransactionsByStatus(TransactionStatus.PENDING).first().forEach { tx ->
                    appDao.updateTransactionStatusById(tx.transactionId, TransactionStatus.SETTLED)
                }
                Result.success(flushRes)
            } else {
                appDao.getTransactionsByStatus(TransactionStatus.PENDING).first().forEach { tx ->
                    appDao.updateTransactionStatusById(tx.transactionId, TransactionStatus.FAILED)
                }
                Result.failure(Exception("Flush failed"))
            }
        } catch (e: Exception) {
            appDao.getTransactionsByStatus(TransactionStatus.PENDING).first().forEach { tx ->
                appDao.updateTransactionStatusById(tx.transactionId, TransactionStatus.FAILED)
            }
            Result.failure(e)
        }
    }

    suspend fun fetchTransactions(): Result<List<Transaction>> {
        return try {
            syncTransactionsWithServer()
            val resp = apiService.getTransactions()
            if (resp.isSuccessful && resp.body() != null) {
                Result.success(resp.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch transactions"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAccounts(): Result<List<Account>> {
        return try {
            val resp = apiService.getAccounts()
            if (resp.isSuccessful && resp.body() != null) {
                Result.success(resp.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch accounts"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Dynamic dynamic authentication repository methods

    suspend fun sendOtp(phoneNumber: String): Result<Map<String, Any>> {
        return try {
            val resp = apiService.sendOtp(phoneNumber)
            if (resp.isSuccessful && resp.body() != null) {
                Result.success(resp.body()!!)
            } else {
                Result.failure(Exception("Failed to send OTP"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyOtp(phoneNumber: String, otp: String): Result<Map<String, Any>> {
        return try {
            val resp = apiService.verifyOtp(phoneNumber, otp)
            if (resp.isSuccessful && resp.body() != null) {
                Result.success(resp.body()!!)
            } else {
                Result.failure(Exception("Invalid OTP"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchServerPublicKey(): Result<String> {
        return try {
            val resp = apiService.getServerPublicKey()
            if (resp.isSuccessful && resp.body() != null) {
                val key = resp.body()!!.publicKey
                context?.getSharedPreferences("upi_mesh_prefs", Context.MODE_PRIVATE)
                    ?.edit()?.putString("server_public_key", key)?.apply()
                Result.success(key)
            } else {
                Result.failure(Exception("Failed to fetch server public key"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerUser(
        vpa: String,
        holderName: String,
        phoneNumber: String,
        mpin: String,
        email: String,
        bankName: String,
        bankAccountNumber: String,
        cardNumber: String,
        expiryDate: String,
        cvv: String
    ): Result<Account> {
        return try {
            val resp = apiService.registerUser(
                AuthRegisterRequest(
                    vpa = vpa,
                    holderName = holderName,
                    phoneNumber = phoneNumber,
                    mpin = mpin,
                    email = email,
                    bankName = bankName,
                    bankAccountNumber = bankAccountNumber,
                    cardNumber = cardNumber,
                    expiryDate = expiryDate,
                    cvv = cvv
                )
            )
            if (resp.isSuccessful && resp.body() != null) {
                fetchServerPublicKey()
                Result.success(resp.body()!!)
            } else {
                val errorMsg = resp.errorBody()?.string() ?: "Registration failed"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Cannot connect to server. Ensure your backend is running and the Server IP is configured correctly.", e))
        }
    }

    suspend fun loginUser(vpa: String, mpin: String): Result<AuthLoginResponse> {
        return try {
            val resp = apiService.loginUser(AuthLoginRequest(vpa, mpin))
            if (resp.isSuccessful && resp.body() != null) {
                fetchServerPublicKey()
                Result.success(resp.body()!!)
            } else {
                val errorMsg = resp.errorBody()?.string() ?: "Invalid VPA or MPIN"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Cannot connect to server. Ensure your backend is running and the Server IP is configured correctly.", e))
        }
    }

    suspend fun logoutUser(token: String): Result<Map<String, Any>> {
        return try {
            val resp = apiService.logoutUser(token)
            if (resp.isSuccessful && resp.body() != null) {
                Result.success(resp.body()!!)
            } else {
                Result.failure(Exception("Logout failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSessionAccount(token: String): Result<Account> {
        return try {
            val resp = apiService.getSessionAccount(token)
            if (resp.isSuccessful && resp.body() != null) {
                Result.success(resp.body()!!)
            } else {
                Result.failure(Exception("Session expired"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun changeMpin(vpa: String, oldMpin: String, newMpin: String): Result<Map<String, Any>> {
        return try {
            val resp = apiService.changeMpin(vpa, oldMpin, newMpin)
            if (resp.isSuccessful && resp.body() != null) {
                Result.success(resp.body()!!)
            } else {
                Result.failure(Exception("Failed to change MPIN"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resetMpin(vpa: String, newMpin: String): Result<Map<String, Any>> {
        return try {
            val resp = apiService.resetMpin(vpa, newMpin)
            if (resp.isSuccessful && resp.body() != null) {
                Result.success(resp.body()!!)
            } else {
                Result.failure(Exception("Failed to reset MPIN"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAccount(vpa: String): Result<Map<String, Any>> {
        return try {
            val resp = apiService.deleteAccount(vpa)
            if (resp.isSuccessful && resp.body() != null) {
                Result.success(resp.body()!!)
            } else {
                Result.failure(Exception("Failed to delete account"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
