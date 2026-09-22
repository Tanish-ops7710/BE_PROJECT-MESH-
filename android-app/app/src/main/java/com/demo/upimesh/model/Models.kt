package com.demo.upimesh.model

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal
import java.util.UUID

data class MeshPacket(
    @SerializedName("packetId") val packetId: String,
    @SerializedName("senderVpa") val senderVpa: String = "",
    @SerializedName("ciphertext") val ciphertext: String,
    @SerializedName("encryptedKey") val encryptedKey: String = "",
    @SerializedName("iv") val iv: String = "",
    @SerializedName("ttl") var ttl: Int = 5,
    @SerializedName("signature") val signature: String? = null,
    @SerializedName("createdTimestamp") val createdTimestamp: Long = System.currentTimeMillis(),
    @SerializedName("createdAt") val createdAt: Long = createdTimestamp
)

data class Account(
    @SerializedName("vpa") val vpa: String,
    @SerializedName("holderName") val holderName: String,
    @SerializedName("balance") val balance: BigDecimal,
    @SerializedName("phoneNumber") val phoneNumber: String? = null,
    @SerializedName("pendingBalance") val pendingBalance: BigDecimal? = BigDecimal.ZERO,
    @SerializedName("dailyLimit") val dailyLimit: BigDecimal? = BigDecimal("10000.00"),
    @SerializedName("monthlyLimit") val monthlyLimit: BigDecimal? = BigDecimal("100000.00"),
    @SerializedName("isFrozen") val isFrozen: Boolean = false,
    @SerializedName("email") val email: String? = null,
    @SerializedName("bankName") val bankName: String? = null,
    @SerializedName("bankAccountNumber") val bankAccountNumber: String? = null,
    @SerializedName("maskedCardNumber") val maskedCardNumber: String? = null,
    @SerializedName("expiryDate") val expiryDate: String? = null
)

object TransactionStatus {
    const val PENDING = "Pending"
    const val PENDING_BLUETOOTH = "Pending Bluetooth"
    const val DELIVERED = "Delivered"
    const val PENDING_UPLOAD = "Pending Upload"
    const val COMPLETED = "Completed"
    const val FAILED = "Failed"
    // Phase 2 statuses
    const val QUEUED_OFFLINE = "Queued Offline"
    const val RELAYING = "Relaying"
    const val UPLOADED = "Uploaded"
    const val SETTLED = "Settled"
    const val EXPIRED = "Expired"
    const val DUPLICATE = "Duplicate"
}

data class Transaction(
    @SerializedName("transactionId") val transactionId: String = UUID.randomUUID().toString(),
    @SerializedName("senderId") val senderId: String = "",
    @SerializedName("receiverId") val receiverId: String = "",
    @SerializedName("amount") val amount: BigDecimal = BigDecimal.ZERO,
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis(),
    @SerializedName("status") val status: String = TransactionStatus.PENDING,
    @SerializedName("retryCount") val retryCount: Int = 0,
    @SerializedName("digitalSignature") val digitalSignature: String = "",
    @SerializedName("createdAt") val createdAt: Long = System.currentTimeMillis(),
    @SerializedName(value = "packetId", alternate = ["packetHash"]) val packetId: String? = null,
    @SerializedName("senderVpa") val senderVpa: String? = null,
    @SerializedName("receiverVpa") val receiverVpa: String? = null,
    @SerializedName("hopCount") val hopCount: Int = 0,
    @SerializedName("bridgeNodeId") val bridgeNodeId: String = "local-bridge"
)

data class DemoSendRequest(
    val senderVpa: String,
    val receiverVpa: String,
    val amount: BigDecimal,
    val pin: String,
    val ttl: Int = 5,
    val startDevice: String = "phone-alice"
)

data class RegisterRequest(
    val vpa: String,
    val holderName: String,
    val initialBalance: BigDecimal = BigDecimal("5000.00")
)

data class IngestResponse(
    val outcome: String,
    val reason: String?,
    val transactionId: Long?,
    val packetHash: String?
)

data class MeshStateResponse(
    val devices: List<MeshDeviceData>,
    val idempotencyCacheSize: Int
)

data class MeshDeviceData(
    val deviceId: String,
    val hasInternet: Boolean,
    val packetCount: Int,
    val packetIds: List<String>
)

data class GossipResultResponse(
    val transfers: Int,
    val deviceCounts: Map<String, Int>
)

data class FlushResultResponse(
    val uploadsAttempted: Int,
    val results: List<Map<String, Any>>
)

data class ServerKeyResponse(
    val publicKey: String,
    val algorithm: String,
    val hybridScheme: String
)

data class AuthRegisterRequest(
    val vpa: String,
    val holderName: String,
    val phoneNumber: String,
    val mpin: String,
    val email: String,
    val bankName: String,
    val bankAccountNumber: String,
    val cardNumber: String,
    val expiryDate: String,
    val cvv: String
)

data class AuthLoginRequest(
    val vpa: String,
    val mpin: String
)

data class AuthLoginResponse(
    val token: String,
    val vpa: String,
    val account: Account
)
