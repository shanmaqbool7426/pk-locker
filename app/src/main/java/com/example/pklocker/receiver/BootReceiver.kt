package com.example.pklocker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.pklocker.service.LockService
import com.example.pklocker.util.LockManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val lockManager = LockManager(context)
            
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
