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
                    val payload = CryptoUtils.unwrapPayload(packet, context)
                    if (payload != null) {
                        repositoryScope.launch {
                            persistIncomingPacket(packet, payload)
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
        return try {
            val req = DemoSendRequest(senderVpa, receiverVpa, amount, pin, 5, "phone-alice")
            // Use demoSend which creates the packet AND injects it into the mesh simulator
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
                // Save locally to Room DB
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
                persistLocalTransaction(packet.packetId, senderVpa, receiverVpa, amount, note, TransactionStatus.PENDING)
                sendPacketOverBluetooth(packet)
                // Generate local SMS & Notification
                appDao.insertSms(
                    SmsEntity(
                        body = "Debit Alert: ₹$amount sent to $receiverVpa via Offline BLE Mesh. Packet ID: ${packet.packetId.take(8)}",
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
                // Local fallback simulation if server is unreachable
                val localId = UUID.randomUUID().toString()
                val packet = MeshPacket(
                    packetId = localId,
                    senderVpa = senderVpa,
                    ciphertext = "OFFLINE_AES_GCM_CIPHERTEXT_" + UUID.randomUUID().toString().replace("-", ""),
                    encryptedKey = "RSA_OAEP_ENCRYPTED_KEY_HEADER",
                    iv = "IV_VECTOR_12B",
                    ttl = 5
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
                persistLocalTransaction(packet.packetId, senderVpa, receiverVpa, amount, note, TransactionStatus.PENDING)
                appDao.insertSms(
                    SmsEntity(
                        body = "Offline Debit: ₹$amount queued for BLE gossip to $receiverVpa. Packet ID: ${localId.take(8)}",
                        type = "DEBIT"
                    )
                )
                Result.success(packet)
            }
        } catch (e: Exception) {
            // Local fallback simulation if offline
            val localId = UUID.randomUUID().toString()
            val packet = MeshPacket(
                packetId = localId,
                senderVpa = senderVpa,
                ciphertext = "OFFLINE_AES_GCM_CIPHERTEXT_" + UUID.randomUUID().toString().replace("-", ""),
                encryptedKey = "RSA_OAEP_ENCRYPTED_KEY_HEADER",
                iv = "IV_VECTOR_12B",
                ttl = 5
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
            persistLocalTransaction(packet.packetId, senderVpa, receiverVpa, amount, note, TransactionStatus.PENDING)
            Result.success(packet)
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

    suspend fun flushBridgeUploads(): Result<FlushResultResponse> {
        return try {
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
                    appDao.updateTransactionStatusById(tx.transactionId, TransactionStatus.COMPLETED)
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
