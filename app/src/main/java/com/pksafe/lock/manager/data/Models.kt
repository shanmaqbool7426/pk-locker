package com.pksafe.lock.manager.data

import com.google.gson.annotations.SerializedName

// --- Auth ---
data class Shopkeeper(
    val id: String,
    val name: String,
    val email: String,
    val phone: String,
    val shopName: String,
    val role: String
)

data class LoginRequest(val email: String, val password: String)
data class LoginResponse(val success: Boolean, val message: String, val token: String? = null, val shopkeeper: Shopkeeper? = null)

data class SignupRequest(val name: String, val email: String, val password: String, val phone: String, val shopName: String, val role: String = "shopkeeper")
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
    @SerializedName("customerName") val customerName: String,
    @SerializedName("cnic") val cnic: String,
    @SerializedName("phoneNumber") val phoneNumber: String,
    @SerializedName("brand") val brand: String? = null,
    @SerializedName("model") val model: String? = null,
    @SerializedName("androidVersion") val androidVersion: String? = null,
    @SerializedName("status") val status: String = "Unlocked",
    @SerializedName("emiTenure") val emiTenure: Int = 0,
    @SerializedName("emiAmount") val emiAmount: Double = 0.0,
    @SerializedName("totalPrice") val totalPrice: Double = 0.0,
    @SerializedName("emiStartDate") val emiStartDate: String? = null,
    @SerializedName("registeredAt") val registeredAt: String? = null,
    @SerializedName("smsCodes") val smsCodes: SmsCodes? = null,
    @SerializedName("controls") val controls: DeviceControls? = null,
    @SerializedName("appRestrictions") val appRestrictions: AppRestrictions? = null,
    @SerializedName("location") val location: LocationData? = null,
    @SerializedName("geofence") val geofence: GeofenceData? = null,
    @SerializedName("locationHistory") val locationHistory: List<LocationEntry>? = null
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
    val status: String // "Paid" or "Unpaid"
)

// --- Customer Specific Details ---
data class CustomerDeviceResponse(val success: Boolean, val data: CustomerDetailData)
data class CustomerDetailData(
    val device: DeviceResponse,
    val emiSummary: EmiSummary
)
data class EmiSummary(
    val total: Int,
    val paid: Int,
    val unpaid: Int,
    val schedule: List<EmiInstallment>
)
data class EmiInstallment(
    val installmentNumber: Int,
    val dueDate: String,
    val amount: Double,
    val status: String // Paid, Unpaid
)

data class SmsCodes(val lockCode: String? = null, val unlockCode: String? = null)

data class DeviceRegistrationRequest(
    val imei: String,
    val imei2: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val androidVersion: String? = null,
    val customerName: String,
    val cnic: String,
    val phoneNumber: String,
    val productName: String? = null,
    val emiTenure: Int = 0,
    val totalPrice: Double = 0.0,
    val downPayment: Double = 0.0,
    val balance: Double = 0.0,
    val emiStartDate: String? = null,
    val emiAmount: Double = 0.0,
    val fcmToken: String? = null,
    val guarantor: Guarantor? = null,
    val cnicProofImage: String? = null // NEW: Customer CNIC Proof
)

data class Guarantor(
    val name: String? = null, 
    val mobile: String? = null, 
    val address: String? = null,
    val cnicProofImage: String? = null // NEW: Guarantor CNIC Proof
)
data class RegistrationResponse(val success: Boolean, val message: String, val device: DeviceSummary? = null)
data class DeviceSummary(val id: String, val imei: String, val customerName: String, val smsCodes: SmsCodes? = null)
data class AdvancedControlRequest(val action: String, val state: Any)
