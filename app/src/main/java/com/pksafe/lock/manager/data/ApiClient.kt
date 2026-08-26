package com.pksafe.lock.manager.data

import java.util.concurrent.TimeUnit

/**
 * Shared Retrofit + OkHttp client for all ViewModels.
 *
 * WHY THIS EXISTS: OkHttp's DEFAULT read timeout is 10 seconds. The production
 * API runs on Vercel serverless — a cold-started function (Firebase Admin init +
 * Mongo connection + several sequential DB operations) can easily exceed 10s,
 * which surfaces on the client as a bare "Connection error" even though the
 * request was valid (e.g. MARK PAID failing while the schedule GET that ran
 * moments before succeeded on an already-warm instance).
 *
 * All calls through this client get generous timeouts to tolerate cold starts.
 */
object ApiClient {

    val okHttpClient: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun createApiService(): ApiService {
        return retrofit2.Retrofit.Builder()
            .baseUrl(com.pksafe.lock.manager.util.Constants.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
