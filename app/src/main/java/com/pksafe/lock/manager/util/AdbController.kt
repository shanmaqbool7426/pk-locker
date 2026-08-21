package com.pksafe.lock.manager.util

import android.content.Context

/**
 * High-level ADB controller that orchestrates wireless ADB pairing and connection.
 * Handles mDNS discovery, pairing with 6-digit code, and ADB session establishment.
 */
class AdbController(private val context: Context) {

    interface ProgressListener {
        fun onLog(message: String)
    }

    /**
     * Full flow: discover pairing service → pair with code → discover connect service → connect.
     * @param pairingCode 6-digit pairing code from the target device
     * @param listener Progress callback for logging
     * @throws Exception if any step fails
     */
    @Throws(Exception::class)
    fun pairAndConnectFromCode(pairingCode: String, listener: ProgressListener) {
        val code = pairingCode.trim()
        if (!code.matches(Regex("\\d{6}"))) {
            throw IllegalArgumentException("Enter the 6-digit Wi-Fi pairing code from the target phone")
        }

        log(listener, "Searching target on same Wi-Fi...")
        val pairingService = AdbMdnsDiscovery.discoverPairing(context, 20000L)
        log(listener, "Found ${pairingService.host} (pairing port ${pairingService.port})")

        log(listener, "Pairing with code $code...")
        pair(pairingService.host, pairingService.port, code)
        log(listener, "Pairing OK. Opening ADB session...")

        val connectService = AdbMdnsDiscovery.discoverConnect(context, pairingService.host, 20000L)
        log(listener, "Connecting to ${connectService.host}:${connectService.port}")
        connect(connectService.host, connectService.port)
        log(listener, "Connected.")
    }

    @Throws(Exception::class)
    fun pair(host: String, port: Int, pairingCode: String) {
        AdbConnectionManager.getInstance(context).pair(host, port, pairingCode)
    }

    @Throws(Exception::class)
    fun connect(host: String, port: Int) {
        AdbConnectionManager.getInstance(context).connect(host, port)
    }

    fun disconnect() {
        try {
            AdbConnectionManager.getInstance(context).disconnect()
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun isConnected(): Boolean {
        return try {
            AdbConnectionManager.getInstance(context).isConnected
        } catch (e: Exception) {
            false
        }
    }

    private fun log(listener: ProgressListener?, message: String) {
        listener?.onLog(message)
    }
}
