package com.pksafe.lock.manager

import android.app.Application
import android.os.Build
import io.github.muntashirakon.adb.PRNGFixes
import org.conscrypt.Conscrypt
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.security.Security

/**
 * Application class that initializes Conscrypt (TLS provider for ADB)
 * and HiddenApiBypass (for accessing hidden Android APIs needed by ADB).
 *
 * This is REQUIRED for wireless ADB to work on Android 11+.
 * Without Conscrypt, TLS handshake fails → socket errors.
 * Without HiddenApiBypass, android.sun.* classes are blocked.
 */
class PkLockerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Fix random number generation for older Android versions
        PRNGFixes.apply()
    }

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(base)
        // Initialize Conscrypt as the #1 TLS provider
        // This provides the cipher suites that ADB wireless debugging requires
        try {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
        } catch (e: Exception) {
            // Conscrypt not available - ADB TLS may fail
        }

        // Bypass hidden API restrictions on Android 9 (Pie) and later
        // This allows access to android.sun.security.* and android.sun.misc.* classes
        // which are needed for X.509 certificate generation in AdbConnectionManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                HiddenApiBypass.addHiddenApiExemptions("Landroid/sun/", "Landroid/app/")
            } catch (e: Exception) {
                // HiddenApiBypass not available
            }
        }
    }
}
