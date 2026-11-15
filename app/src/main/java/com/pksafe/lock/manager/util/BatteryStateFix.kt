package com.pksafe.lock.manager.util

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.pksafe.lock.manager.receiver.AdminReceiver

/**
 * One-time recovery for phones provisioned by older builds, which ran
 * `dumpsys battery unplug` over ADB. That command makes the battery service report
 * "not charging" (no charging icon, frozen battery %) until the phone reboots, and
 * ADB is disabled at the end of provisioning so it could never be reset.
 *
 * A reboot clears it. Device Owner can reboot the phone itself, so we do it exactly
 * once, only while the screen is off and no provisioning session is running.
 * The flag is stored BEFORE rebooting, so this can never become a reboot loop.
 */
object BatteryStateFix {
    private const val TAG = "BATTERY_FIX"
    private const val PREFS = "PKLockerPrefs"
    const val FLAG = "battery_state_fix_v1_done"
    // dpm.reboot() throws while a call is active, so allow a few tries (never a loop).
    private const val ATTEMPTS = "battery_state_fix_v1_attempts"
    private const val MAX_ATTEMPTS = 3

    fun rebootOnceIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getBoolean(FLAG, false)) return
            val attempts = prefs.getInt(ATTEMPTS, 0)
            if (attempts >= MAX_ATTEMPTS) return
            if (!prefs.getBoolean("is_customer", false) || prefs.getBoolean("is_admin", false)) return
            if (prefs.getBoolean("wireless_provisioning_active", false)) return

            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (!dpm.isDeviceOwnerApp(context.packageName)) return

            // Wait for the screen to be off so the customer is never interrupted.
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (pm.isInteractive) return

            // Phone only just booted (< 10 min) => fake state is already gone; no reboot needed.
            if (android.os.SystemClock.elapsedRealtime() < 10 * 60 * 1000L) {
                prefs.edit().putBoolean(FLAG, true).commit()
                return
            }

            // Count the attempt FIRST (commit = synchronous). If the reboot succeeds the flag
            // is set; if it throws (e.g. call in progress) we retry later, max 3 times.
            prefs.edit().putInt(ATTEMPTS, attempts + 1).putBoolean(FLAG, true).commit()
            Log.w(TAG, "Rebooting once to clear legacy 'battery unplug' state")
            try {
                dpm.reboot(ComponentName(context, AdminReceiver::class.java))
            } catch (e: Exception) {
                // Reboot refused (e.g. call in progress): un-mark so a later run retries
                // (bounded by MAX_ATTEMPTS, so this can never loop).
                prefs.edit().putBoolean(FLAG, false).commit()
                Log.w(TAG, "Reboot refused: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Battery state fix skipped: ${e.message}")
        }
    }
}
