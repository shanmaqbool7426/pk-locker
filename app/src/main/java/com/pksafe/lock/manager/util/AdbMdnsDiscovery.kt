package com.pksafe.lock.manager.util

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Discovers ADB devices on the local network via mDNS/NSD.
 * Android 11+ wireless debugging uses mDNS for service discovery.
 *
 * This implementation uses CountDownLatch to wait for service resolution
 * instead of blind Thread.sleep(), and supports retry rounds for reliability.
 */
object AdbMdnsDiscovery {
    const val SERVICE_CONNECT = "_adb-tls-connect._tcp"
    const val SERVICE_PAIRING = "_adb-tls-pairing._tcp"

    data class DiscoveredService(
        val host: String,
        val port: Int,
        val serviceName: String
    )

    @Throws(Exception::class)
    fun discoverPairing(
        context: Context,
        timeoutMs: Long = 20000L,
        retryRounds: Int = 3
    ): DiscoveredService {
        return discoverWithRetries(context, SERVICE_PAIRING, null, timeoutMs, retryRounds)
    }

    @Throws(Exception::class)
    fun discoverConnect(
        context: Context,
        host: String,
        timeoutMs: Long = 20000L,
        retryRounds: Int = 3
    ): DiscoveredService {
        return discoverWithRetries(context, SERVICE_CONNECT, host, timeoutMs, retryRounds)
    }

    @Throws(Exception::class)
    private fun discoverWithRetries(
        context: Context,
        serviceType: String,
        expectedHost: String?,
        totalTimeoutMs: Long,
        retryRounds: Int
    ): DiscoveredService {
        val roundTimeout = totalTimeoutMs / retryRounds
        var lastError: Exception? = null

        for (round in 1..retryRounds) {
            try {
                return discoverSingleRound(context, serviceType, expectedHost, roundTimeout)
            } catch (e: Exception) {
                lastError = e
                // Small pause between rounds to let network settle
                if (round < retryRounds) {
                    Thread.sleep(500)
                }
            }
        }

        throw lastError ?: IllegalStateException(
            if (expectedHost != null) "ADB session not found for $expectedHost"
            else "No device found. Open Pair with pairing code on target, same Wi-Fi."
        )
    }

    @Throws(Exception::class)
    private fun discoverSingleRound(
        context: Context,
        serviceType: String,
        expectedHost: String?,
        timeoutMs: Long
    ): DiscoveredService {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            ?: throw IllegalStateException("NSD not available on this device")

        val resolvedRef = AtomicReference<NsdServiceInfo?>(null)
        val errorRef = AtomicReference<Exception?>(null)
        val latch = CountDownLatch(1)
        var listener: NsdManager.DiscoveryListener? = null

        listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                errorRef.set(IllegalStateException("Could not search network for ADB (error $errorCode)"))
                latch.countDown()
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                // If we already found a match, ignore further services
                if (resolvedRef.get() != null) return

                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val host: InetAddress = serviceInfo.host ?: return
                        val hostAddress = host.hostAddress ?: return
                        if (serviceInfo.port <= 0) return
                        if (expectedHost != null && expectedHost != hostAddress) return

                        // Only set if not already set by another concurrent resolution
                        if (resolvedRef.compareAndSet(null, serviceInfo)) {
                            latch.countDown()
                        }
                    }
                })
            }
        }

        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)

        try {
            // Wait for a resolved service or timeout
            val found = latch.await(timeoutMs, TimeUnit.MILLISECONDS)

            if (!found) {
                throw IllegalStateException(
                    if (expectedHost != null) "ADB session not found for $expectedHost"
                    else "No device found. Open Pair with pairing code on target, same Wi-Fi."
                )
            }

            errorRef.get()?.let { throw it }

            val serviceInfo = resolvedRef.get()
                ?: throw IllegalStateException("Service resolved but data missing")

            val host: InetAddress = serviceInfo.host
                ?: throw IllegalStateException("Resolved service has no host")
            val hostAddress = host.hostAddress
                ?: throw IllegalStateException("Resolved service has no IP")

            return DiscoveredService(hostAddress, serviceInfo.port, serviceInfo.serviceName)
        } finally {
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (_: Exception) {
                // Ignore
            }
        }
    }
}
