package com.pksafe.lock.manager.util

import android.content.Context
import java.util.regex.Pattern

/**
 * Orchestrates the full device provisioning workflow via wireless ADB.
 *
 * Steps:
 * 1. Pre-checks (ADB connected, no existing owner, no accounts)
 * 2. Prepare device (enable package, remove old admin)
 * 3. Set device owner via dpm set-device-owner
 * 4. Grant all runtime permissions via pm grant + appops set
 * 5. Enable anti-uninstall accessibility service
 * 6. Mark setup as complete (skip welcome wizard)
 */
class ProvisionWorkflow(private val context: Context) {

    private val shell = AdbShell(context.applicationContext)

    interface Listener {
        fun onLog(message: String)
        fun onComplete(success: Boolean, message: String)
    }

    companion object {
        private const val TARGET_PACKAGE = DeviceOwnerSetup.PACKAGE
        private const val DEVICE_ADMIN_COMPONENT = DeviceOwnerSetup.ADMIN_COMPONENT
        private val ACCOUNTS_COUNT = Pattern.compile("Accounts:\\s*(\\d+)")
    }

    /**
     * Sets device owner on the already-installed PK Locker app.
     * Assumes the app is already installed on the customer phone.
     */
    fun runDeviceOwnerOnly(listener: Listener) {
        try {
            runPreChecks(listener)
            prepareForDeviceOwner(listener)
            applyDeviceOwner(listener)
            grantCommonPermissions(listener)
            enableAccessibilityGuard(listener)
            markSetupComplete(listener)
            listener.onComplete(true, "Device owner active: $TARGET_PACKAGE")
        } catch (e: Exception) {
            listener.onComplete(false, formatError(e))
        }
    }

    @Throws(Exception::class)
    private fun runPreChecks(listener: Listener) {
        log(listener, "Checking ADB connection...")
        if (!AdbConnectionManager.getInstance(context).isConnected) {
            throw IllegalStateException("Connect to target wireless ADB first")
        }

        log(listener, "Checking existing device owner...")
        val owners = shell.run("dpm list-owners")
        log(listener, if (owners.isEmpty()) "(none)" else owners)

        // Only block if a DIFFERENT package is already device owner.
        // Ignore shell errors, empty output, or "no owners" messages.
        val lowerOwners = owners.lowercase()
        val hasOtherOwner = lowerOwners.contains("device owner") 
            && !lowerOwners.contains(TARGET_PACKAGE)
            && !lowerOwners.contains("no ")
        if (hasOtherOwner) {
            throw IllegalStateException("Another app is already Device Owner. Factory reset the target device first. Output: $owners")
        }

        log(listener, "Checking accounts (must be 0 for device owner)...")
        ensureNoAccounts(listener)
        log(listener, "Account check OK.")
    }

    @Throws(Exception::class)
    private fun prepareForDeviceOwner(listener: Listener) {
        log(listener, "Preparing device for device owner...")
        shell.run("pm enable $TARGET_PACKAGE 2>/dev/null || true")
        shell.run("pm unhide $TARGET_PACKAGE 2>/dev/null || true")
        shell.run("dpm remove-active-admin --user 0 $DEVICE_ADMIN_COMPONENT 2>/dev/null || true")
        val removeResult = shell.run("dpm remove-device-owner $TARGET_PACKAGE 2>/dev/null || true")
        if (removeResult.isNotEmpty()) {
            log(listener, removeResult)
        }
        log(listener, "Preparation done.")
    }

    @Throws(Exception::class)
    private fun applyDeviceOwner(listener: Listener) {
        log(listener, "Setting device owner...")
        log(listener, "Component: $DEVICE_ADMIN_COMPONENT")

        val commands = arrayOf(
            "dpm set-device-owner --user 0 $DEVICE_ADMIN_COMPONENT",
            "dpm set-device-owner $DEVICE_ADMIN_COMPONENT"
        )

        var output = ""
        var success = false

        for (cmd in commands) {
            log(listener, "Run: $cmd")
            output = shell.run(cmd)
            log(listener, if (output.isEmpty()) "(empty response)" else output)
            val lower = output.lowercase()
            if (lower.contains("success") || lower.contains("device owner set") || lower.contains("admin set")) {
                success = true
                break
            }
        }

        if (!success) {
            throw IllegalStateException(buildDeviceOwnerErrorHint(output))
        }

        if (!isDeviceOwnerSet(listener)) {
            throw IllegalStateException("Device owner command ran but verification did not confirm $TARGET_PACKAGE")
        }
        log(listener, "Device owner verified.")
    }

    @Throws(Exception::class)
    private fun isDeviceOwnerSet(listener: Listener): Boolean {
        log(listener, "Verifying device owner...")
        val owners = shell.run("dpm list-owners")
        log(listener, if (owners.isEmpty()) "(empty)" else owners.trim())
        if (owners.contains(TARGET_PACKAGE)) return true

        val dumpResult = shell.run("dumpsys device_policy 2>/dev/null | grep -m3 'Device Owner' 2>/dev/null")
        if (dumpResult.isEmpty()) return false
        log(listener, dumpResult.trim())
        return dumpResult.contains(TARGET_PACKAGE)
    }

    private fun grantCommonPermissions(listener: Listener) {
        log(listener, "Granting permissions...")
        for (perm in DeviceOwnerSetup.runtimePermissions) {
            try {
                val result = shell.run("pm grant $TARGET_PACKAGE $perm")
                if (result.isNotEmpty() && !result.lowercase().contains("granted")) {
                    log(listener, "$perm: $result")
                }
            } catch (e: Exception) {
                log(listener, "$perm skipped: ${e.message}")
            }
        }

        try {
            shell.run("appops set $TARGET_PACKAGE SYSTEM_ALERT_WINDOW allow")
            shell.run("appops set $TARGET_PACKAGE android:get_usage_stats allow")
            shell.run("appops set $TARGET_PACKAGE android:system_alert_window allow")
            shell.run("appops set $TARGET_PACKAGE android:write_settings allow")
            log(listener, "Overlay, usage stats, write settings granted.")
        } catch (e: Exception) {
            log(listener, "appops: ${e.message}")
        }
    }

    private fun enableAccessibilityGuard(listener: Listener) {
        log(listener, "Enabling Anti-Uninstall Accessibility Guard...")
        try {
            shell.run(DeviceOwnerSetup.ACCESSIBILITY_ENABLE_COMMAND)
            log(listener, "Accessibility guard enabled.")
        } catch (e: Exception) {
            log(listener, "Accessibility guard: ${e.message}")
        }
    }

    private fun markSetupComplete(listener: Listener) {
        log(listener, "Marking setup as complete (skip welcome wizard)...")
        try {
            shell.run("settings put secure user_setup_complete 1")
            shell.run("settings put global device_provisioned 1")
            log(listener, "Setup marked as complete.")
        } catch (e: Exception) {
            log(listener, "Setup marking: ${e.message}")
        }
    }

    @Throws(Exception::class)
    private fun ensureNoAccounts(listener: Listener) {
        val accountsOutput = shell.run("dumpsys account | grep -m1 'Accounts:'")
        log(listener, if (accountsOutput.isEmpty()) "Could not read account count" else accountsOutput.trim())

        val matcher = ACCOUNTS_COUNT.matcher(accountsOutput)
        if (matcher.find()) {
            val count = matcher.group(1).toInt()
            if (count > 0) {
                log(listener, "WARNING: $count account(s) found. Device owner may fail — continuing anyway...")
                // Don't throw — let the dpm command attempt and report the real error if it fails
            }
        } else {
            // Could not parse — don't block, let dpm set-device-owner decide
            log(listener, "Could not determine account count, proceeding...")
        }
    }

    private fun buildDeviceOwnerErrorHint(output: String): String {
        val lower = output.lowercase()
        return when {
            lower.contains("account") -> "Device owner blocked: remove all accounts and factory reset if needed. Output: $output"
            lower.contains("already") && lower.contains("admin") -> "Clear old device admin first (factory reset recommended). Output: $output"
            lower.contains("not found") || lower.contains("does not exist") -> "Device admin component not found — reinstall the lock APK. Output: $output"
            lower.contains("provisioning") -> "Complete or skip setup wizard on target, then retry. Output: $output"
            else -> "set-device-owner failed: $output"
        }
    }

    private fun formatError(e: Exception): String {
        val msg = e.message
        return if (msg.isNullOrEmpty()) e.javaClass.simpleName else msg
    }

    private fun log(listener: Listener, message: String) {
        listener.onLog(message)
    }
}
