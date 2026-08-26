package com.pksafe.lock.manager.receiver

import android.Manifest
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log

class AdminReceiver : DeviceAdminReceiver() {

    companion object {
        /**
         * Broadcast action to apply the full Device Owner protection including
         * DISALLOW_DEBUGGING_FEATURES. Sent by the shopkeeper-side wireless ADB
         * provisioning flow as the final step, after all permissions are granted.
         */
        const val ACTION_APPLY_DEBUG_RESTRICTION =
            "com.pksafe.lock.manager.ACTION_APPLY_DEBUG_RESTRICTION"

        /**
         * Broadcast action sent by the shopkeeper-side provisioning flow to tell the
         * customer app that a wireless ADB provisioning session has started or ended.
         * Extra "active" (boolean): true while provisioning is in progress, false when
         * it is finished.
         */
        const val ACTION_SET_PROVISIONING_ACTIVE =
            "com.pksafe.lock.manager.ACTION_SET_PROVISIONING_ACTIVE"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d("ADMIN_RECEIVER", "PK Locker Admin Enabled — attempting IMEI fetch")
        // ADB device owner: fetch IMEI and runtime permissions immediately on admin enable.
        // Do NOT apply DISALLOW_DEBUGGING_FEATURES here — even if a shared pref marks
        // provisioning complete, this callback fires while an active wireless ADB connection
        // is still granting permissions. Applying the debugging restriction now would drop
        // the connection before the shopkeeper side finishes.
        // For QR provisioning the restriction is applied in onProfileProvisioningComplete();
        // for wireless ADB provisioning it is applied by the final broadcast.
        fetchAndSaveImei(context)
        applyDeviceOwnerProtection(context, includeDebuggingRestriction = false)
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        Log.d("ADMIN_RECEIVER", "Provisioning Complete — Device Owner Active")

        // Save provisioning completion with timestamp so the QR screen can detect success
        context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("provisioning_complete", true)
            .putBoolean("is_customer", true)
            .putString("provisioning_method", "qr")
            .putLong("provisioning_completed_at", System.currentTimeMillis())
            .apply()

        // Fetch IMEI first, then mark as customer
        fetchAndSaveImei(context)
        // Apply Device Owner restrictions immediately (QR provisioning doesn't use an
        // active ADB connection, so the debugging restriction can be applied safely).
        applyDeviceOwnerProtection(context, includeDebuggingRestriction = true)

        // Notify any listening UI that provisioning finished
        try {
            context.sendBroadcast(Intent("com.pksafe.lock.manager.PROVISIONING_COMPLETE"))
        } catch (e: Exception) {
            Log.w("ADMIN_RECEIVER", "Could not send provisioning complete broadcast: ${e.message}")
        }

        // Force start the app to finalize setup
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            launchIntent.putExtra("provisioning_mode", "qr")
            context.startActivity(launchIntent)
        } else {
            Log.e("ADMIN_RECEIVER", "No launch intent found — app may not be installed correctly")
        }
    }

    private fun applyDeviceOwnerProtection(context: Context, includeDebuggingRestriction: Boolean = true) {
        try {
            com.pksafe.lock.manager.util.LockManager(context)
                .applyDeviceOwnerProtection(includeDebuggingRestriction)
            Log.d(
                "ADMIN_RECEIVER",
                "Device Owner protection applied (debuggingRestriction=$includeDebuggingRestriction)"
            )
        } catch (e: Exception) {
            Log.e("ADMIN_RECEIVER", "Could not apply Device Owner protection: ${e.message}")
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_APPLY_DEBUG_RESTRICTION -> {
                Log.d("ADMIN_RECEIVER", "Received $ACTION_APPLY_DEBUG_RESTRICTION")
                applyDeviceOwnerProtection(context, includeDebuggingRestriction = true)
            }
            else -> super.onReceive(context, intent)
        }
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d("ADMIN_RECEIVER", "PK Locker Admin Disabled")
    }

    private fun fetchAndSaveImei(context: Context) {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val compName = ComponentName(context, AdminReceiver::class.java)

            if (dpm.isDeviceOwnerApp(context.packageName)) {
                val permissions = listOf(
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_SMS,
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.POST_NOTIFICATIONS
                )
                permissions.forEach { perm ->
                    try {
                        dpm.setPermissionGrantState(
                            compName,
                            context.packageName,
                            perm,
                            DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                        )
                    } catch (e: Exception) {
                        Log.w("ADMIN_RECEIVER", "Could not grant $perm: ${e.message}")
                    }
                }
                try {
                    dpm.setUninstallBlocked(compName, context.packageName, true)
                } catch (e: Exception) {
                    Log.w("ADMIN_RECEIVER", "Uninstall block: ${e.message}")
                }
                Log.d("ADMIN_RECEIVER", "Device Owner permissions granted")
            }

            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            // Try dual SIM slots + serial fallback
            val imei = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    val slot0 = try { tm.getImei(0) } catch (e: Exception) { null }
                    val slot1 = try { tm.getImei(1) } catch (e: Exception) { null }
                    slot0 ?: slot1 ?: try { tm.imei } catch (e: Exception) { null }
                }
                else -> try { tm.deviceId } catch (e: Exception) { null }
            }

            val imei2 = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try { tm.getImei(1) } catch (e: Exception) { null }
            } else null

            if (!imei.isNullOrBlank()) {
                Log.d("ADMIN_RECEIVER", "IMEI auto-fetched: $imei")
                context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("provisioning_complete", true)
                    .putBoolean("is_customer", true)
                    .putString("device_imei", imei)
                    .apply {
                        if (!imei2.isNullOrBlank()) putString("device_imei2", imei2)
                    }
                    .apply()
                Log.d("ADMIN_RECEIVER", "IMEI saved to prefs — customer mode activated")
            } else {
                Log.w("ADMIN_RECEIVER", "IMEI fetch returned null — manual entry will be needed")
                // Still mark provisioning complete so app launches normally
                context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("provisioning_complete", true)
                    .putBoolean("is_customer", true)
                    .apply()
            }
        } catch (e: Exception) {
            Log.e("ADMIN_RECEIVER", "Error fetching IMEI: ${e.message}")
        }
    }
}
