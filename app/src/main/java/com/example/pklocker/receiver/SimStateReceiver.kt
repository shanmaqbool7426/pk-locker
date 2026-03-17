package com.example.pklocker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.example.pklocker.util.LockManager

class SimStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.intent.action.SIM_STATE_CHANGED") {
            val state = intent.getStringExtra("ss")
            Log.d("SIM_RECEIVER", "SIM State Changed: $state")

            val sharedPrefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
            val isCustomer = sharedPrefs.getBoolean("is_customer", false)

            // Only trigger for customers
            if (!isCustomer) return

            when (state) {
                "ABSENT", "REMOVED" -> {
                    Log.w("SIM_RECEIVER", "SIM REMOVED! Locking device...")
                    
                    // Update local state
                    sharedPrefs.edit().putBoolean("is_locked", true).apply()
                    
                    // Trigger Professional Lock
                    val lockManager = LockManager(context)
                    lockManager.lockDevice()
                }
                "LOADED", "READY" -> {
                    Log.d("SIM_RECEIVER", "SIM is present and ready.")
                }
            }
        }
    }
}
