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

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d("ADMIN_RECEIVER", "PK Locker Admin Enabled — attempting IMEI fetch")
        // ADB device owner: fetch IMEI immediately on admin enable
        fetchAndSaveImei(context)
        // Apply Device Owner restrictions immediately (uninstall block, factory reset block, etc.)
        applyDeviceOwnerProtection(context)
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
        // Apply Device Owner restrictions immediately
        applyDeviceOwnerProtection(context)

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

    private fun applyDeviceOwnerProtection(context: Context) {
        try {
            com.pksafe.lock.manager.util.LockManager(context).applyDeviceOwnerProtection()
            Log.d("ADMIN_RECEIVER", "Device Owner protection applied")
        } catch (e: Exception) {
            Log.e("ADMIN_RECEIVER", "Could not apply Device Owner protection: ${e.message}")
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
