package com.pksafe.lock.manager.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface ApiService {
    // --- Shopkeeper Auth ---
    @POST("auth/login")
    suspend fun loginShopkeeper(@Body request: LoginRequest): Response<LoginResponse>

    @POST("auth/register")
    suspend fun signupShopkeeper(@Body request: SignupRequest): Response<SignupResponse>

    // --- Devices Management ---
    @POST("devices/register")
    suspend fun registerDevice(
        @Header("Authorization") token: String,
        @Body request: DeviceRegistrationRequest
    ): Response<RegistrationResponse>

    @GET("devices")
    suspend fun getAllDevices(
        @Header("Authorization") token: String
    ): Response<DeviceListResponse>

    @GET("devices/deregistered")
    suspend fun getDeregisteredDevices(
        @Header("Authorization") token: String
    ): Response<DeviceListResponse>

    @GET("devices/stats")
    suspend fun getStats(
        @Header("Authorization") token: String
    ): Response<StatsResponse>

    @GET("devices/dashboard-analytics")
    suspend fun getDashboardAnalytics(
        @Header("Authorization") token: String
    ): Response<StatsResponse>

    @POST("devices/{imei}/lock")
    suspend fun lockDevice(
        @Header("Authorization") token: String,
        @Path("imei") imei: String
    ): Response<RegistrationResponse>

    @POST("devices/{imei}/unlock")
    suspend fun unlockDevice(
        @Header("Authorization") token: String,
        @Path("imei") imei: String
    ): Response<RegistrationResponse>

    @POST("devices/{imei}/controls")
    suspend fun sendAdvancedControl(
        @Header("Authorization") token: String,
        @Path("imei") imei: String,
        @Body control: AdvancedControlRequest
    ): Response<RegistrationResponse>

    @POST("devices/update-token")
    suspend fun updateFcmToken(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): Response<RegistrationResponse>

    @POST("devices/update-shopkeeper-token")
    suspend fun updateShopkeeperFcmToken(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): Response<RegistrationResponse>

    @POST("devices/{imei}/sim-changed")
    suspend fun notifySimChanged(
        @Path("imei") imei: String,
        @Body body: Map<String, String>
    ): Response<RegistrationResponse>

    @POST("devices/{imei}/location")
    suspend fun notifyLocation(
        @Path("imei") imei: String,
        @Body body: Map<String, String>
    ): Response<RegistrationResponse>

    @POST("devices/{imei}/unlock-all")
    suspend fun unlockAllControls(
        @Header("Authorization") token: String,
        @Path("imei") imei: String
    ): Response<RegistrationResponse>

    @POST("devices/{imei}/deregister")
    suspend fun deregisterDevice(
        @Header("Authorization") token: String,
        @Path("imei") imei: String
    ): Response<RegistrationResponse>

    // ── Customer-side: fetch device info + smsCodes for offline SMS locking ──
    // This is called when customer activates their device (enters IMEI).
    // smsCodes (lockCode + unlockCode) are saved to SharedPrefs by MainActivity
    // so SmsReceiver can use them offline without internet.
    @GET("devices/{imei}")
    suspend fun getDeviceStatus(
        @Header("Authorization") token: String,
        @Path("imei") imei: String
    ): Response<CustomerDeviceResponse>

    // --- EMI Management ---
    @GET("emis/device/{imei}")
    suspend fun getDeviceEmiSchedule(
        @Header("Authorization") token: String,
        @Path("imei") imei: String
    ): Response<DeviceEmiScheduleResponse>

    @POST("emis/{emiId}/mark-paid")
    suspend fun markEmiAsPaid(
        @Header("Authorization") token: String,
        @Path("emiId") emiId: String
    ): Response<RegistrationResponse>

    @POST("emis/device/{imei}")
    suspend fun rescheduleEmiPlan(
        @Header("Authorization") token: String,
        @Path("imei") imei: String,
        @Body request: RescheduleEmiRequest
    ): Response<RegistrationResponse>

    // --- Key Management ---
    @POST("key-orders/checkout-safepay")
    suspend fun checkoutKeys(
        @Header("Authorization") token: String,
        @Body request: Map<String, String> // e.g. ["numKeys" to "10", "platform" to "android"]
    ): Response<KeyCheckoutResponse>

    @POST("key-orders/verify-safepay")
    suspend fun verifyPayment(
        @Header("Authorization") token: String,
        @Body request: Map<String, String> // e.g. ["tracker" to "...", "orderId" to "..."]
    ): Response<RegistrationResponse>

    @POST("key-orders/free-test-keys")
    suspend fun allocateFreeKeys(
        @Header("Authorization") token: String,
        @Body request: Map<String, String> // e.g. ["numKeys" to "10"]
    ): Response<RegistrationResponse>

    @POST("key-orders/wallet-pay")
    suspend fun walletPay(
        @Header("Authorization") token: String,
        @Body request: WalletPayRequest
    ): Response<WalletPayResponse>

    @GET("key-orders/history")
    suspend fun getKeyHistory(
        @Header("Authorization") token: String
    ): Response<KeyHistoryResponse>
}

data class WalletPayRequest(
    val mobileNumber: String,
    val method: String, // "EasyPaisa" or "JazzCash"
    val numKeys: String,
    val platform: String = "android"
)

data class WalletPayResponse(
    val success: Boolean,
    val message: String,
    val transactionId: String,
    val availableKeys: Int? = null
)

data class KeyCheckoutResponse(
    val success: Boolean,
    val data: SafepayData
)

data class SafepayData(
    val orderId: String,
    val amount: Double,
    val tracker: String,
    val checkoutUrl: String
)

data class KeyHistoryResponse(
    val success: Boolean,
    val data: List<KeyOrderData>
)

data class KeyOrderData(
    val _id: String,
    val numKeys: Int,
    val totalAmount: Double,
    val status: String,
    val createdAt: String
)

data class RescheduleEmiRequest(
    val emiTenure: Int,
    val emiAmount: Double,
    val totalPrice: Double,
    val downPayment: Double,
    val balance: Double,
    val emiStartDate: String? = null
)
