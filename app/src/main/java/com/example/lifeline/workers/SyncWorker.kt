package com.example.lifeline.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.lifeline.data.MessageRepository

/**
 * SyncWorker — Background worker for Firebase Firestore sync.
 *
 * Runs periodically (every 15 minutes) when network is available.
 * Uploads unsynced local messages and pulls new messages from the cloud.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SyncWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "SyncWorker started")
        return try {
            val repository = MessageRepository(applicationContext)

            // Upload unsynced messages
            repository.syncToFirestore()

            // Pull new messages from cloud
            repository.syncFromFirestore()

            Log.d(TAG, "SyncWorker completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "SyncWorker failed: ${e.message}", e)
            Result.retry()
        }
    }
}
