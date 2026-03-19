package com.example.pklocker.ui.registration

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pklocker.data.ApiService
import com.example.pklocker.data.DeviceRegistrationRequest
import com.example.pklocker.data.Guarantor
import com.example.pklocker.util.LockManager
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch
import android.net.Uri
import android.util.Base64
import java.io.InputStream
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RegistrationViewModel : ViewModel() {

    // Device Info
    var imei by mutableStateOf("")
    var imei2 by mutableStateOf("")
    var brand by mutableStateOf("")
    var model by mutableStateOf("")
    var androidVersion by mutableStateOf("")

    // Customer Info
    var name by mutableStateOf("")
    var cnic by mutableStateOf("")
    var phone by mutableStateOf("")
    
    // EMI Details
    var productName by mutableStateOf("")
    var totalPrice by mutableStateOf("")
    var downPayment by mutableStateOf("")
    var emiTenure by mutableStateOf("")
    var emiStartDate by mutableStateOf("")

    // Guarantor Info
    var guarantorName by mutableStateOf("")
    var guarantorPhone by mutableStateOf("")
    var guarantorAddress by mutableStateOf("")
    var customerCnicImage by mutableStateOf<String?>(null) // Base64
    var guarantorCnicImage by mutableStateOf<String?>(null) // Base64

    var isLoading by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var isSuccess by mutableStateOf(false)

    private val retrofit = Retrofit.Builder()
        .baseUrl(com.example.pklocker.util.Constants.BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val apiService = retrofit.create(ApiService::class.java)
    
    fun startScanner(context: Context) {
        val scanner = GmsBarcodeScanning.getClient(context)
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val rawValue: String? = barcode.rawValue
                if (!rawValue.isNullOrBlank()) {
                    imei = rawValue
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Scanning failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    fun registerDevice(context: Context) {
        val lockManager = LockManager(context)
        val sharedPrefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val token = sharedPrefs.getString("auth_token", "") ?: ""
        val fcmToken = sharedPrefs.getString("fcm_token", "") ?: ""

        if (token.isEmpty()) {
            message = "Error: Please login again."
            return
        }

        // Shopkeeper doesn't need these permissions on their own terminal to register a client
        if (!validateInputs()) return

        viewModelScope.launch {
            isLoading = true
            try {
                val total = totalPrice.toDoubleOrNull() ?: 0.0
                val down = downPayment.toDoubleOrNull() ?: 0.0
                val balance = total - down
                val tenureInt = emiTenure.toIntOrNull() ?: 0
                val emiAmount = if (tenureInt > 0) balance / tenureInt else 0.0

                val request = DeviceRegistrationRequest(
                    imei = imei,
                    imei2 = imei2,
                    brand = brand,
                    model = model,
                    androidVersion = androidVersion,
                    customerName = name,
                    cnic = cnic,
                    phoneNumber = phone,
                    productName = productName,
                    totalPrice = total,
                    downPayment = down,
                    balance = balance,
                    emiTenure = tenureInt,
                    emiStartDate = emiStartDate,
                    emiAmount = emiAmount,
                    guarantor = Guarantor(
                        name = guarantorName,
                        mobile = guarantorPhone,
                        address = guarantorAddress,
                        cnicProofImage = guarantorCnicImage
                    ),
                    cnicProofImage = customerCnicImage
                )
                
                val response = apiService.registerDevice("Bearer $token", request)
                
                if (response.isSuccessful && response.body()?.success == true) {
                    
                    // FCM Token sync for the newly registered IMEI if needed 
                    // (But usually this happens on the actual customer device)
                    if (fcmToken.isNotEmpty()) {
                        apiService.updateFcmToken("Bearer $token", mapOf("imei" to imei, "fcmToken" to fcmToken))
                    }

                    isSuccess = true
                    message = "Device Registered successfully!"
                    // CRITICAL: DO NOT set is_customer = true here, as this is the shopkeeper's phone.
                } else {
                    isSuccess = false
                    message = "Failed: ${response.body()?.message ?: response.message()}"
                }
            } catch (e: Exception) {
                isSuccess = false
                message = "Error: ${e.localizedMessage}"
                Log.e("Registration", "Error", e)
            } finally {
                isLoading = false
            }
        }
    }

    fun testUnlock(context: Context) {
        val lockManager = LockManager(context)
        lockManager.unlockDevice()
        message = "Device Unlocked (Service Stopped)"
        isSuccess = true
    }

    private fun validateInputs(): Boolean {
        if (imei.isBlank()) {
            message = "IMEI/QR is required"
            return false
        }
        if (name.isBlank()) {
            message = "Name is required"
            return false
        }
        if (cnic.length < 13) {
            message = "Invalid CNIC"
            return false
        }
        return true
    }

    fun convertUriToBase64(context: Context, uri: Uri): String? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            if (bytes != null) {
                Base64.encodeToString(bytes, Base64.DEFAULT)
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
