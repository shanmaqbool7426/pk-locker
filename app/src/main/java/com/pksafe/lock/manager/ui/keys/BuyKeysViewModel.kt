package com.pksafe.lock.manager.ui.keys

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pksafe.lock.manager.data.*
import com.pksafe.lock.manager.util.Constants
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.InputStream

class BuyKeysViewModel : ViewModel() {
    var numKeys by mutableStateOf("10")
    var history by mutableStateOf<List<KeyOrderData>>(emptyList())
    var screenshotBase64 by mutableStateOf<String?>(null)
    var isLoading by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var isSuccess by mutableStateOf(false)

    private val retrofit = Retrofit.Builder()
        .baseUrl(Constants.BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val apiService = retrofit.create(ApiService::class.java)

    fun fetchHistory(context: Context) {
        val sharedPrefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val token = sharedPrefs.getString("auth_token", "") ?: ""
        
        viewModelScope.launch {
            try {
                val response = apiService.getKeyHistory("Bearer $token")
                if (response.isSuccessful) {
                    history = response.body()?.data ?: emptyList()
                }
            } catch (e: Exception) { }
        }
    }

    fun submitRequest(context: Context) {
        val sharedPrefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val token = sharedPrefs.getString("auth_token", "") ?: ""
        
        if (screenshotBase64 == null) {
            message = "Please upload payment screenshot"
            return
        }

        viewModelScope.launch {
            isLoading = true
            try {
                val keys = numKeys.toIntOrNull() ?: 0
                val request = KeyRequest(
                    numKeys = keys,
                    paymentProofImage = screenshotBase64 ?: "",
                    platform = "android"
                )
                
                val response = apiService.submitKeyRequest("Bearer $token", request)
                if (response.isSuccessful) {
                    isSuccess = true
                    message = "Request submitted! Pending approval."
                    fetchHistory(context)
                    screenshotBase64 = null
                } else {
                    message = "Error: ${response.message()}"
                }
            } catch (e: Exception) {
                message = "Network error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun handleImageSelection(context: Context, uri: Uri) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            if (bytes != null) {
                screenshotBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            message = "Failed to load image"
        }
    }
}
