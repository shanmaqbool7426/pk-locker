package com.pksafe.lock.manager.util

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicReference

/**
 * Discovers ADB devices on the local network via mDNS/NSD.
 * Android 11+ wireless debugging uses mDNS for service discovery.
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
    fun discoverPairing(context: Context, timeout: Long): DiscoveredService {
        return discover(context, SERVICE_PAIRING, null, timeout)
    }

    @Throws(Exception::class)
    fun discoverConnect(context: Context, host: String, timeout: Long): DiscoveredService {
        return discover(context, SERVICE_CONNECT, host, timeout)
    }

    @Throws(Exception::class)
    private fun discover(
        context: Context,
        serviceType: String,
        expectedHost: String?,
        timeout: Long
    ): DiscoveredService {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            ?: throw IllegalStateException("NSD not available on this device")

        val discoveredServices = mutableListOf<NsdServiceInfo>()
        val errorRef = AtomicReference<Exception?>(null)

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                errorRef.set(IllegalStateException("Could not search network for ADB (error $errorCode)"))
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        synchronized(discoveredServices) {
                            discoveredServices.add(serviceInfo)
                        }
                    }
                })
            }
        }

        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)

        Thread.sleep(timeout)

        try {
            nsdManager.stopServiceDiscovery(listener)
        } catch (e: Exception) {
            // Ignore
        }
        Thread.sleep(800)

        errorRef.get()?.let { throw it }

        synchronized(discoveredServices) {
            for (serviceInfo in discoveredServices) {
                val host: InetAddress = serviceInfo.host ?: continue
                val hostAddress = host.hostAddress ?: continue
                if (serviceInfo.port > 0 && (expectedHost == null || expectedHost == hostAddress)) {
                    return DiscoveredService(hostAddress, serviceInfo.port, serviceInfo.serviceName)
                }
            }
        }

        throw IllegalStateException(
            if (expectedHost != null) "ADB session not found for $expectedHost"
            else "No device found. Open Pair with pairing code on target, same Wi-Fi."
        )
    }
}
