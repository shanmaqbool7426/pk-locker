package com.pksafe.lock.manager.util

import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.MessageDigest

/**
 * Shared helpers used by both QR and NFC provisioning screens.
 * Centralizes IP detection and signature checksum calculation so both flows
 * use the exact same values and avoid hardcoded mismatches.
 */
object ProvisioningQrScreenHelper {

    private const val TAG = "ProvisioningHelper"

    /**
     * Get the best reachable IPv4 address for this phone.
     *
     * Priority:
     * 1. WiFi connection IP (most reliable for same-network provisioning)
     * 2. Hotspot/tethering interface IP (when shopkeeper phone is the hotspot)
     * 3. Any other active non-loopback IPv4 interface
     */
    fun getBestDeviceIpAddress(context: Context): String? {
        try {
            // Method 1: WifiManager — primary WiFi IP
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiInfo = wifiManager?.connectionInfo
            val ipInt = wifiInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                val ip = String.format(
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
                if (ip != "0.0.0.0") {
                    Log.d(TAG, "WiFi IP detected: $ip")
                    return ip
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "WifiManager IP detection failed: ${e.message}")
        }

        // Method 2: NetworkInterface — works for hotspot and tethering
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            val candidates = mutableListOf<String>()

            while (interfaces.hasMoreElements()) {
                val netInterface = interfaces.nextElement()
                if (netInterface.isLoopback || !netInterface.isUp) continue

                val name = netInterface.name.lowercase()
                val addresses = netInterface.inetAddresses

                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress
                        if (!host.isNullOrBlank()) {
                            candidates.add(host)
                            // Prefer hotspot/tethering interfaces if present
                            if (name.contains("wlan") || name.contains("ap") || name.contains("p2p")) {
                                Log.d(TAG, "Preferred interface $name IP: $host")
                                return host
                            }
                        }
                    }
                }
            }

            if (candidates.isNotEmpty()) {
                Log.d(TAG, "Fallback network IP: ${candidates.first()}")
                return candidates.first()
            }
        } catch (e: Exception) {
            Log.w(TAG, "NetworkInterface IP detection failed: ${e.message}")
        }

        return null
    }

    /**
     * Compute the Base64 URL-safe SHA-256 hash of the app's signing certificate.
     * This checksum is REQUIRED by Android for QR / NFC Device Owner provisioning.
     */
    fun getAppSignatureHash(context: Context): String {
        return try {
            val pm = context.packageManager
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                ).signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
            }

            val firstSig = signatures?.getOrNull(0) ?: return "No Signature"
            val md = MessageDigest.getInstance("SHA-256")
            md.update(firstSig.toByteArray())
            val digest = md.digest()
            android.util.Base64.encodeToString(
                digest,
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compute signature hash", e)
            "Error detecting signature"
        }
    }

    /**
     * Format bytes into human-readable size.
     */
    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
            bytes >= 1_000 -> "%.1f KB".format(bytes / 1_000.0)
            else -> "$bytes B"
        }
    }
}
