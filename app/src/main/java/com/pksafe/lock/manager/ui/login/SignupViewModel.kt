package com.pksafe.lock.manager.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pksafe.lock.manager.data.ApiService
import com.pksafe.lock.manager.data.SignupRequest
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SignupViewModel : ViewModel() {
    var name by mutableStateOf("")
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var phone by mutableStateOf("")
    var shopName by mutableStateOf("")
    var referredByPhone by mutableStateOf("")
    
    var isLoading by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var isSignupSuccess by mutableStateOf(false)

    private val retrofit = Retrofit.Builder()
        .baseUrl(com.pksafe.lock.manager.util.Constants.BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val apiService = retrofit.create(ApiService::class.java)

    fun onSignupClick() {
        if (name.isBlank() || email.isBlank() || password.isBlank() || phone.isBlank() || shopName.isBlank()) {
            message = "Please fill all required fields"
            return
        }

        viewModelScope.launch {
            isLoading = true
            message = null
            try {
                val request = SignupRequest(
                    name = name, 
                    email = email, 
                    password = password, 
                    phone = phone, 
                    shopName = shopName,
                    referredByPhone = referredByPhone.takeIf { it.isNotBlank() }
                )
                val response = apiService.signupShopkeeper(request)
                
                if (response.isSuccessful && response.body()?.success == true) {
                    isSignupSuccess = true
                    message = "Account created! Please login."
                } else {
                    val errorString = response.errorBody()?.string()
                    if (errorString != null && errorString.contains("message")) {
                        // Extract message manually or just show generic if it fails
                        try {
                            val jsonObject = org.json.JSONObject(errorString)
                            message = jsonObject.getString("message")
                        } catch (e: Exception) {
                            message = "Signup failed: Server rejected the request"
                        }
                    } else {
                        message = "Signup failed. Please try again."
                    }
                }
            } catch (e: Exception) {
                message = "Connection error: Check your internet or server"
            } finally {
                isLoading = false
            }
        }
    }
}
