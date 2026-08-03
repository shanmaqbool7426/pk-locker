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
                val prefs = getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
                val isAutoLockEnabled = prefs.getBoolean("auto_lock_enabled", false)
                val isCustomer = prefs.getBoolean("is_customer", false) || LockManager(applicationContext).isDeviceOwner()
                
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

        // 3. Settings & Uninstallation Protection
        val isSettingsApp = packageName.contains("settings") ||
                packageName.contains("packageinstaller") ||
                packageName.contains("permissioncontroller") ||
                packageName.contains("uninstaller") ||
                packageName.contains("installer")

        if (!isSettingsApp) return

        if (isSettingsBlocked) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }

        // Extract complete screen text recursively from full view tree
        val sb = StringBuilder()
        event.text.forEach { sb.append(it.toString().lowercase()).append(" ") }
        rootInActiveWindow?.let { root ->
            extractAllText(root, sb)
            root.recycle()
        }

        val screenText = sb.toString()
        if (screenText.isBlank()) return

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
