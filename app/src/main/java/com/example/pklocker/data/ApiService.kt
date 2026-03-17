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
    @POST("devices/register")
    suspend fun registerDevice(
        @Header("Authorization") token: String,
        @Body request: DeviceRegistrationRequest
    ): Response<RegistrationResponse>

    @GET("devices")
    suspend fun getAllDevices(
        @Header("Authorization") token: String
    ): Response<DeviceListResponse>

    @GET("devices/stats")
    suspend fun getStats(
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
}
