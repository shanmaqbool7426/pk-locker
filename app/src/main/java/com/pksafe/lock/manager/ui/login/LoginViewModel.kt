package com.pksafe.lock.manager.ui.login

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pksafe.lock.manager.data.ApiService
import com.pksafe.lock.manager.data.LoginRequest
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class LoginViewModel : ViewModel() {
    var phone by mutableStateOf("")
    var password by mutableStateOf("")
    
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var isLoggedIn by mutableStateOf(false)

    private val retrofit = Retrofit.Builder()
        .baseUrl(com.pksafe.lock.manager.util.Constants.BASE_URL) 
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val apiService = retrofit.create(ApiService::class.java)

    fun onLoginClick(context: Context) {
        if (phone.isBlank() || password.isBlank()) {
            errorMessage = "Please enter both phone and password"
            return
        }

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val response = apiService.loginShopkeeper(LoginRequest(phone, password))
                
                if (response.isSuccessful && response.body()?.success == true) {
                    val loginData = response.body()
                    isLoggedIn = true
                    
                    // Save Admin status and Shop info from the nested shopkeeper object
                    val sharedPrefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
                    sharedPrefs.edit().apply {
                        putBoolean("is_admin", loginData?.shopkeeper?.role == "admin")
                        putBoolean("is_logged_in", true)
                        putBoolean("is_customer", false) // Reset customer status for admin device
                        putBoolean("is_locked", false)   // NEVER lock an admin device
                        putBoolean("settings_blocked", false)
                        putBoolean("auto_lock_enabled", false)
                        putString("shop_name", loginData?.shopkeeper?.shopName ?: "Shopkeeper")
                        putString("shop_phone", loginData?.shopkeeper?.phone ?: "")
                        putString("auth_token", loginData?.token)
                        apply()
                    }

                    // Update Shopkeeper's FCM Token for notifications
                    com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val fcmToken = task.result
                            val authToken = loginData?.token
                            if (fcmToken != null && authToken != null) {
                                viewModelScope.launch {
                                    try {
                                        apiService.updateShopkeeperFcmToken("Bearer $authToken", mapOf("fcmToken" to fcmToken))
                                    } catch (e: Exception) {
                                        android.util.Log.e("LOGIN", "Failed to update shopkeeper token", e)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    errorMessage = response.body()?.message ?: "Invalid credentials"
                }
            } catch (e: Exception) {
                errorMessage = "Connection Failed: Check your server"
            } finally {
                isLoading = false
            }
        }
    }
}
