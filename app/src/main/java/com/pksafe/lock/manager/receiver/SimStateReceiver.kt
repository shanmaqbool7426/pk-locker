package com.pksafe.lock.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.pksafe.lock.manager.util.LockManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.pksafe.lock.manager.data.ApiService
import com.pksafe.lock.manager.util.Constants
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
                    
                    var currentIccid = "Unknown"
                    var currentPhoneNumber = ""
                    
                    try {
                        // Strategy 1: SubscriptionManager — per-subscription ICCID + number.
                        // Dual-SIM aware: match the number to the SIM that actually changed.
                        // NOTE: sub.number is often empty (carriers rarely write MSISDN to
                        // SIM), so collect the best available and fall back below.
                        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                        val subs = subscriptionManager?.activeSubscriptionInfoList
                        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                        
                        var fallbackIccid = ""
                        var fallbackNumber = ""
                        if (!subs.isNullOrEmpty()) {
                            for (sub in subs) {
                                val subIccid = try { sub.iccId } catch (_: Exception) { null }
                                val subNumber = try {
                                    sub.number?.takeIf { it.isNotBlank() }
                                        ?: telephonyManager.createForSubscriptionId(sub.subscriptionId).line1Number?.takeIf { it.isNotBlank() }
                                } catch (_: Exception) { null }
                                
                                if (!subIccid.isNullOrEmpty() && fallbackIccid.isEmpty()) fallbackIccid = subIccid
                                if (!subNumber.isNullOrEmpty() && fallbackNumber.isEmpty()) fallbackNumber = subNumber
                                // Prefer the subscription that has BOTH iccid and a readable number
                                if (!subIccid.isNullOrEmpty() && !subNumber.isNullOrEmpty()) {
                                    currentIccid = subIccid
                                    currentPhoneNumber = subNumber
                                    break
                                }
                            }
                        }
                        
                        // Strategy 2: TelephonyManager (Serial + line1Number fallback)
                        if (currentIccid == "Unknown") {
                            currentIccid = fallbackIccid.ifEmpty { telephonyManager.simSerialNumber ?: "Unknown" }
                        }
                        if (currentPhoneNumber.isEmpty()) {
                            currentPhoneNumber = fallbackNumber.ifEmpty { telephonyManager.line1Number ?: "" }
                        }
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
