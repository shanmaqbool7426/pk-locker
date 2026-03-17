package com.example.pklocker.data

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
data class DashboardData(val android: PlatformKeys, val ios: PlatformKeys, val devices: DeviceStats)
data class PlatformKeys(val totalKeys: Int, val usedKeys: Int, val availableKeys: Int)
data class DeviceStats(val total: Int, val locked: Int, val deregistered: Int)

// --- Devices ---
data class DeviceListResponse(val success: Boolean, val count: Int, val data: List<DeviceResponse>)

data class DeviceResponse(
    @SerializedName("imei") val imei: String,
    @SerializedName("customerName") val customerName: String,
    @SerializedName("cnic") val cnic: String,
    @SerializedName("phoneNumber") val phoneNumber: String,
    @SerializedName("brand") val brand: String? = null,
    @SerializedName("model") val model: String? = null,
    @SerializedName("status") val status: String = "Unlocked",
    @SerializedName("emiTenure") val emiTenure: Int = 0,
    @SerializedName("emiAmount") val emiAmount: Double = 0.0,
    @SerializedName("totalPrice") val totalPrice: Double = 0.0,
    @SerializedName("emiStartDate") val emiStartDate: String? = null,
    @SerializedName("registeredAt") val registeredAt: String? = null,
    @SerializedName("smsCodes") val smsCodes: SmsCodes? = null
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
    val guarantor: Guarantor? = null
)

data class Guarantor(val name: String? = null, val mobile: String? = null, val address: String? = null)
data class RegistrationResponse(val success: Boolean, val message: String, val device: DeviceSummary? = null)
data class DeviceSummary(val id: String, val imei: String, val customerName: String, val smsCodes: SmsCodes? = null)
data class AdvancedControlRequest(val action: String, val state: Boolean)
