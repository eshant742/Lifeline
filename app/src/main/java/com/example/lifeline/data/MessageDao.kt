package com.example.lifeline.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY priority DESC, timestamp DESC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE messageType = 'SOS' OR messageType = 'STATUS_UPDATE' ORDER BY priority DESC, timestamp DESC")
    fun getSosMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE isSynced = 0")
    suspend fun getUnsyncedMessages(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE timestamp > :sinceTimestamp AND isSynced = 0")
    suspend fun getUnsyncedMessagesSince(sinceTimestamp: Long): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessageById(id: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE messageType = :type ORDER BY timestamp DESC")
    fun getMessagesByType(type: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE status = 'TRAPPED' OR status = 'INJURED' ORDER BY priority DESC, timestamp DESC")
    fun getActiveDistressSignals(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE messageType = 'DANGER_ZONE' ORDER BY timestamp DESC")
    fun getDangerZones(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE messageType = 'RESOURCE' ORDER BY timestamp DESC")
    fun getResources(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE senderId = :senderId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestMessageFromSender(senderId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE messageType = 'HEARTBEAT' AND senderId = :senderId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastHeartbeat(senderId: String): MessageEntity?

    @Query("SELECT COUNT(*) FROM messages WHERE status = 'TRAPPED' OR status = 'INJURED'")
    fun getActiveDistressCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages WHERE status = 'SAFE'")
    fun getSafeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages")
    fun getTotalMessageCount(): Flow<Int>

    @Query("UPDATE messages SET rescueStatus = :rescueStatus, rescuerId = :rescuerId, rescuerName = :rescuerName WHERE id = :messageId")
    suspend fun updateRescueStatus(messageId: String, rescueStatus: String, rescuerId: String, rescuerName: String)

    @Query("UPDATE messages SET isSynced = 1 WHERE id = :messageId")
    suspend fun markAsSynced(messageId: String)

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int): List<MessageEntity>

    @Query("DELETE FROM messages WHERE timestamp < :olderThan AND messageType = 'HEARTBEAT'")
    suspend fun pruneOldHeartbeats(olderThan: Long)
}
