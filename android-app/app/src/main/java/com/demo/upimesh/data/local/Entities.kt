package com.demo.upimesh.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.demo.upimesh.model.TransactionStatus

@Entity(tableName = "offline_packets")
data class OfflinePacketEntity(
    @PrimaryKey val packetId: String,
    val senderVpa: String,
    val receiverVpa: String,
    val amount: String,
    val ciphertext: String,
    val encryptedKey: String,
    val iv: String,
    val ttl: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "QUEUED_IN_MESH"
)

@Entity(tableName = "local_transactions")
data class LocalTransactionEntity(
    @PrimaryKey val transactionId: String,
    val packetId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val senderVpa: String = "",
    val receiverVpa: String = "",
    val amount: String = "0",
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = TransactionStatus.PENDING,
    val retryCount: Int = 0,
    val digitalSignature: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val note: String = ""
)

@Entity(tableName = "sms_inbox")
data class SmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String = "BANK-UPI",
    val body: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String // DEBIT, CREDIT, SETTLEMENT, ALERT
)

@Entity(tableName = "app_notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val message: String,
    val type: String,
    val timestamp: Long = System.currentTimeMillis()
)
