package com.example.pklocker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.example.pklocker.util.LockManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.example.pklocker.data.ApiService
import com.example.pklocker.util.Constants
import android.telephony.SubscriptionManager

class SimStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.intent.action.SIM_STATE_CHANGED") {
            val state = intent.getStringExtra("ss")
            Log.d("SIM_RECEIVER", "SIM State Changed: $state")

            val sharedPrefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
            val isCustomer = sharedPrefs.getBoolean("is_customer", false)
            val imei = sharedPrefs.getString("device_imei", null)

            // Only trigger for customers with recorded IMEI
            if (!isCustomer || imei == null) return

            when (state) {
                "ABSENT", "REMOVED" -> {
                    val autoLockOnSim = sharedPrefs.getBoolean("auto_lock_sim_change_enabled", false)
                    if (autoLockOnSim) {
                        Log.w("SIM_RECEIVER", "SIM REMOVED! Auto-Lock is ON. Locking device...")
                        
                        // Set specific SIM lock flag
                        sharedPrefs.edit()
                            .putBoolean("is_locked", true)
                            .putBoolean("is_locked_by_sim", true)
                            .apply()
                        
                        // Trigger Professional Lock
                        val lockManager = LockManager(context)
                        lockManager.lockDevice()
                    } else {
                        Log.i("SIM_RECEIVER", "SIM REMOVED! Auto-Lock is OFF. Only logging.")
                    }
                }
                "LOADED", "READY" -> {
                    Log.d("SIM_RECEIVER", "SIM is present and ready (State: $state). Checking for change...")
                    
                    val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                    var currentIccid = "Unknown"
                    var currentPhoneNumber = ""
                    
                    try {
                        // Strategy 1: TelephonyManager (Serial)
                        currentIccid = telephonyManager.simSerialNumber ?: "Unknown"
                        
                        // Strategy 2: SubscriptionManager (Better for modern Android)
                        if (currentIccid == "Unknown" || currentIccid.isEmpty()) {
                            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? android.telephony.SubscriptionManager
                            val activeSubscriptions = subscriptionManager?.activeSubscriptionInfoList
                            if (!activeSubscriptions.isNullOrEmpty()) {
                                currentIccid = activeSubscriptions[0].iccId ?: "Unknown"
                                Log.d("SIM_RECEIVER", "ICCID found via SubscriptionManager: $currentIccid")
                            }
                        }

                        currentPhoneNumber = telephonyManager.line1Number ?: ""
                    } catch (e: Exception) {
                        Log.w("SIM_RECEIVER", "Error reading SIM info: ${e.message}")
                    }

                    Log.d("SIM_RECEIVER", "Current ICCID: $currentIccid")

                    val lastIccid = sharedPrefs.getString("last_sim_iccid", "")
                    val isLockedBySim = sharedPrefs.getBoolean("is_locked_by_sim", false)
                    val autoLockOnSim = sharedPrefs.getBoolean("auto_lock_sim_change_enabled", false)

                    Log.d("SIM_RECEIVER", "Stored Info - LastICCID: $lastIccid, LockedBySim: $isLockedBySim, AutoLockEnabled: $autoLockOnSim")

                    // 1. Initial Setup or Re-insertion of Authorized SIM
                    if (currentIccid != "Unknown" && (lastIccid.isNullOrEmpty() || currentIccid == lastIccid)) {
                        Log.i("SIM_RECEIVER", "Authorized or First SIM detected: $currentIccid")
                        
                        if (lastIccid.isNullOrEmpty()) {
                            sharedPrefs.edit().putString("last_sim_iccid", currentIccid).apply()
                        }

                        if (isLockedBySim) {
                            Log.i("SIM_RECEIVER", "Unlocking device via SIM match...")
                            val lockManager = LockManager(context)
                            lockManager.unlockDevice()
                            sharedPrefs.edit().putBoolean("is_locked_by_sim", false).apply()
                        }
                        return
                    }

                    // 2. SIM Change Detected! (Only if current != last and last is NOT empty)
                    if (currentIccid != "Unknown" && !lastIccid.isNullOrEmpty() && currentIccid != lastIccid) {
                        Log.i("SIM_RECEIVER", "SIM Change Detected! ICCID: $currentIccid, Last: $lastIccid")
                        
                        // If Auto-Lock is ON, lock immediately
                        if (autoLockOnSim) {
                            Log.w("SIM_RECEIVER", "SIM Change + Auto-Lock ON! Locking device...")
                            sharedPrefs.edit()
                                .putBoolean("is_locked", true)
                                .putBoolean("is_locked_by_sim", true)
                                .apply()
                            val lockManager = LockManager(context)
                            lockManager.lockDevice()
                        }

                        // Notify backend
                        val retrofit = Retrofit.Builder()
                            .baseUrl(Constants.BASE_URL)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build()
                        val apiService = retrofit.create(ApiService::class.java)

                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val response = apiService.notifySimChanged(imei, mapOf(
                                    "iccid" to currentIccid,
                                    "phoneNumber" to currentPhoneNumber
                                ))
                                if (response.isSuccessful) {
                                    Log.d("SIM_RECEIVER", "Backend notified of SIM change")
                                    // Optionally update last_sim_iccid if we want to accept this new SIM
                                    // User said: "New number automatically save ho" -> usually means we accept it.
                                    sharedPrefs.edit().putString("last_sim_iccid", currentIccid).apply()
                                }
                            } catch (e: Exception) {
                                Log.e("SIM_RECEIVER", "Failed to notify backend", e)
                            }
                        }
                    }
                }
            }
        }
    }
}
