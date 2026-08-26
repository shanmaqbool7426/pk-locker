package com.pksafe.lock.manager

import android.app.Application
import android.content.Context
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

        // Auto-enable accessibility guard if this app is already Device Owner.
        // This runs on every app cold start so the guard stays active after ADB/cable setup.
        val lockManager = com.pksafe.lock.manager.util.LockManager(this)
        try {
            lockManager.ensureAccessibilityServiceEnabled()
        } catch (_: Exception) {
            // Ignore: will retry in MainActivity UI loop
        }
        // Apply reliable Device Owner restrictions (uninstall block, factory reset block, etc.)
        // This works even when accessibility service cannot be auto-enabled.
        // Only apply the ADB/wireless-debugging block on fully provisioned customer devices.
        // On the shopkeeper device (or during active provisioning) this would break the
        // wireless ADB connection needed to set up / manage customer phones.
        val prefs = getSharedPreferences("PKLockerPrefs", MODE_PRIVATE)
        val isCustomer = prefs.getBoolean("is_customer", false)
        val provisioningComplete = prefs.getBoolean("provisioning_complete", false)
        val provisioningActive = prefs.getBoolean("wireless_provisioning_active", false)
        try {
            // Never apply the ADB-killing restriction while a wireless provisioning
            // session is actively running; otherwise the shopkeeper connection drops
            // before all permissions are granted.
            if (isCustomer && provisioningComplete && !provisioningActive) {
                lockManager.applyDeviceOwnerProtection(includeDebuggingRestriction = true)
            } else {
                lockManager.applyDeviceOwnerProtection(includeDebuggingRestriction = false)
            }
        } catch (_: Exception) {
            // Ignore: will retry in MainActivity UI loop
        }
    }

    override fun attachBaseContext(base: Context) {
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
