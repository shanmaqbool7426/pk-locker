package com.pksafe.lock.manager.data

import com.google.gson.annotations.SerializedName
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// --- Auth ---
data class Shopkeeper(
    @SerializedName("_id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("shopName") val shopName: String? = null,
    @SerializedName("role") val role: String? = null
)

data class LoginRequest(val phone: String, val password: String)
data class LoginResponse(val success: Boolean, val message: String, val token: String? = null, val shopkeeper: Shopkeeper? = null)

data class SignupRequest(val name: String, val password: String, val phone: String, val shopName: String, val role: String = "shopkeeper", val referredByPhone: String? = null)
data class SignupResponse(val success: Boolean, val message: String, val shopkeeper: Shopkeeper? = null)

// --- Dashboard Stats ---
data class StatsResponse(val success: Boolean, val data: DashboardData)
data class DashboardData(
    val android: PlatformKeys, 
    val ios: PlatformKeys, 
    val devices: DeviceStats,
    val analytics: DashboardAnalytics? = null
)
data class PlatformKeys(val totalKeys: Int, val usedKeys: Int, val availableKeys: Int)
data class DeviceStats(val total: Int, val locked: Int, val deregistered: Int)

data class DashboardAnalytics(
    val monthlyCollection: Double,
    val collectionRate: String,
    val highRiskCount: Int,
    val overdueTrend: List<OverdueEntry>,
    val bestCustomers: List<BestCustomerEntry>,
    val deviceStats: AnalyticsDeviceStats
)
data class OverdueEntry(val month: Int, val count: Int)
data class BestCustomerEntry(val name: String, val amount: Double)
data class AnalyticsDeviceStats(val locked: Int, val unlocked: Int)

// --- Devices ---
data class DeviceListResponse(val success: Boolean, val count: Int, val data: List<DeviceResponse>)

data class DeviceResponse(
    @SerializedName("imei") val imei: String,
    @SerializedName("imei2") val imei2: String? = null,
    @SerializedName("customerName") val customerName: String,
    @SerializedName("cnic") val cnic: String,
    @SerializedName("phoneNumber") val phoneNumber: String,
    @SerializedName("profilePicture") val profilePicture: String? = null,
    @SerializedName("cnicProofImage") val cnicProofImage: String? = null,
    @SerializedName("brand") val brand: String? = null,
    @SerializedName("model") val model: String? = null,
    @SerializedName("androidVersion") val androidVersion: String? = null,
    @SerializedName("status") val status: String = "Unlocked",
    @SerializedName("productName") val productName: String? = null,
    @SerializedName("totalPrice") val totalPrice: Double = 0.0,
    @SerializedName("downPayment") val downPayment: Double = 0.0,
    @SerializedName("balance") val balance: Double = 0.0,
    @SerializedName("emiTenure") val emiTenure: Int = 0,
    @SerializedName("emiAmount") val emiAmount: Double = 0.0,
    @SerializedName("emiStartDate") val emiStartDate: String? = null,
    @SerializedName("guarantor") val guarantor: Guarantor? = null,
    @SerializedName("registeredAt") val registeredAt: String? = null,
    @SerializedName("smsCodes") val smsCodes: SmsCodes? = null,
    @SerializedName("controls") val controls: DeviceControls? = null,
    @SerializedName("appRestrictions") val appRestrictions: AppRestrictions? = null,
    @SerializedName("location") val location: LocationData? = null,
    @SerializedName("geofence") val geofence: GeofenceData? = null,
    @SerializedName("locationHistory") val locationHistory: List<LocationEntry>? = null,
    @SerializedName("shopkeeper") val shopkeeper: Shopkeeper? = null
)

data class DeviceControls(
    @SerializedName("usbLock") val usbLock: Boolean = false,
    @SerializedName("cameraDisabled") val cameraDisabled: Boolean = false,
    @SerializedName("installBlocked") val installBlocked: Boolean = false,
    @SerializedName("uninstallBlocked") val uninstallBlocked: Boolean = false,
    @SerializedName("settingsBlocked") val settingsBlocked: Boolean = false,
    @SerializedName("debuggingBlocked") val debuggingBlocked: Boolean = false,
    @SerializedName("autoLock") val autoLock: Boolean = false,
    @SerializedName("autoLockOnSimChange") val autoLockOnSimChange: Boolean = false, // NEW
    @SerializedName("softResetBlocked") val softResetBlocked: Boolean = false,
    @SerializedName("softBootBlocked") val softBootBlocked: Boolean = false,
    @SerializedName("outgoingCallsBlocked") val outgoingCallsBlocked: Boolean = false,
    @SerializedName("warningAudio") val warningAudio: Boolean = false,
    @SerializedName("warningWallpaper") val warningWallpaper: String? = null
)

data class AppRestrictions(
    @SerializedName("whatsapp") val whatsapp: Boolean = false,
    @SerializedName("facebook") val facebook: Boolean = false,
    @SerializedName("instagram") val instagram: Boolean = false,
    @SerializedName("youtube") val youtube: Boolean = false,
    @SerializedName("chrome") val chrome: Boolean = false,
    @SerializedName("telegram") val telegram: Boolean = false
)

data class LocationData(
    val lat: Double,
    val lng: Double,
    val updatedAt: String? = null
)

data class GeofenceData(
    val lat: Double? = null,
    val lng: Double? = null,
    val radius: Double = 5.0,
    val isEnabled: Boolean = false,
    val lastBreachAt: String? = null
)

data class LocationEntry(
    val lat: Double,
    val lng: Double,
    val timestamp: String? = null
)

// --- EMI Schedule Details ---
data class DeviceEmiScheduleResponse(val success: Boolean, val data: EmiScheduleData)
data class EmiScheduleData(
    val imei: String,
    val customerName: String,
    val totalPrice: Double,
    val downPayment: Double,
    val balance: Double,
    val summary: EmiScheduleSummary,
    val schedule: List<EmiInstallmentItem>
)
data class EmiScheduleSummary(
    val total: Int,
    val paid: Int,
    val unpaid: Int,
    val paidTotal: Double,
    val unpaidTotal: Double
)
data class EmiInstallmentItem(
    val _id: String,
    val installmentNumber: Int,
    val dueDate: String,
    val amount: Double,
    val status: String
)

// --- Customer Specific Details ---
data class CustomerDeviceResponse(val success: Boolean, val data: CustomerDetailData)
data class CustomerDetailData(
    val device: DeviceResponse,
    val emiSummary: EmiSummary
)
data class EmiSummary(
    @SerializedName("total") val total: Int? = 0,
    @SerializedName("paid") val paid: Int? = 0,
    @SerializedName("unpaid") val unpaid: Int? = 0,
    @SerializedName("schedule") val schedule: List<EmiInstallment>? = null,
    @SerializedName("nextEmi") val nextEmi: NextEmi? = null
)
data class NextEmi(
    @SerializedName("amount") val amount: Double? = 0.0, 
    @SerializedName("dueDate") val dueDate: String? = null
)


data class EmiInstallment(
    val installmentNumber: Int,
    val dueDate: String,
    val amount: Double,
    val status: String
)

data class SmsCodes(val lockCode: String? = null, val unlockCode: String? = null)

data class DeviceRegistrationRequest(
    @SerializedName("imei") val imei: String,
    @SerializedName("imei2") val imei2: String? = null,
    @SerializedName("brand") val brand: String? = null,
    @SerializedName("model") val model: String? = null,
    @SerializedName("androidVersion") val androidVersion: String? = null,
    @SerializedName("customerName") val customerName: String,
    @SerializedName("cnic") val cnic: String,
    @SerializedName("phoneNumber") val phoneNumber: String,
    @SerializedName("productName") val productName: String? = null,
    @SerializedName("emiTenure") val emiTenure: Int = 0,
    @SerializedName("totalPrice") val totalPrice: Double = 0.0,
    @SerializedName("downPayment") val downPayment: Double = 0.0,
    @SerializedName("balance") val balance: Double = 0.0,
    @SerializedName("emiStartDate") val emiStartDate: String? = null,
    @SerializedName("emiAmount") val emiAmount: Double = 0.0,
    @SerializedName("fcmToken") val fcmToken: String? = null,
    @SerializedName("guarantor") val guarantor: Guarantor? = null,
    @SerializedName("profilePicture") val profilePicture: String? = null, // NEW: Customer Profile Photo
    @SerializedName("cnicProofImage") val cnicProofImage: String? = null // NEW: Customer CNIC Proof
)

data class Guarantor(
    @SerializedName("name") val name: String? = null, 
    @SerializedName("mobile") val mobile: String? = null, 
    @SerializedName("address") val address: String? = null,
    @SerializedName("cnicProofImage") val cnicProofImage: String? = null // NEW: Guarantor CNIC Proof
)
data class RegistrationResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("device") val device: DeviceSummary? = null
)
data class DeviceSummary(
    @SerializedName("id") val id: String,
    @SerializedName("imei") val imei: String,
    @SerializedName("customerName") val customerName: String,
    @SerializedName("smsCodes") val smsCodes: SmsCodes? = null
)
data class AdvancedControlRequest(
    @SerializedName("action") val action: String,
    @SerializedName("state") val state: Any
)

// --- Key Orders ---
data class KeyOrder(
    @SerializedName("_id") val id: String,
    @SerializedName("shopkeeper") val shopkeeper: ShopkeeperSummary?,
    @SerializedName("platform") val platform: String,
    @SerializedName("numKeys") val numKeys: Int,
    @SerializedName("unitPrice") val unitPrice: Double,
    @SerializedName("totalAmount") val totalAmount: Double,
    @SerializedName("status") val status: String, // Pending, Approved, Rejected
    @SerializedName("paymentProofImage") val paymentProofImage: String? = null,
    @SerializedName("createdAt") val createdAt: String
)

data class ShopkeeperSummary(
    @SerializedName("_id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("phone") val phone: String,
    @SerializedName("shopName") val shopName: String
)

data class KeyOrderListResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: List<KeyOrder>
)
data class GenericResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String
)

data class KeyRequest(
    @SerializedName("numKeys") val numKeys: Int,
    @SerializedName("paymentProofImage") val paymentProofImage: String,
    @SerializedName("platform") val platform: String = "android"
)
