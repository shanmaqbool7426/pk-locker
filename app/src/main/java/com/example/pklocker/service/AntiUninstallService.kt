package com.example.pklocker.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.os.Handler
import android.os.Looper
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

        // ─── PROPER PACKAGE NAME MAPPING ──────────────────────────────────
        // Dashboard se "youtube" aata hai, lekin Android mein package name
        // "com.google.android.youtube" hota hai. Is mapping se exact match hoga.
        private val APP_PACKAGE_MAP = mapOf(
            "whatsapp"  to listOf("com.whatsapp", "com.whatsapp.w4b"),
            "facebook"  to listOf("com.facebook.katana", "com.facebook.lite", "com.facebook.orca"),
            "instagram" to listOf("com.instagram.android", "com.instagram.lite"),
            "youtube"   to listOf("com.google.android.youtube", "com.google.android.apps.youtube.music"),
            "chrome"    to listOf("com.android.chrome", "com.chrome.beta"),
            "telegram"  to listOf("org.telegram.messenger", "org.thunderdog.challegram"),
            "hotstar"   to listOf("in.startv.hotstar", "com.hotstar.android")
        )
    }



    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString()?.lowercase() ?: ""
        if (packageName.isNotEmpty()) {
            Log.d("ANTI_GUARD", "Event from: $packageName")
        }

        val prefs = applicationContext.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val isCustomer = prefs.getBoolean("is_customer", false)
        val isLocked = prefs.getBoolean("is_locked", false)
        val isSettingsBlocked = prefs.getBoolean("settings_blocked", false)

        if (!isCustomer) return
        
        Log.d("ANTI_GUARD", "Processing: $packageName")

        if (packageName.isEmpty()) return

        // ─── 1. Dynamic App Blocking (From Shopkeeper Dashboard) ──────────
        val blockedApps = prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()
        
        Log.d("ANTI_GUARD", "Checking if $packageName is blocked. Blocklist: $blockedApps")

        // Check using proper package name mapping
        val isAppBlocked = blockedApps.any { appKey ->
            val knownPackages = APP_PACKAGE_MAP[appKey.lowercase()]
            val matched = if (knownPackages != null) {
                knownPackages.any { pkg -> packageName == pkg }
            } else {
                packageName.contains(appKey.lowercase())
            }
            if (matched) Log.d("ANTI_GUARD", "MATCH FOUND: $appKey blocks $packageName")
            matched
        }

        if (isAppBlocked && !packageName.contains("pklocker")) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            Handler(Looper.getMainLooper()).postDelayed({
                performGlobalAction(GLOBAL_ACTION_HOME)
            }, 100)
            Log.d("ANTI_GUARD", "App Blocked by Dashboard: $packageName (matched: ${blockedApps})")
            return
        }

        // ─── 2. Full device lock: block everything except our app ──────────
        if (isLocked && !packageName.contains("pklocker") && !packageName.contains("dialer") && !packageName.contains("telecom")) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            Log.d("ANTI_GUARD", "Device Locked - Blocked: $packageName")
            return
        }

        // ─── 3. Settings Protection ──────────────────────────────────────
        val isSettingsApp = packageName.contains("settings") ||
                packageName.contains("packageinstaller") ||
                packageName.contains("permissioncontroller")

        if (!isSettingsApp) return

        // If settings are blocked by shopkeeper dashboard, block ALL settings access
        if (isSettingsBlocked) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            Log.d("ANTI_GUARD", "Settings Blocked by Dashboard")
            return
        }

        // Screen content collect karo for keyword-based blocking
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
