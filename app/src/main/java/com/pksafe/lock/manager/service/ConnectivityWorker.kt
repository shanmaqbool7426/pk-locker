package com.pksafe.lock.manager.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker
import com.pksafe.lock.manager.data.AdvancedControlRequest
import com.pksafe.lock.manager.data.ApiService
import com.pksafe.lock.manager.util.LockManager
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ConnectivityWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): ListenableWorker.Result {
        val sharedPrefs = applicationContext.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val isCustomer = sharedPrefs.getBoolean("is_customer", false)
        val imei = sharedPrefs.getString("device_imei", "") ?: "" // SharedPrefs se actual IMEI

        if (!isCustomer) return ListenableWorker.Result.success()
        if (imei.isBlank()) return ListenableWorker.Result.success() // IMEI nahi hai, skip karo

        val lastSyncTime = sharedPrefs.getLong("last_online_sync", System.currentTimeMillis())
        val currentTime = System.currentTimeMillis()
        
        // 24 Hours in Milliseconds
        val offlineLimit = TimeUnit.HOURS.toMillis(24)

        if (currentTime - lastSyncTime > offlineLimit) {
            Log.w("OFFLINE_GUARD", "Device offline for too long! Locking...")
            
            // 1. Lock Locally
            sharedPrefs.edit().putBoolean("is_locked", true).apply()
            val lockManager = LockManager(applicationContext)
            lockManager.lockDevice()

            // 2. Try to notify server (if a tiny bit of internet is available)
            reportStatusToServer(imei, "OFFLINE_LOCKED")
        } else {
            // Heartbeat: If online, tell server device is active
            reportStatusToServer(imei, "ONLINE_ACTIVE")
        }

        return ListenableWorker.Result.success()
    }

    private suspend fun reportStatusToServer(imei: String, status: String) {
        try {
            val sharedPrefs = applicationContext.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
            val token = sharedPrefs.getString("auth_token", "") ?: ""
            
            val retrofit = Retrofit.Builder()
                .baseUrl(com.pksafe.lock.manager.util.Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val api = retrofit.create(ApiService::class.java)
            
            // FIX: Added the missing Bearer Token as the first argument
            api.sendAdvancedControl("Bearer $token", imei, AdvancedControlRequest("STATUS_UPDATE", true))
            
            // Update last sync time locally if successful
            sharedPrefs.edit().putLong("last_online_sync", System.currentTimeMillis()).apply()
            
            Log.d("OFFLINE_GUARD", "Status reported to server: $status")
        } catch (e: Exception) {
            Log.e("OFFLINE_GUARD", "Failed to report status: ${e.message}")
        }
    }
}
