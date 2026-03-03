package com.example.lifeline.data

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

class MessageRepository(context: Context) {

    private val messageDao: MessageDao = AppDatabase.getDatabase(context).messageDao()
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    companion object {
        private const val TAG = "MessageRepository"
        private const val COLLECTION_MESSAGES = "messages"
    }

    val allMessages: Flow<List<MessageEntity>> = messageDao.getAllMessages()

    suspend fun insertMessage(message: MessageEntity) {
        messageDao.insertMessage(message)
    }

    suspend fun getMessageById(id: String): MessageEntity? {
        return messageDao.getMessageById(id)
    }

    /**
     * Upload all unsynced messages to Firebase Firestore.
     * Marks each message as synced after successful upload.
     */
    suspend fun syncToFirestore() {
        try {
            val unsyncedMessages = messageDao.getUnsyncedMessages()
            if (unsyncedMessages.isEmpty()) return

            for (message in unsyncedMessages) {
                val data = hashMapOf(
                    "id" to message.id,
                    "senderId" to message.senderId,
                    "senderName" to message.senderName,
                    "senderPhone" to message.senderPhone,
                    "status" to message.status,
                    "content" to message.content,
                    "latitude" to message.latitude,
                    "longitude" to message.longitude,
                    "timestamp" to message.timestamp
                )
                firestore.collection(COLLECTION_MESSAGES)
                    .document(message.id)
                    .set(data)
                    .await()

                // Mark as synced
                val synced = message.copy(isSynced = true)
                messageDao.insertMessage(synced)
            }
            Log.d(TAG, "Synced ${unsyncedMessages.size} messages to Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing to Firestore: ${e.message}", e)
        }
    }

    /**
     * Pull messages from Firestore that we don't have locally.
     */
    suspend fun syncFromFirestore() {
        try {
            val snapshot = firestore.collection(COLLECTION_MESSAGES)
                .get()
                .await()

            for (doc in snapshot.documents) {
                val id = doc.getString("id") ?: continue
                // Only insert if we don't already have it
                if (messageDao.getMessageById(id) == null) {
                    val message = MessageEntity(
                        id = id,
                        senderId = doc.getString("senderId") ?: "",
                        senderName = doc.getString("senderName") ?: "Unknown",
                        senderPhone = doc.getString("senderPhone") ?: "",
                        status = doc.getString("status") ?: "TRAPPED",
                        content = doc.getString("content") ?: "",
                        latitude = doc.getDouble("latitude") ?: 0.0,
                        longitude = doc.getDouble("longitude") ?: 0.0,
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        isSynced = true
                    )
                    messageDao.insertMessage(message)
                }
            }
            Log.d(TAG, "Synced from Firestore: ${snapshot.size()} docs checked")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing from Firestore: ${e.message}", e)
        }
    }
}
