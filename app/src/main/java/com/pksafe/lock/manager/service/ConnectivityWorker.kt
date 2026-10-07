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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

        // ── Proactive FCM Sync: if not verified, try to sync now ────────────
        if (!prefs.getBoolean("fcm_verified", false)) {
            Log.d(TAG, "FCM not verified, attempting proactive sync...")
            try {
                // We use the fire-and-forget sync function from MainActivity
                // (need to make it accessible or re-implement here)
                val tokenTask = com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                if (tokenTask.isSuccessful) {
                    val token = tokenTask.result
                    if (!token.isNullOrBlank()) {
                        syncToken(imei, token, applicationContext)
                    }
                }
            } catch (e: Exception) { }
        }

        // ── Send heartbeat and get server state ────────────────────────────
        val serverState = sendHeartbeat(imei)

        if (serverState != null) {
            // ── RECONNECT SYNC — catch missed commands (Lock/Unlock) ────────
            val localLocked = prefs.getBoolean("is_locked", false)
            val serverLocked = serverState.status == "Locked"

            if (serverLocked != localLocked) {
                Log.w(TAG, "RECONNECT SYNC: Server status ($serverState.status) mismatch with Local! Syncing...")
                prefs.edit().putBoolean("is_locked", serverLocked).commit()
                
                Handler(Looper.getMainLooper()).post {
                    val lockManager = LockManager(applicationContext)
                    if (serverLocked) {
                        lockManager.lockDevice()
                        // Start lock overlay
                        try {
                            val serviceIntent = Intent(applicationContext, LockService::class.java)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                applicationContext.startForegroundService(serviceIntent)
                            } else {
                                applicationContext.startService(serviceIntent)
                            }
                        } catch (e: Exception) { }
                    } else {
                        lockManager.unlockDevice()
                        applicationContext.stopService(Intent(applicationContext, LockService::class.java))
                    }
                }
            }

            // ── RESTRICTIONS SYNC — catch missed hardware/app blocks ────────
            Handler(Looper.getMainLooper()).post {
                val lockManager = LockManager(applicationContext)
                serverState.controls?.let { c ->
                    lockManager.setUsbDataDisabled(c.usbLock)
                    lockManager.setCameraDisabled(c.cameraDisabled)
                    lockManager.setAppInstallDisabled(c.installBlocked)
                    lockManager.setAppUninstallDisabled(c.uninstallBlocked)
                    lockManager.setOutgoingCallsDisabled(c.outgoingCallsBlocked)
                    lockManager.setFactoryResetDisabled(c.softResetBlocked)
                    lockManager.setSafeBootDisabled(c.softBootBlocked)
                    prefs.edit().putBoolean("settings_blocked", c.settingsBlocked).apply()
                }
                serverState.appRestrictions?.let { a ->
                    // Unhide/Hide apps based on server state
                    val apps = mapOf(
                        "whatsapp" to a.whatsapp, "facebook" to a.facebook, 
                        "instagram" to a.instagram, "youtube" to a.youtube, 
                        "chrome" to a.chrome, "telegram" to a.telegram
                    )
                    apps.forEach { (key, blocked) -> lockManager.setAppHidden(key, blocked) }
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

    private suspend fun syncToken(imei: String, token: String, context: Context) {
        try {
            val retrofit = Retrofit.Builder()
                .baseUrl(com.pksafe.lock.manager.util.Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val api = retrofit.create(ApiService::class.java)
            val res = api.updateFcmToken(mapOf("imei" to imei, "fcmToken" to token))
            if (res.isSuccessful) {
                context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
                    .edit().putBoolean("fcm_verified", true).apply()
                Log.d(TAG, "Verified FCM token on server via worker")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token sync via worker failed: ${e.message}")
        }
    }
}
