package com.example.pklocker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.pklocker.MainActivity
import com.example.pklocker.R
import com.example.pklocker.util.LockManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d("FCM_LOG", "Signal Received: ${remoteMessage.data}")

        val data = remoteMessage.data
        var command = data["command"] // e.g., "lock", "unlock", "hardware_block", "app_block"
        val state = data["state"] == "true" || data["state"] == "1"
        val target = data["target"] // e.g., "usb", "camera"

        // Backward Compatibility Fix: Agar server sirf 'state' bhej raha hai (purana logic)
        if (command == null && data.containsKey("state")) {
            command = "lock_toggle"
        }

        val prefs = getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val lockManager = LockManager(applicationContext)

        when (command) {
            "lock", "state_change", "lock_toggle" -> {
                // Agar purana lock_toggle hai to state hi targetState hai
                val targetState = if (command == "lock") true else state
                
                prefs.edit().putBoolean("is_locked", targetState).commit()
                
                if (targetState) {
                    wakeUpScreen()
                    startLockServiceDirectly()
                    triggerFullScreenLock()
                    Handler(Looper.getMainLooper()).postDelayed({
                        lockManager.lockDevice()
                    }, 1500)
                } else {
                    // Unlock logic
                    applicationContext.stopService(Intent(applicationContext, LockService::class.java))
                    lockManager.unlockDevice() // Clear restrictions
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.cancel(1001)
                }
            }
            "hardware_block" -> {
                when (target) {
                    "usb" -> lockManager.setUsbDataDisabled(state)
                    "camera" -> lockManager.setCameraDisabled(state)
                    "settings" -> {
                        // Save to prefs so AntiUninstallService can enforce it
                        prefs.edit().putBoolean("settings_blocked", state).commit()
                        Log.d("FCM_LOG", "Settings blocked: $state")
                    }
                    "auto_lock" -> {
                        prefs.edit().putBoolean("auto_lock_enabled", state).commit()
                    }
                    "alarm" -> lockManager.toggleWarningAlarm(state)
                    else -> Log.w("FCM_LOG", "Unknown hardware_block target: $target")
                }
            }
            "config_change" -> {
                when (target) {
                    "wallpaper" -> {
                        val url = data["url"] ?: data["state"] // state might contain url in some payloads
                        lockManager.setWarningWallpaper(url)
                    }
                }
            }
            "app_block" -> {
                val appKey = target ?: return
                
                // Strategy 1: Device Owner → setApplicationHidden (BEST - OS level block)
                val hiddenByDPM = lockManager.setAppHidden(appKey, state)
                
                // Strategy 2: Fallback → SharedPrefs + Accessibility Service blocking
                // Always save to prefs as backup (Accessibility will use this)
                val blockedApps = prefs.getStringSet("blocked_apps", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                if (state) blockedApps.add(appKey)
                else blockedApps.remove(appKey)
                prefs.edit().putStringSet("blocked_apps", blockedApps).commit()
                
                Log.d("FCM_LOG", "App block [$appKey] state=$state, DPM_hidden=$hiddenByDPM, fallback_saved=true")
            }
            "request_data" -> {
                when (target) {
                    "location" -> {
                        // Location update logic (already in your app's background service likely)
                        // Trigger a one-time sync if needed
                    }
                    "phone_info" -> {
                        // Send back IMEI/Phone info to server
                    }
                }
            }
        }
    }

    // ─── Direct LockService start ─────────────────────────────────────────────
    // App ke background/killed hone par bhi ye kaam karega
    private fun startLockServiceDirectly() {
        try {
            val serviceIntent = Intent(applicationContext, LockService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(serviceIntent)
            } else {
                applicationContext.startService(serviceIntent)
            }
            Log.d("FCM_LOG", "LockService started directly from FCM")
        } catch (e: Exception) {
            Log.e("FCM_LOG", "LockService start failed: ${e.message}")
        }
    }

    private fun triggerFullScreenLock() {
        val channelId = "critical_lock_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Security Alert", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Critical security notifications"
                setBypassDnd(true)
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }

        val lockIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            putExtra("FORCE_LOCK", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, lockIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("DEVICE LOCKED")
            .setContentText("Security Protocol Active. Please pay EMI.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        manager.notify(1001, notification)

        // Direct attempt bhi karo
        try {
            startActivity(lockIntent)
        } catch (e: Exception) {
            Log.e("FCM_LOG", "Direct activity start failed: ${e.message}")
        }
    }

    private fun wakeUpScreen() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
                "PKLocker:WakeUp"
            )
            wakeLock.acquire(10000) // 10 seconds — overlay load hone ke liye kaafi
            Log.d("FCM_LOG", "Screen woken up")
        } catch (e: Exception) {
            Log.e("FCM_LOG", "WakeLock error: ${e.message}")
        }
    }
}
