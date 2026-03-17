package com.example.pklocker.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pklocker.data.ApiService
import com.example.pklocker.data.SignupRequest
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SignupViewModel : ViewModel() {
    var name by mutableStateOf("")
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var phone by mutableStateOf("")
    var shopName by mutableStateOf("")
    
    var isLoading by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var isSignupSuccess by mutableStateOf(false)

    private val retrofit = Retrofit.Builder()
        .baseUrl("http://192.168.100.3:5000/api/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val apiService = retrofit.create(ApiService::class.java)

    fun onSignupClick() {
        if (name.isBlank() || email.isBlank() || password.isBlank() || phone.isBlank() || shopName.isBlank()) {
            message = "Please fill all fields"
            return
        }

        viewModelScope.launch {
            isLoading = true
            message = null
            try {
                val request = SignupRequest(name, email, password, phone, shopName)
                val response = apiService.signupShopkeeper(request)
                
                if (response.isSuccessful && response.body()?.success == true) {
                    isSignupSuccess = true
                    message = "Account created! Please login."
                } else {
                    message = response.body()?.message ?: "Signup failed"
                }
            } catch (e: Exception) {
                message = "Connection error: Check your server"
            } finally {
                isLoading = false
            }
        }
    }
}
