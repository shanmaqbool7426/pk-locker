package com.pksafe.lock.manager.ui.dashboard

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pksafe.lock.manager.data.ApiService
import com.pksafe.lock.manager.data.DashboardData
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class DashboardViewModel : ViewModel() {

    var dashboardData by mutableStateOf<DashboardData?>(null)
    var shopName by mutableStateOf("Shopkeeper")
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    private val retrofit = Retrofit.Builder()
        .baseUrl(com.pksafe.lock.manager.util.Constants.BASE_URL) 
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val apiService = retrofit.create(ApiService::class.java)

    fun initDashboard(context: Context) {
        val sharedPrefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        shopName = sharedPrefs.getString("shop_name", "Shopkeeper") ?: "Shopkeeper"
        val token = sharedPrefs.getString("auth_token", "") ?: ""
        
        if (token.isNotEmpty()) {
            fetchStats("Bearer $token")
        } else {
            errorMessage = "Authentication required"
        }
    }

    private fun fetchStats(token: String) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val response = apiService.getStats(token)
                if (response.isSuccessful && response.body()?.success == true) {
                    dashboardData = response.body()?.data
                } else {
                    errorMessage = "Failed to fetch stats: ${response.code()}"
                }
            } catch (e: Exception) {
                Log.e("DASHBOARD_VM", "Error fetching stats", e)
                errorMessage = "Connection Failed"
            } finally {
                isLoading = false
            }
        }
    }
}
