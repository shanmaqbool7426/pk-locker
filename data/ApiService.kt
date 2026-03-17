package com.example.pklocker.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    // --- Shopkeeper Auth ---
    @POST("auth/login")
    suspend fun loginShopkeeper(@Body request: LoginRequest): Response<LoginResponse>

    @POST("auth/register")
    suspend fun signupShopkeeper(@Body request: SignupRequest): Response<SignupResponse>

    // --- Devices Management ---
    @POST("register-device")
    suspend fun registerDevice(@Body request: DeviceRegistrationRequest): Response<RegistrationResponse>

    @GET("devices")
    suspend fun getAllDevices(): Response<List<DeviceResponse>>

    @GET("devices/stats")
    suspend fun getStats(
        @Header("Authorization") token: String
    ): Response<StatsResponse>

    @POST("devices/{imei}/lock")
    suspend fun lockDevice(@Path("imei") imei: String): Response<RegistrationResponse>

    @POST("devices/{imei}/unlock")
    suspend fun unlockDevice(@Path("imei") imei: String): Response<RegistrationResponse>

    @POST("devices/{imei}/advanced-control")
    suspend fun sendAdvancedControl(
        @Path("imei") imei: String,
        @Body control: AdvancedControlRequest
    ): Response<RegistrationResponse>

    @POST("devices/update-token")
    suspend fun updateFcmToken(
        @Body body: Map<String, String>
    ): Response<RegistrationResponse>
}
