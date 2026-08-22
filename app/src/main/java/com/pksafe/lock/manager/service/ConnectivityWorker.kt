package com.pksafe.lock.manager.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker
import com.pksafe.lock.manager.data.ApiService
import com.pksafe.lock.manager.util.LockManager
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ConnectivityWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "HEARTBEAT"
        // Heartbeat every 2 hours (was 24h — too infrequent to catch missed commands)
        val HEARTBEAT_INTERVAL: Long = TimeUnit.HOURS.toMillis(2)
    }

    override suspend fun doWork(): ListenableWorker.Result {
        val prefs = applicationContext.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val isCustomer = prefs.getBoolean("is_customer", false)
        val imei = prefs.getString("device_imei", "") ?: ""

        if (!isCustomer) return ListenableWorker.Result.success()
        if (imei.isBlank()) return ListenableWorker.Result.success()

        // Admin devices only send a basic heartbeat, never lock
        if (prefs.getBoolean("is_admin", false)) {
            sendHeartbeat(imei)
            return ListenableWorker.Result.success()
        }

        val now = System.currentTimeMillis()

        // ── Send heartbeat and get server state ────────────────────────────
        val serverState = sendHeartbeat(imei)

        if (serverState != null) {
            // ── FIX 2: RECONNECT SYNC — catch missed commands after offline ──
            // If server says device should be locked but device is unlocked, lock it
            val localLocked = prefs.getBoolean("is_locked", false)
            val serverLocked = serverState.status == "Locked"

            if (serverLocked && !localLocked) {
                Log.w(TAG, "RECONNECT SYNC: Server says Locked but device is Unlocked! Applying lock...")
                prefs.edit().putBoolean("is_locked", true).commit()
                Handler(Looper.getMainLooper()).post {
                    val lockManager = LockManager(applicationContext)
                    lockManager.lockDevice()
                    // Start lock overlay
                    try {
                        val serviceIntent = Intent(applicationContext, LockService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            applicationContext.startForegroundService(serviceIntent)
                        } else {
                            applicationContext.startService(serviceIntent)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start LockService: ${e.message}")
                    }
                }
            }

            // Check if device was deregistered while offline
            if (serverState.isDeregistered) {
                Log.w(TAG, "RECONNECT SYNC: Device was deregistered while offline!")
                com.pksafe.lock.manager.util.DeviceDeregistrator.performFullDeregister(applicationContext)
                return ListenableWorker.Result.success()
            }

            // Update both heartbeat timestamps on success
            prefs.edit()
                .putLong("last_heartbeat_time", now)
                .putLong("last_online_sync", now)
                .apply()
        }

        // ── Legacy: offline auto-lock after 24h without any heartbeat ─────
        val lastSyncTime = prefs.getLong("last_online_sync", now)
        val offlineLimit = TimeUnit.HOURS.toMillis(24)
        if (now - lastSyncTime > offlineLimit && serverState == null) {
            Log.w(TAG, "Device offline for 24h+ and heartbeat failed. Locking locally...")
            prefs.edit().putBoolean("is_locked", true).apply()
            val lockManager = LockManager(applicationContext)
            lockManager.lockDevice()
        }

        return ListenableWorker.Result.success()
    }

    /**
     * Sends heartbeat to server, returns parsed server state or null on failure.
     */
    private suspend fun sendHeartbeat(imei: String): com.pksafe.lock.manager.data.HeartbeatData? {
        return try {
            val retrofit = Retrofit.Builder()
                .baseUrl(com.pksafe.lock.manager.util.Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val api = retrofit.create(ApiService::class.java)

            val response = api.sendHeartbeat(imei)
            if (response.isSuccessful && response.body()?.success == true) {
                Log.d(TAG, "Heartbeat sent. Server state: ${response.body()?.data?.status}")
                response.body()?.data
            } else {
                Log.w(TAG, "Heartbeat response not successful: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Heartbeat failed: ${e.message}")
            null
        }
    }
}
