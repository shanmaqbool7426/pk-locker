package com.pksafe.lock.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pksafe.lock.manager.util.LockManager

/**
 * Dedicated receiver for the final step of wireless ADB provisioning.
 *
 * Unlike [AdminReceiver], this receiver does **not** require
 * [android.permission.BIND_DEVICE_ADMIN], so the shopkeeper-side provisioning
 * flow can send an ADB `am broadcast` to it without being blocked by the
 * permission check that protects device-admin broadcasts.
 *
 * Receiving [AdminReceiver.ACTION_APPLY_DEBUG_RESTRICTION] applies
 * [android.os.UserManager.DISALLOW_DEBUGGING_FEATURES] through the Device Owner
 * API, which disables ADB and wireless debugging on the customer device. This is
 * intentionally done as the LAST provisioning step so the wireless ADB connection
 * stays alive until all permissions and settings are granted.
 *
 * This receiver also handles [AdminReceiver.ACTION_SET_PROVISIONING_ACTIVE] so the
 * customer app knows when a wireless provisioning session is in progress and can
 * avoid re-applying the debugging restriction mid-setup (e.g. when the app process
 * restarts after becoming Device Owner).
 */
class DebugRestrictionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AdminReceiver.ACTION_APPLY_DEBUG_RESTRICTION -> applyDebugRestriction(context)
            AdminReceiver.ACTION_SET_PROVISIONING_ACTIVE -> setProvisioningActive(context, intent)
            else -> return
        }
    }

    private fun applyDebugRestriction(context: Context) {
        Log.d("DEBUG_RESTRICTION", "Applying DISALLOW_DEBUGGING_FEATURES after provisioning")
        try {
            LockManager(context).applyDeviceOwnerProtection(includeDebuggingRestriction = true)
        } catch (e: Exception) {
            Log.e("DEBUG_RESTRICTION", "Failed to apply debugging restriction: ${e.message}")
        } finally {
            // Provisioning is finished once this restriction is applied. Clear the
            // in-progress flag so the app will re-apply protection on future starts.
            context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("wireless_provisioning_active", false)
                .apply()
        }
    }

    private fun setProvisioningActive(context: Context, intent: Intent) {
        val active = intent.getBooleanExtra("active", false)
        context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("wireless_provisioning_active", active)
            .apply()
        Log.d("DEBUG_RESTRICTION", "Wireless provisioning active=$active")
    }
}
