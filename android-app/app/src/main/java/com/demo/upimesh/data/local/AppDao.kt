package com.demo.upimesh.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    // Offline Packets
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPacket(packet: OfflinePacketEntity)

    @Query("SELECT * FROM offline_packets ORDER BY timestamp DESC")
    fun getAllPackets(): Flow<List<OfflinePacketEntity>>

    @Delete
    suspend fun deletePacket(packet: OfflinePacketEntity)

    @Query("DELETE FROM offline_packets WHERE packetId = :packetId")
    suspend fun deletePacketById(packetId: String)

    // Transactions
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransaction(tx: LocalTransactionEntity): Long

    @Query("SELECT * FROM local_transactions ORDER BY createdAt DESC, timestamp DESC")
    fun getAllTransactions(): Flow<List<LocalTransactionEntity>>

    @Query("SELECT * FROM local_transactions ORDER BY createdAt DESC, timestamp DESC")
    suspend fun getAllTransactionsList(): List<LocalTransactionEntity>

    @Query("SELECT * FROM local_transactions WHERE transactionId = :transactionId LIMIT 1")
    suspend fun getTransactionById(transactionId: String): LocalTransactionEntity?

    @Query("SELECT * FROM local_transactions WHERE packetId = :packetId LIMIT 1")
    suspend fun getTransactionByPacketId(packetId: String): LocalTransactionEntity?

    @Query("SELECT * FROM local_transactions WHERE status = :status ORDER BY createdAt DESC, timestamp DESC")
    fun getTransactionsByStatus(status: String): Flow<List<LocalTransactionEntity>>

    @Query("UPDATE local_transactions SET status = :status WHERE packetId = :packetId")
    suspend fun updateTransactionStatus(packetId: String, status: String)

    @Query("UPDATE local_transactions SET status = :status WHERE transactionId = :transactionId")
    suspend fun updateTransactionStatusById(transactionId: String, status: String)

    @Query("UPDATE local_transactions SET retryCount = retryCount + 1 WHERE transactionId = :transactionId")
    suspend fun incrementTransactionRetryCount(transactionId: String)

    // SMS Inbox
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSms(sms: SmsEntity)

    @Query("SELECT * FROM sms_inbox ORDER BY timestamp DESC")
    fun getAllSms(): Flow<List<SmsEntity>>

    // Notifications
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notif: NotificationEntity)

    @Query("SELECT * FROM app_notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<NotificationEntity>>
}
