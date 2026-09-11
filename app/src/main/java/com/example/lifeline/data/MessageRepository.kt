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
     * Includes ALL 24 fields (original 10 + new 14 mesh/medical/rescue fields).
     * Uses markAsSynced() instead of re-inserting the entire entity.
     */
    suspend fun syncToFirestore() {
        try {
            val unsyncedMessages = messageDao.getUnsyncedMessages()
            if (unsyncedMessages.isEmpty()) return

            for (message in unsyncedMessages) {
                val data = hashMapOf(
                    // Original fields
                    "id" to message.id,
                    "senderId" to message.senderId,
                    "senderName" to message.senderName,
                    "senderPhone" to message.senderPhone,
                    "status" to message.status,
                    "content" to message.content,
                    "latitude" to message.latitude,
                    "longitude" to message.longitude,
                    "timestamp" to message.timestamp,
                    // Mesh networking fields
                    "hopCount" to message.hopCount,
                    "maxHops" to message.maxHops,
                    "messageType" to message.messageType,
                    "priority" to message.priority,
                    // Medical profile
                    "bloodType" to message.bloodType,
                    "allergies" to message.allergies,
                    "medicalConditions" to message.medicalConditions,
                    "emergencyContact" to message.emergencyContact,
                    // Rescue coordination
                    "rescueStatus" to message.rescueStatus,
                    "rescuerId" to message.rescuerId,
                    "rescuerName" to message.rescuerName,
                    "ackForMessageId" to message.ackForMessageId,
                    // Role & metadata
                    "senderRole" to message.senderRole,
                    "isRelayed" to message.isRelayed
                )
                firestore.collection(COLLECTION_MESSAGES)
                    .document(message.id)
                    .set(data)
                    .await()

                // Mark as synced using dedicated DAO method (no re-insert)
                messageDao.markAsSynced(message.id)
            }
            Log.d(TAG, "Synced ${unsyncedMessages.size} messages to Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing to Firestore: ${e.message}", e)
        }
    }

    /**
     * Pull messages from Firestore that we don't have locally.
     * Reads ALL 24 fields from the cloud document.
     */
    suspend fun syncFromFirestore() {
        try {
            val snapshot = firestore.collection(COLLECTION_MESSAGES)
                .get()
                .await()

            var insertedCount = 0
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
                        isSynced = true,
                        // Mesh networking fields
                        hopCount = (doc.getLong("hopCount") ?: 0L).toInt(),
                        maxHops = (doc.getLong("maxHops") ?: 7L).toInt(),
                        messageType = doc.getString("messageType") ?: "SOS",
                        priority = (doc.getLong("priority") ?: 3L).toInt(),
                        // Medical profile
                        bloodType = doc.getString("bloodType") ?: "",
                        allergies = doc.getString("allergies") ?: "",
                        medicalConditions = doc.getString("medicalConditions") ?: "",
                        emergencyContact = doc.getString("emergencyContact") ?: "",
                        // Rescue coordination
                        rescueStatus = doc.getString("rescueStatus") ?: "",
                        rescuerId = doc.getString("rescuerId") ?: "",
                        rescuerName = doc.getString("rescuerName") ?: "",
                        ackForMessageId = doc.getString("ackForMessageId") ?: "",
                        // Role & metadata
                        senderRole = doc.getString("senderRole") ?: "VICTIM",
                        isRelayed = doc.getBoolean("isRelayed") ?: false
                    )
                    messageDao.insertMessage(message)
                    insertedCount++
                }
            }
            Log.d(TAG, "Synced from Firestore: $insertedCount new messages (${snapshot.size()} total checked)")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing from Firestore: ${e.message}", e)
        }
    }
}
