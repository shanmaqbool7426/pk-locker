package com.pksafe.lock.manager.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pksafe.lock.manager.data.ApiService
import com.pksafe.lock.manager.util.Constants
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class LocationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sharedPrefs = applicationContext.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val imei = sharedPrefs.getString("device_imei", null) ?: return Result.failure()
        
        Log.d("LOCATION_WORKER", "Starting location sync for IMEI: $imei")

        // 1. Check Permissions
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w("LOCATION_WORKER", "Permission not granted")
            return Result.retry()
        }

        try {
            // 2. Get Location
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
            val locationTask = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            val location = Tasks.await(locationTask)

            if (location != null) {
                Log.d("LOCATION_WORKER", "Location captured: ${location.latitude}, ${location.longitude}")
                
                // 3. Send to Backend
                val retrofit = Retrofit.Builder()
                    .baseUrl(Constants.BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                val apiService = retrofit.create(ApiService::class.java)

                val response = apiService.notifyLocation(imei, mapOf(
                    "lat" to location.latitude.toString(),
                    "lng" to location.longitude.toString()
                ))

                if (response.isSuccessful) {
                    Log.i("LOCATION_WORKER", "Location synced successfully")
                    return Result.success()
                } else {
                    Log.e("LOCATION_WORKER", "Backend sync failed: ${response.code()}")
                    return Result.retry()
                }
            } else {
                Log.w("LOCATION_WORKER", "Location is null")
                return Result.retry()
            }
        } catch (e: Exception) {
            Log.e("LOCATION_WORKER", "Error in LocationWorker", e)
            return Result.retry()
        }
    }
}
