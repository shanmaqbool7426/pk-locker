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
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        Log.d("ADMIN_RECEIVER", "Provisioning Complete — Device Owner Active")

        // Fetch IMEI first, then mark as customer
        fetchAndSaveImei(context)

        // Force start the app to finalize setup
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        launchIntent?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra("provisioning_mode", "qr")
        }
        context.startActivity(launchIntent)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d("ADMIN_RECEIVER", "PK Locker Admin Disabled")
    }

    private fun fetchAndSaveImei(context: Context) {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val compName = ComponentName(context, AdminReceiver::class.java)

            // Grant critical permissions to self as Device Owner
            if (dpm.isDeviceOwnerApp(context.packageName)) {
                val permissions = listOf(
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_SMS,
                    Manifest.permission.SEND_SMS
                )
                permissions.forEach { perm ->
                    dpm.setPermissionGrantState(compName, context.packageName, perm, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
                }
                Log.d("ADMIN_RECEIVER", "Critical permissions granted to self")
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
