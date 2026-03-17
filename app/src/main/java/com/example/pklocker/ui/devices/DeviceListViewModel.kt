package com.example.pklocker.ui.devices

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pklocker.data.AdvancedControlRequest
import com.example.pklocker.data.ApiService
import com.example.pklocker.data.DeviceControls
import com.example.pklocker.data.DeviceResponse
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class DeviceListViewModel : ViewModel() {

    var devices by mutableStateOf<List<DeviceResponse>>(emptyList())
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    private val BASE_URL = com.example.pklocker.util.Constants.BASE_URL 

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val apiService = retrofit.create(ApiService::class.java)

    fun fetchDevices(context: Context) {
        val sharedPrefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val token = sharedPrefs.getString("auth_token", "") ?: ""

        if (token.isEmpty()) {
            errorMessage = "Authentication required"
            return
        }

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val response = apiService.getAllDevices("Bearer $token")
                
                if (response.isSuccessful && response.body() != null) {
                    val responseData = response.body()!!
                    devices = responseData.data
                    Log.d("DEVICE_LIST", "Fetched ${devices.size} devices from server")
                } else {
                    errorMessage = "Server Error: ${response.code()}"
                    devices = emptyList()
                }
            } catch (e: Exception) {
                Log.e("API_ERROR", "Failed: ${e.message}")
                errorMessage = "Connection Failed"
                devices = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    fun toggleLock(context: Context, imei: String, targetLockState: Boolean) {
        val sharedPrefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val token = sharedPrefs.getString("auth_token", "") ?: ""
        
        if (token.isEmpty()) return

        viewModelScope.launch {
            isLoading = true
            try {
                val response = if (targetLockState) {
                    apiService.lockDevice("Bearer $token", imei)
                } else {
                    apiService.unlockDevice("Bearer $token", imei)
                }

                if (response.isSuccessful) {
                    // Fetch fresh list only after successful server update
                    fetchDevices(context)
                } else {
                    Log.e("LOCK_ERROR", "Action failed: ${response.message()}")
                }
            } catch (e: Exception) {
                fetchDevices(context)
                Log.e("LOCK_EXCEPTION", "Error: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    fun sendControl(context: Context, imei: String, action: String, state: Any) {
        val sharedPrefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val token = sharedPrefs.getString("auth_token", "") ?: ""

        if (token.isEmpty()) return

        viewModelScope.launch {
            try {
                val response = apiService.sendAdvancedControl("Bearer $token", imei, AdvancedControlRequest(action, state))
                
                if (response.isSuccessful) {
                    Log.d("CONTROL_SUCCESS", "Action $action changed to $state")
                    // Fetch accurate DB state after control command
                    fetchDevices(context)
                } else {
                    Log.e("CONTROL_ERROR", "Action failed: ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e("CONTROL_ERROR", "Command failed: ${e.message}")
                fetchDevices(context) // Rollback
            }
        }
    }

    fun unlockAllControls(context: Context, imei: String) {
        val sharedPrefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val token = sharedPrefs.getString("auth_token", "") ?: ""

        if (token.isEmpty()) return

        viewModelScope.launch {
            isLoading = true
            try {
                val response = apiService.unlockAllControls("Bearer $token", imei)
                if (response.isSuccessful) {
                    Log.d("UNLOCK_ALL", "All controls cleared for IMEI: $imei")
                    fetchDevices(context)
                } else {
                    Log.e("UNLOCK_ALL_ERROR", "Failed: ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e("UNLOCK_ALL_ERROR", "Exception: ${e.message}")
                fetchDevices(context)
            } finally {
                isLoading = false
            }
        }
    }

    fun deregisterDevice(context: Context, imei: String, onSuccess: () -> Unit) {
        val sharedPrefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val token = sharedPrefs.getString("auth_token", "") ?: ""

        if (token.isEmpty()) return

        viewModelScope.launch {
            isLoading = true
            try {
                val response = apiService.deregisterDevice("Bearer $token", imei)
                if (response.isSuccessful) {
                    Log.d("DEREGISTER", "Device released: $imei")
                    onSuccess()
                } else {
                    Log.e("DEREGISTER_ERROR", "Failed: ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e("DEREGISTER_ERROR", "Exception: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }
}
