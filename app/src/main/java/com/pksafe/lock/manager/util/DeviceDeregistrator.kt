package com.pksafe.lock.manager.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.pksafe.lock.manager.service.LockService

/**
 * Shared deregister logic used by BOTH FCM and SMS paths.
 *
 * When called, this:
 *  1. Clears all customer SharedPrefs
 *  2. Stops LockService + cancels notifications
 *  3. Removes all DPM restrictions
 *  4. Submits a silent self-uninstall (while still Device Owner)
 *  5. Removes Device Admin + Device Owner as cleanup fallback
 *
 * Can be called from any Context (Service, BroadcastReceiver, Activity).
 */
object DeviceDeregistrator {

    private const val TAG = "PKL_DEREGISTER"

    fun performFullDeregister(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)

        // ── STEP 1: Clear all prefs (AntiUninstallService stops instantly) ─────
        prefs.edit()
            .putBoolean("is_locked", false)
            .putBoolean("settings_blocked", false)
            .putBoolean("auto_lock_enabled", false)
            .putBoolean("is_customer", false)
            .putStringSet("blocked_apps", emptySet())
            .commit()

        // ── STEP 2: Stop services & notifications ─────────────────────────────
        try {
            appContext.stopService(Intent(appContext, LockService::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "Could not stop LockService: ${e.message}")
        }

        try {
            val notifManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notifManager.cancel(1001)
        } catch (e: Exception) {
            Log.w(TAG, "Could not cancel notification: ${e.message}")
        }

        // ── STEP 3–5 on main thread (DPM operations require it) ───────────────
        Handler(Looper.getMainLooper()).post {
            try {
                val lockManager = LockManager(appContext)

                // Clear all DPM restrictions
                lockManager.setCameraDisabled(false)
                lockManager.setUsbDataDisabled(false)
                lockManager.setAppInstallDisabled(false)
                lockManager.setAppUninstallDisabled(false)
                lockManager.setOutgoingCallsDisabled(false)
                lockManager.setFactoryResetDisabled(false)
                lockManager.setSafeBootDisabled(false)
                lockManager.toggleWarningAlarm(false)

                // Unhide all blocked apps
                listOf("whatsapp", "facebook", "instagram", "youtube", "chrome", "telegram", "hotstar").forEach { appKey ->
                    lockManager.setAppHidden(appKey, false)
                }

                // ── STEP 4: Submit silent self-uninstall WHILE still Device Owner ──
                triggerSilentUninstall(appContext)

                // ── STEP 5: Remove Device Admin + Owner (fallback cleanup) ─────────
                lockManager.selfDeactivate()

                Log.d(TAG, "Full deregister complete — uninstall submitted + admin removed")
            } catch (e: Exception) {
                Log.e(TAG, "Deregister error: ${e.message}")
            }
        }
    }

    /**
     * Uses PackageInstaller.uninstall() — silent when app is Device Owner.
     * Falls back to system uninstall page if silent mode fails.
     */
    private fun triggerSilentUninstall(context: Context) {
        try {
            Log.d(TAG, "Initiating silent self-uninstall...")

            // Disable accessibility guard so it doesn't interfere
            try {
                android.provider.Settings.Secure.putString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    ""
                )
                android.provider.Settings.Secure.putInt(
                    context.contentResolver,
                    android.provider.Settings.Secure.ACCESSIBILITY_ENABLED,
                    0
                )
                Log.d(TAG, "Accessibility guard disabled")
            } catch (e: Exception) {
                Log.w(TAG, "Could not disable accessibility: ${e.message}")
            }

            // Create PendingIntent for uninstall result callback
            val intent = Intent("com.pksafe.lock.manager.SELF_UNINSTALL").apply {
                setPackage(context.packageName)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            // PackageInstaller.uninstall() is silent when app is Device Owner
            val installer = context.packageManager.packageInstaller
            installer.uninstall(context.packageName, pendingIntent.intentSender)

            Log.d(TAG, "Silent uninstall submitted for ${context.packageName}")
        } catch (e: Exception) {
            Log.e(TAG, "Silent uninstall failed: ${e.message}")
            // Fallback: open system uninstall page so customer can do it manually
            try {
                val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(uninstallIntent)
            } catch (ex: Exception) {
                Log.e(TAG, "Fallback uninstall also failed: ${ex.message}")
            }
        }
    }
}
