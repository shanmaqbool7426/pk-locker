package com.example.pklocker.ui.login

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pklocker.data.ApiService
import com.example.pklocker.data.LoginRequest
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class LoginViewModel : ViewModel() {
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var isLoggedIn by mutableStateOf(false)

    private val retrofit = Retrofit.Builder()
        .baseUrl(com.example.pklocker.util.Constants.BASE_URL) 
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val apiService = retrofit.create(ApiService::class.java)

    fun onLoginClick(context: Context) {
        if (email.isBlank() || password.isBlank()) {
            errorMessage = "Please enter both email and password"
            return
        }

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val response = apiService.loginShopkeeper(LoginRequest(email, password))
                
                if (response.isSuccessful && response.body()?.success == true) {
                    val loginData = response.body()
                    isLoggedIn = true
                    
                    // Save Admin status and Shop info from the nested shopkeeper object
                    val sharedPrefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
                    sharedPrefs.edit().apply {
                        putBoolean("is_admin", true)
                        putBoolean("is_logged_in", true)
                        putString("shop_name", loginData?.shopkeeper?.shopName ?: "Shopkeeper")
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
