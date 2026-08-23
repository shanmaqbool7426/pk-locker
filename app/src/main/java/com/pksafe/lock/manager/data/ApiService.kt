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
    ): Response<DashboardAnalyticsResponse>

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

    // Heartbeat: lightweight ping that updates lastSeen on server
    // and returns current device state for missed-command detection
    @POST("devices/{imei}/heartbeat")
    suspend fun sendHeartbeat(
        @Path("imei") imei: String
    ): Response<HeartbeatResponse>

    // Command acknowledgment: device tells server it received and processed an FCM command
    @POST("devices/{imei}/command-ack")
    suspend fun sendCommandAck(
        @Path("imei") imei: String,
        @Body body: Map<String, String>
    ): Response<RegistrationResponse>

    // Activity Log (Audit Trail): paginated history of all actions on a device
    @GET("devices/{imei}/activity")
    suspend fun getActivityLog(
        @Header("Authorization") token: String,
        @Path("imei") imei: String,
        @retrofit2.http.Query("page") page: Int = 1,
        @retrofit2.http.Query("limit") limit: Int = 50
    ): Response<ActivityLogResponse>

    @POST("devices/{imei}/deregister")
    suspend fun deregisterDevice(
        @Header("Authorization") token: String,
        @Path("imei") imei: String
    ): Response<DeregisterResponse>

    // ── Customer-side: fetch device info + smsCodes for offline SMS locking ──
    // This is called when customer activates their device (enters IMEI).
    // smsCodes (lockCode + unlockCode) are saved to SharedPrefs by MainActivity
    // so SmsReceiver can use them offline without internet.
    @GET("devices/public/{imei}")
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
        @Path("emiId") emiId: String,
        @Body body: Map<String, Any> = emptyMap()
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

    // --- Admin Key Management ---
    @GET("admin/key-orders")
    suspend fun getAdminKeyOrders(
        @Header("Authorization") token: String
    ): Response<KeyOrderListResponse>

    @POST("key-orders/request")
    suspend fun submitKeyRequest(
        @Header("Authorization") token: String,
        @Body request: KeyRequest
    ): Response<KeyOrderListResponse>

    @POST("admin/key-orders/{id}/approve")
    suspend fun approveKeyOrder(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<GenericResponse>

    @POST("admin/key-orders/{id}/reject")
    suspend fun rejectKeyOrder(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body body: Map<String, String> // ["notes" to "..."]
    ): Response<GenericResponse>
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
