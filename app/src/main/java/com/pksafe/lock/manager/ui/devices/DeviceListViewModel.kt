package com.pksafe.lock.manager.ui.devices

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pksafe.lock.manager.data.AdvancedControlRequest
import com.pksafe.lock.manager.data.ApiService
import com.pksafe.lock.manager.data.DeviceControls
import com.pksafe.lock.manager.data.DeviceResponse
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class DeviceListViewModel : ViewModel() {

    var devices by mutableStateOf<List<DeviceResponse>>(emptyList())
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    private val BASE_URL = com.pksafe.lock.manager.util.Constants.BASE_URL 

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

    // --- EMI SCHEDULE MANAGEMENT ---
    var selectedEmiSchedule by mutableStateOf<com.pksafe.lock.manager.data.EmiScheduleData?>(null)
    var isFetchingEmi by mutableStateOf(false)

    fun fetchEmiSchedule(context: Context, imei: String) {
        val sharedPrefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val token = sharedPrefs.getString("auth_token", "") ?: ""
        if (token.isEmpty()) return

        viewModelScope.launch {
            isFetchingEmi = true
            try {
                val response = apiService.getDeviceEmiSchedule("Bearer $token", imei)
                if (response.isSuccessful && response.body()?.success == true) {
                    selectedEmiSchedule = response.body()?.data
                } else {
                    errorMessage = "Failed to load EMI schedule"
                }
            } catch (e: Exception) {
                Log.e("EMI_FETCH_ERROR", "Error: ${e.message}")
                errorMessage = "Connection error while fetching EMIs"
            } finally {
                isFetchingEmi = false
            }
        }
    }

    fun markEmiAsPaid(context: Context, emiId: String, imei: String) {
        val sharedPrefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val token = sharedPrefs.getString("auth_token", "") ?: ""
        if (token.isEmpty()) return

        viewModelScope.launch {
            isFetchingEmi = true // show loader in bottom sheet
            try {
                val response = apiService.markEmiAsPaid("Bearer $token", emiId)
                if (response.isSuccessful && response.body()?.success == true) {
                    // Refresh EMI schedule
                    fetchEmiSchedule(context, imei)
                    // Also refresh the main list softly so total prices/etc update if needed
                    fetchDevices(context)
                } else {
                    errorMessage = "Failed to mark as paid: ${response.body()?.message}"
                }
            } catch (e: Exception) {
                Log.e("EMI_PAY_ERROR", "Error: ${e.message}")
                errorMessage = "Connection error"
            } finally {
                isFetchingEmi = false
            }
        }
    }

    fun rescheduleEmiPlan(context: Context, imei: String, request: com.pksafe.lock.manager.data.RescheduleEmiRequest) {
        val sharedPrefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val token = sharedPrefs.getString("auth_token", "") ?: ""
        if (token.isEmpty()) return

        viewModelScope.launch {
            isFetchingEmi = true
            try {
                val response = apiService.rescheduleEmiPlan("Bearer $token", imei, request)
                if (response.isSuccessful && response.body()?.success == true) {
                    fetchEmiSchedule(context, imei)
                    fetchDevices(context)
                } else {
                    errorMessage = "Failed to restructure EMI: ${response.body()?.message}"
                }
            } catch (e: Exception) {
                Log.e("EMI_RESCHEDULE_ERROR", "Error: ${e.message}")
                errorMessage = "Connection error"
            } finally {
                isFetchingEmi = false
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
