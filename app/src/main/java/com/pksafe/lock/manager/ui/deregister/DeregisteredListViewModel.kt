package com.pksafe.lock.manager.ui.deregister

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pksafe.lock.manager.data.ApiService
import com.pksafe.lock.manager.data.DeviceResponse
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class DeregisteredListViewModel : ViewModel() {

    var devices by mutableStateOf<List<DeviceResponse>>(emptyList())
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    private val BASE_URL = com.pksafe.lock.manager.util.Constants.BASE_URL 

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val apiService = retrofit.create(ApiService::class.java)

    fun fetchDeregisteredDevices(context: Context) {
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
                val response = apiService.getDeregisteredDevices("Bearer $token")
                if (response.isSuccessful && response.body() != null) {
                    devices = response.body()!!.data
                } else {
                    errorMessage = "Server Error: ${response.code()}"
                }
            } catch (e: Exception) {
                Log.e("Deregistered_API_ERROR", "Failed: ${e.message}")
                errorMessage = "Connection Failed"
            } finally {
                isLoading = false
            }
        }
    }
}
