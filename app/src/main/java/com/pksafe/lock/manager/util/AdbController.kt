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
     * Retries the whole flow up to [maxAttempts] times for reliability.
     *
     * @param pairingCode 6-digit pairing code from the target device
     * @param listener Progress callback for logging
     * @param maxAttempts number of connection attempts before giving up
     * @throws Exception if all attempts fail
     */
    @Throws(Exception::class)
    fun pairAndConnectFromCode(
        pairingCode: String,
        listener: ProgressListener,
        maxAttempts: Int = 3
    ) {
        val code = pairingCode.trim()
        if (!code.matches(Regex("\\d{6}"))) {
            throw IllegalArgumentException("Enter the 6-digit Wi-Fi pairing code from the target phone")
        }

        var lastError: Exception? = null

        for (attempt in 1..maxAttempts) {
            try {
                if (attempt > 1) {
                    log(listener, "Retry attempt $attempt/$maxAttempts...")
                    // Brief pause before retry to let network/target settle
                    Thread.sleep(1000)
                }

                log(listener, "Searching target on same Wi-Fi...")
                val pairingService = AdbMdnsDiscovery.discoverPairing(context, 20000L, 3)
                log(listener, "Found ${pairingService.host} (pairing port ${pairingService.port})")

                log(listener, "Pairing with code $code...")
                pair(pairingService.host, pairingService.port, code)
                log(listener, "Pairing OK. Opening ADB session...")

                // Connect service may take a moment to appear after pairing
                val connectService = discoverConnectWithRetry(context, pairingService.host, 5)
                log(listener, "Connecting to ${connectService.host}:${connectService.port}")
                connect(connectService.host, connectService.port)
                log(listener, "Connected.")
                return
            } catch (e: Exception) {
                lastError = e
                log(listener, "Attempt $attempt failed: ${e.message}")
                disconnect()
            }
        }

        throw lastError ?: IllegalStateException("Could not connect after $maxAttempts attempts")
    }

    /**
     * Discovers the ADB connect service after pairing, with extra retries.
     * The connect service is sometimes slow to advertise after pairing completes.
     */
    @Throws(Exception::class)
    private fun discoverConnectWithRetry(
        context: Context,
        host: String,
        maxAttempts: Int
    ): AdbMdnsDiscovery.DiscoveredService {
        var lastError: Exception? = null
        for (attempt in 1..maxAttempts) {
            try {
                return AdbMdnsDiscovery.discoverConnect(context, host, 15000L, 2)
            } catch (e: Exception) {
                lastError = e
                if (attempt < maxAttempts) {
                    Thread.sleep(1000)
                }
            }
        }
        throw lastError ?: IllegalStateException("Could not find ADB connect service for $host")
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
