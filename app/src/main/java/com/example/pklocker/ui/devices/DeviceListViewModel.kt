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
            try {
                // Optimistic UI update
                devices = devices.map { 
                    if (it.imei == imei) {
                        it.copy(status = if (targetLockState) "Locked" else "Unlocked")
                    } else it
                }

                val response = if (targetLockState) {
                    apiService.lockDevice("Bearer $token", imei)
                } else {
                    apiService.unlockDevice("Bearer $token", imei)
                }

                if (!response.isSuccessful) {
                    // Rollback if failed
                    fetchDevices(context)
                    Log.e("LOCK_ERROR", "Action failed: ${response.message()}")
                }
            } catch (e: Exception) {
                fetchDevices(context)
                Log.e("LOCK_EXCEPTION", "Error: ${e.message}")
            }
        }
    }

    fun sendControl(context: Context, imei: String, action: String, state: Any) {
        val sharedPrefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val token = sharedPrefs.getString("auth_token", "") ?: ""

        if (token.isEmpty()) return

        viewModelScope.launch {
            try {
                // Optimistic UI update
                devices = devices.map { device ->
                    if (device.imei == imei) {
                        val controls = device.controls ?: DeviceControls()
                        when (action) {
                            "usbLock" -> device.copy(controls = controls.copy(usbLock = state as Boolean))
                            "cameraDisabled" -> device.copy(controls = controls.copy(cameraDisabled = state as Boolean))
                            "settingsBlocked" -> device.copy(controls = controls.copy(settingsBlocked = state as Boolean))
                            "autoLock" -> device.copy(controls = controls.copy(autoLock = state as Boolean))
                            "installBlocked" -> device.copy(controls = controls.copy(installBlocked = state as Boolean))
                            "uninstallBlocked" -> device.copy(controls = controls.copy(uninstallBlocked = state as Boolean))
                            "softResetBlocked" -> device.copy(controls = controls.copy(softResetBlocked = state as Boolean))
                            "softBootBlocked" -> device.copy(controls = controls.copy(softBootBlocked = state as Boolean))
                            "outgoingCallsBlocked" -> device.copy(controls = controls.copy(outgoingCallsBlocked = state as Boolean))
                            "warningAudio" -> device.copy(controls = controls.copy(warningAudio = state as Boolean))
                            "warningWallpaper" -> device.copy(controls = controls.copy(warningWallpaper = state as String))
                            
                            // App Restrictions
                            "instagram" -> device.copy(appRestrictions = device.appRestrictions?.copy(instagram = state as Boolean))
                            "whatsapp" -> device.copy(appRestrictions = device.appRestrictions?.copy(whatsapp = state as Boolean))
                            "facebook" -> device.copy(appRestrictions = device.appRestrictions?.copy(facebook = state as Boolean))
                            "youtube" -> device.copy(appRestrictions = device.appRestrictions?.copy(youtube = state as Boolean))
                            
                            else -> device
                        }
                    } else device
                }

                val response = apiService.sendAdvancedControl("Bearer $token", imei, AdvancedControlRequest(action, state))
                
                if (!response.isSuccessful) {
                    Log.e("CONTROL_ERROR", "Action failed: ${response.message()}")
                    fetchDevices(context) // Rollback
                }
            } catch (e: Exception) {
                Log.e("CONTROL_ERROR", "Command failed: ${e.message}")
                fetchDevices(context) // Rollback
            }
        }
    }
}
