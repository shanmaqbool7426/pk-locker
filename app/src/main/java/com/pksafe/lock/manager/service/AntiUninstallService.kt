package com.pksafe.lock.manager.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.os.Handler
import android.os.Looper
import android.widget.Toast

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.pksafe.lock.manager.util.LockManager

import android.provider.Settings

import android.view.accessibility.AccessibilityNodeInfo

class AntiUninstallService : AccessibilityService() {
    
    private var connectivityReceiver: BroadcastReceiver? = null

    companion object {
        // --- 1. Blocked Keywords (ONLY dangerous actions) ---
        // Be VERY specific to avoid false positives on legitimate settings
        private val BLOCKED_KEYWORDS = listOf(
            // App removal / admin
            "uninstall pk locker", "uninstall pksafe", "delete app",
            "manage app if unused", "remove permissions",
            "device admin apps", "deactivate pk locker", "active admin apps",
            // Accessibility guard bypass
            "installed services", "downloaded apps",
            // Dangerous resets ONLY (specific phrases, not generic "reset")
            "factory data reset", "erase all data", "factory reset protection",
            // Developer/ADB bypass prevention
            "developer options", "usb debugging", "wireless debugging",
            // Force stop our app
            "force stop pk locker", "force close pk locker"
        )

        // --- 2. ALLOWED Settings (NEVER block these — customer needs them) ---
        // Be SPECIFIC to avoid matching dangerous screens
        private val ALLOWED_SETTINGS_KEYWORDS = listOf(
            // Basic settings that customer MUST be able to access
            "date", "time", "language", "display", "sound", "volume",
            "wi-fi", "wifi", "bluetooth", "mobile data", "network",
            "battery", "storage", "about phone", "status",
            "notifications", "app notifications", "app permissions",
            "accessibility", "security", "privacy",
            "accounts", "backup", "google",
            "brightness", "wallpaper", "theme", "font",
            // Additional common settings
            "date & time", "date and time", "time zone", "automatic date",
            "language & input", "keyboard", "spell checker",
            "region", "locale", "country",
            "screen", "rotation", "timeout", "sleep",
            "ringtone", "vibrate", "do not disturb", "dnd",
            "data usage", "data saver", "hotspot", "tethering",
            "sim", "cellular", "roaming", "apn",
            "location", "gps", "maps",
            "fingerprint", "face", "screen lock", "password",
            "pin", "pattern", "lock screen",
            "apps", "default apps", "special app access",
            "digital wellbeing", "parental", "focus",
            "system update", "software update", "android version",
            "model", "serial", "imei", "ip address",
            "build number", "oem"
        )

        // --- 3. Package Mapping ---
        private val APP_PACKAGE_MAP = mapOf(
            "whatsapp"  to listOf("com.whatsapp", "com.whatsapp.w4b"),
            "facebook"  to listOf("com.facebook.katana", "com.facebook.lite", "com.facebook.orca"),
            "instagram" to listOf("com.instagram.android", "com.instagram.lite"),
            "youtube"   to listOf("com.google.android.youtube", "com.google.android.apps.youtube.music"),
            "chrome"    to listOf("com.android.chrome", "com.chrome.beta"),
            "telegram"  to listOf("org.telegram.messenger", "org.thunderdog.challegram"),
            "hotstar"   to listOf("in.startv.hotstar", "com.hotstar.android")
        )

        // --- 3. Service Running Check ---
        fun isServiceRunning(context: Context): Boolean {
            try {
                // 1. Check System Settings for enabled accessibility services
                val enabledServicesSetting = try {
                    Settings.Secure.getString(
                        context.contentResolver,
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                    ) ?: ""
                } catch (_: Exception) { "" }

                if (enabledServicesSetting.contains(context.packageName)) {
                    return true
                }

                // 2. Fallback check via AccessibilityManager
                val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
                val enabledServices = am?.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK) ?: emptyList()

                for (service in enabledServices) {
                    val serviceInfo = service.resolveInfo?.serviceInfo ?: continue
                    if (serviceInfo.packageName == context.packageName) {
                        return true
                    }
                }
            } catch (e: Exception) {
                Log.e("ANTI_GUARD", "Error checking accessibility status: ${e.message}")
            }
            return false
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("ANTI_GUARD", "Service Connected - Initializing Auto-Lock Monitor")
        registerAutoLockReceiver()
    }

    private fun registerAutoLockReceiver() {
        if (connectivityReceiver != null) return
        
        connectivityReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // NO AUTO-LOCK on internet disconnect — user mandate: manual lock only
                Log.d("ANTI_GUARD", "Connectivity change detected (no auto-lock).")
            }
        }
        
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiver(connectivityReceiver, filter)
    }

    private fun extractAllText(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (node == null) return
        try {
            node.text?.let { sb.append(it.toString().lowercase()).append(" ") }
            node.contentDescription?.let { sb.append(it.toString().lowercase()).append(" ") }
            node.viewIdResourceName?.let { sb.append(it.lowercase()).append(" ") }
            val count = node.childCount
            for (i in 0 until count) {
                val child = node.getChild(i)
                if (child != null) {
                    extractAllText(child, sb)
                    child.recycle()
                }
            }
        } catch (_: Exception) { }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString()?.lowercase() ?: ""
        if (packageName.isEmpty()) return

        val prefs = applicationContext.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val lockManager = LockManager(applicationContext)
        val isCustomer = prefs.getBoolean("is_customer", false) || lockManager.isDeviceOwner()

        if (!isCustomer) return

        val isLocked = prefs.getBoolean("is_locked", false)
        val isSettingsBlocked = prefs.getBoolean("settings_blocked", false)

        // 1. Dynamic App Blocking
        val blockedApps = prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()
        val isAppBlocked = blockedApps.any { appKey ->
            val knownPackages = APP_PACKAGE_MAP[appKey.lowercase()]
            if (knownPackages != null) {
                // Known app — check if package matches exactly
                knownPackages.any { pkg -> packageName == pkg }
            } else {
                // Custom app — check if package contains the key (but be more precise)
                // Only block if the package name contains the app key as a whole word
                // to avoid false positives (e.g., "chat" shouldn't block "com.viber.voip")
                packageName.contains(appKey.lowercase()) && appKey.length > 3
            }
        }

        if (isAppBlocked && !packageName.contains("pklocker")) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            Handler(Looper.getMainLooper()).postDelayed({
                performGlobalAction(GLOBAL_ACTION_HOME)
            }, 100)
            return
        }

        // 2. Full device lock
        if (isLocked && !packageName.contains("pklocker") && !packageName.contains("dialer") && !packageName.contains("telecom")) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }

        // 3. Settings & Uninstallation Protection
        val isSettingsApp = packageName.contains("settings") ||
                packageName.contains("packageinstaller") ||
                packageName.contains("permissioncontroller") ||
                packageName.contains("uninstaller") ||
                packageName.contains("installer")

        if (!isSettingsApp) return

        // CRITICAL: Even if isSettingsBlocked is true, allow basic settings
        // Extract screen text to check for allowed keywords
        val sb = StringBuilder()
        event.text.forEach { sb.append(it.toString().lowercase()).append(" ") }
        rootInActiveWindow?.let { root ->
            extractAllText(root, sb)
            root.recycle()
        }

        val screenText = sb.toString()
        
        // CRITICAL: Check BLOCKED keywords FIRST (security priority)
        // If screen contains dangerous keywords, BLOCK immediately
        val blockedKeyword = BLOCKED_KEYWORDS.firstOrNull { keyword ->
            screenText.contains(keyword)
        }

        if (blockedKeyword != null) {
            Log.w("ANTI_GUARD", "Blocking restricted setting/action. Keyword matched: $blockedKeyword")
            performGlobalAction(GLOBAL_ACTION_BACK)
            Handler(Looper.getMainLooper()).postDelayed({
                performGlobalAction(GLOBAL_ACTION_HOME)
            }, 150)
            Toast.makeText(this, "Security settings restricted by PKLocker.", Toast.LENGTH_SHORT).show()
            return
        }

        // Only if NO blocked keywords found, check for allowed keywords
        val hasAllowedKeyword = ALLOWED_SETTINGS_KEYWORDS.any { keyword ->
            screenText.contains(keyword)
        }

        if (hasAllowedKeyword) {
            Log.d("ANTI_GUARD", "Allowed settings screen — not blocking even if isSettingsBlocked=true")
            return
        }

        // CRITICAL FIX: If screen text is blank or too short, DON'T block
        // This happens when accessibility event fires before screen content loads
        // Default to ALLOWING settings to avoid false positives
        if (screenText.isBlank() || screenText.length < 10) {
            Log.d("ANTI_GUARD", "Screen text blank/short — allowing settings (content not loaded yet)")
            return
        }

        // Only block settings if isSettingsBlocked is true AND not an allowed screen AND no blocked keywords
        if (isSettingsBlocked) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }

        // Extract complete screen text recursively from full view tree (if not already done)
        if (screenText.isBlank()) return
    }

    override fun onInterrupt() { }

    override fun onDestroy() {
        super.onDestroy()
        connectivityReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {}
        }
    }
}
