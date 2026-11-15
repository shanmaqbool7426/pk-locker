package com.pksafe.lock.manager.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.messaging.FirebaseMessaging
import com.pksafe.lock.manager.data.ApiService
import com.pksafe.lock.manager.util.Constants
import kotlinx.coroutines.CompletableDeferred
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Background worker that ensures FCM token is always available and synced to server.
 * Runs periodically (every 6 hours) as a safety net.
 * CRITICAL: FCM token must NEVER be null — this worker guarantees it.
 */
class FcmTokenWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "FCM_TOKEN_WORKER"
        const val WORK_NAME = "FcmTokenSync"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting FCM token verification...")
        
        val prefs = applicationContext.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val existingToken = prefs.getString("fcm_token", null)
        val imei = prefs.getString("device_imei", null)
        val isCustomer = prefs.getBoolean("is_customer", false)
        val isUserLoggedIn = prefs.getBoolean("is_logged_in", false)
        val authToken = prefs.getString("auth_token", null)

        try {
            // Fetch fresh token from Firebase using CompletableDeferred (more reliable)
            val deferred = CompletableDeferred<String?>()
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    deferred.complete(task.result)
                } else {
                    deferred.complete(null)
                }
            }
            val freshToken: String? = deferred.await()

            if (freshToken.isNullOrBlank()) {
                Log.w(TAG, "FCM token fetch failed, will retry on next run")
                return Result.retry()
            }

            // Save token locally
            prefs.edit().putString("fcm_token", freshToken).apply()
            Log.d(TAG, "FCM token verified/saved: ${freshToken.take(20)}...")

            // Sync to server with retry
            val retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val apiService = retrofit.create(ApiService::class.java)

            if (isCustomer && !imei.isNullOrBlank()) {
                // Retry sync up to 3 times
                var synced = false
                for (attempt in 1..3) {
                    try {
                        val res = apiService.updateFcmToken(mapOf("imei" to imei, "fcmToken" to freshToken))
                        if (res.isSuccessful) {
                            prefs.edit().putBoolean("fcm_verified", true).apply()
                            Log.d(TAG, "✅ FCM token synced to server for IMEI: $imei (attempt $attempt)")
                            synced = true
                            break
                        } else {
                            Log.w(TAG, "Sync attempt $attempt failed: HTTP ${res.code()}")
                            if (attempt < 3) kotlinx.coroutines.delay(5000L * attempt)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Sync attempt $attempt exception: ${e.message}")
                        if (attempt < 3) kotlinx.coroutines.delay(5000L * attempt)
                    }
                }
                
                if (!synced) {
                    Log.e(TAG, "❌ Failed to sync after 3 attempts, will retry on next run")
                    return Result.retry()
                }
            } else if (!isCustomer && isUserLoggedIn && authToken != null) {
                apiService.updateShopkeeperFcmToken("Bearer $authToken", mapOf("fcmToken" to freshToken))
                Log.d(TAG, "Shopkeeper FCM token synced")
            }

            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "FCM token worker error: ${e.message}")
            return Result.retry()
        }
    }
}
