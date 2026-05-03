package com.pksafe.lock.manager.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d("ADMIN_RECEIVER", "PK Locker Admin Enabled")
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        // Essential for finalization on some devices
        context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("provisioning_complete", true)
            .putBoolean("is_customer", true) // Auto-mark as customer after QR setup
            .apply()
            
        Log.d("ADMIN_RECEIVER", "Provisioning Complete — Device Owner Active")
        
        // Force start the app to finalize the setup wizard
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
}
