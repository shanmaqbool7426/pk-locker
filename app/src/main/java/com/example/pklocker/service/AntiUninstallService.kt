package com.example.pklocker.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class AntiUninstallService : AccessibilityService() {

    companion object {
        // Ye sab pages customer ko nahi dikhne chahiye
        private val BLOCKED_KEYWORDS = listOf(
            // Hamari app related
            "pk locker",
            "pklocker",

            // Uninstall se bachao
            "uninstall",
            "delete app",

            // Permission pages
            "manage app if unused",     // Yahi masla tha!
            "remove permissions",
            "display over other",       // Overlay permission
            "appear on top",
            "draw over",
            "special app access",

            // Device Admin removal
            "device admin",
            "deactivate",
            "active admin",

            // ─── NEW: Accessibility Service ko band hone se roko ────────────
            // Agar customer Accessibility band kare → AntiUninstall kaam karega nahi
            "accessibility",
            "installed services",
            "downloaded apps",

            // Force stop se bachao
            "force stop",
            "force close",

            // Developer options protection
            "developer options",
            "usb debugging",
            "build number",       // Don't let them tap build number to enable dev options
            "about phone",
            "reset options",
            
            // Factory reset related
            "erase all data",
            "factory reset"
        )
    }



    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val prefs = applicationContext.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val isCustomer = prefs.getBoolean("is_customer", false)
        val isLocked = prefs.getBoolean("is_locked", false)

        if (!isCustomer) return

        val packageName = event.packageName?.toString()?.lowercase() ?: ""
        if (packageName.isEmpty()) return

        // 1. Dynamic App Blocking (From Shopkeeper Dashboard)
        val blockedApps = prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()
        val isAppBlocked = blockedApps.any { packageName.contains(it.lowercase()) }

        if (isAppBlocked || isLocked) {
            // Agar phone lock hai to sab kuch block (except our app and dialer maybe)
            // Lekin agar sirf specific app block hai dashboard se:
            if (isAppBlocked && !packageName.contains("pklocker")) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                Log.d("ANTI_GUARD", "App Blocked by Dashboard: $packageName")
                return 
            }
        }

        // 2. Settings protection
        val isSettingsApp = packageName.contains("settings") ||
                packageName.contains("packageinstaller") ||
                packageName.contains("permissioncontroller")

        if (!isSettingsApp) return

        // Screen content collect karo
        val screenText = buildString {
            append(event.text.toString().lowercase())
            rootInActiveWindow?.let { root ->
                append(root.text?.toString()?.lowercase() ?: "")
                append(root.contentDescription?.toString()?.lowercase() ?: "")
            }
        }

        val blockedKeyword = BLOCKED_KEYWORDS.firstOrNull { keyword ->
            screenText.contains(keyword)
        }

        if (blockedKeyword != null) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            Toast.makeText(this, "Security settings restricted.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onInterrupt() {
        Log.w("ANTI_GUARD", "AntiUninstallService interrupted")
    }
}
