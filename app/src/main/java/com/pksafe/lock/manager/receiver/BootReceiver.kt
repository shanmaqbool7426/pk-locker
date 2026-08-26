package com.pksafe.lock.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.pksafe.lock.manager.service.LockService
import com.pksafe.lock.manager.util.LockManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val lockManager = LockManager(context)

            // Re-apply Device Owner protection after reboot
            // (uninstall block, factory reset block, safe boot block, USB debug block)
            if (lockManager.isDeviceOwner()) {
                val prefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
                val isCustomer = prefs.getBoolean("is_customer", false)
                val provisioningComplete = prefs.getBoolean("provisioning_complete", false)
                val provisioningActive = prefs.getBoolean("wireless_provisioning_active", false)
                lockManager.applyDeviceOwnerProtection(
                    includeDebuggingRestriction = isCustomer && provisioningComplete && !provisioningActive
                )
                lockManager.ensureAccessibilityServiceEnabled()
            }

            // Yahan hum check karenge ke kya device locked state mein tha?
            // Abhi ke liye hum hamesha start kar dete hain agar Admin active ho
            if (lockManager.isAdminActive() && lockManager.canDrawOverlays()) {
                val serviceIntent = Intent(context, LockService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
