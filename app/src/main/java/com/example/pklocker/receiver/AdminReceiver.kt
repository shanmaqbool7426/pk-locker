package com.example.pklocker.receiver

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import android.util.Log

class AdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d("ADMIN_RECEIVER", "PK Locker Admin Enabled")
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        // For Device Owner, calling setProfileEnabled() crashes the app with SecurityException
        // because it's strictly for Work Profiles. Device Owners are already authorized.
        
        try {
            // manager.setProfileEnabled(componentName) <-- THIS CAUSED THE CRASH
            
            // Launch the app automatically after enterprise setup wizard finishes!
            val launchIntent = Intent(context, com.example.pklocker.MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(launchIntent)
        } catch (e: Exception) {
            Log.e("ADMIN_RECEIVER", "Failed to finalize DO setup: ${e.message}")
        }
        
        Log.d("ADMIN_RECEIVER", "Provisioning Complete — Device Owner Active")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d("ADMIN_RECEIVER", "PK Locker Admin Disabled")
    }
}
