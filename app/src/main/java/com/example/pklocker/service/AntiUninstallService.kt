package com.example.pklocker.service

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
import com.example.pklocker.util.LockManager

class AntiUninstallService : AccessibilityService() {
    
    private var connectivityReceiver: BroadcastReceiver? = null

    companion object {
        // --- 1. Blocked Keywords ---
        private val BLOCKED_KEYWORDS = listOf(
            "pk locker", "pklocker", "uninstall", "delete app",
            "manage app if unused", "remove permissions", "display over other",
            "appear on top", "draw over", "special app access",
            "device admin", "deactivate", "active admin",
            "accessibility", "installed services", "downloaded apps",
            "force stop", "force close", "developer options", "usb debugging",
            "build number", "about phone", "reset options", "erase all data",
            "factory reset", "reset"
        )

        // --- 2. Package Mapping ---
        private val APP_PACKAGE_MAP = mapOf(
            "whatsapp"  to listOf("com.whatsapp", "com.whatsapp.w4b"),
            "facebook"  to listOf("com.facebook.katana", "com.facebook.lite", "com.facebook.orca"),
            "instagram" to listOf("com.instagram.android", "com.instagram.lite"),
            "youtube"   to listOf("com.google.android.youtube", "com.google.android.apps.youtube.music"),
            "chrome"    to listOf("com.android.chrome", "com.chrome.beta"),
            "telegram"  to listOf("org.telegram.messenger", "org.thunderdog.challegram"),
            "hotstar"   to listOf("in.startv.hotstar", "com.hotstar.android")
        )

        // --- 3. Service Running Check (CRITICAL FIX) ---
        fun isServiceRunning(context: Context): Boolean {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
            val enabledServices = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC)
            for (service in enabledServices) {
                val serviceInfo = service.resolveInfo.serviceInfo
                if (serviceInfo.packageName == context.packageName &&
                    serviceInfo.name.contains("AntiUninstallService")) {
                    return true
                }
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
                val prefs = getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
                val isAutoLockEnabled = prefs.getBoolean("auto_lock_enabled", false)
                val isCustomer = prefs.getBoolean("is_customer", false)
                
                if (isCustomer && isAutoLockEnabled && !isOnline()) {
                    Log.w("AUTO_LOCK", "Internet disconnected! Triggering Lock from Guard Service.")
                    
                    // Trigger Lock
                    prefs.edit().putBoolean("is_locked", true).commit()
                    val lockManager = LockManager(applicationContext)
                    lockManager.lockDevice()
                }
            }
        }
        
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiver(connectivityReceiver, filter)
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString()?.lowercase() ?: ""

        val prefs = applicationContext.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val isCustomer = prefs.getBoolean("is_customer", false)

        if (!isCustomer) return

        val isLocked = prefs.getBoolean("is_locked", false)
        val isSettingsBlocked = prefs.getBoolean("settings_blocked", false)

        if (packageName.isEmpty()) return

        // 1. Dynamic App Blocking
        val blockedApps = prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()
        val isAppBlocked = blockedApps.any { appKey ->
            val knownPackages = APP_PACKAGE_MAP[appKey.lowercase()]
            if (knownPackages != null) {
                knownPackages.any { pkg -> packageName == pkg }
            } else {
                packageName.contains(appKey.lowercase())
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

        // 3. Settings Protection
        val isSettingsApp = packageName.contains("settings") ||
                packageName.contains("packageinstaller") ||
                packageName.contains("permissioncontroller")

        if (!isSettingsApp) return

        if (isSettingsBlocked) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }

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
