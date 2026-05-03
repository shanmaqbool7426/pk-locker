package com.pksafe.lock.manager.util

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.pksafe.lock.manager.receiver.AdminReceiver
import com.pksafe.lock.manager.service.LockService
import android.telephony.TelephonyManager
import android.media.RingtoneManager
import android.media.Ringtone
import android.app.WallpaperManager
import java.net.URL
import android.graphics.BitmapFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LockManager(private val context: Context) {

    private val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(context, AdminReceiver::class.java)

    // ─── PROPER PACKAGE NAME MAPPING ──────────────────────────────────────
    // Dashboard keys → actual Android package names
    companion object {
        val APP_PACKAGE_MAP = mapOf(
            "whatsapp"  to listOf("com.whatsapp", "com.whatsapp.w4b"),
            "facebook"  to listOf("com.facebook.katana", "com.facebook.lite", "com.facebook.orca"),
            "instagram" to listOf("com.instagram.android", "com.instagram.lite"),
            "youtube"   to listOf("com.google.android.youtube", "com.google.android.apps.youtube.music"),
            "chrome"    to listOf("com.android.chrome", "com.chrome.beta"),
            "telegram"  to listOf("org.telegram.messenger", "org.thunderdog.challegram"),
            "hotstar"   to listOf("in.startv.hotstar", "com.hotstar.android")
        )
    }

    fun isAdminActive(): Boolean = devicePolicyManager.isAdminActive(adminComponent)

    fun isDeviceOwner(): Boolean = devicePolicyManager.isDeviceOwnerApp(context.packageName)

    fun requestAdminPermission() {
        try {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Critical Security Activation Required for EMI Protection.")
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("LOCK_MANAGER", "Admin request failed", e)
        }
    }

    fun canDrawOverlays(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true

    fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, 
                android.net.Uri.parse("package:${context.packageName}"))
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    // ─── FULL ENFORCEMENT (When Locked) ───────────────────────────────────────
    fun lockDevice() {
        if (!isAdminActive()) return

        try {
            // 1. Start Overlay
            val intent = Intent(context, LockService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }

            // 2. Hardware Restrictions (US Locker Style)
            applyHardRestrictions(true)

            // 3. Hardware Lock
            Handler(Looper.getMainLooper()).postDelayed({
                devicePolicyManager.lockNow()
            }, 1000)

        } catch (e: Exception) {
            Log.e("LOCK_ENFORCE", "Lock failed", e)
        }
    }

    fun unlockDevice() {
        try {
            context.stopService(Intent(context, LockService::class.java))
            
            // Remove Hardware Restrictions
            applyHardRestrictions(false)

            val prefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("is_locked", false).apply()
        } catch (e: Exception) {
            Log.e("UNLOCK_ENFORCE", "Unlock failed", e)
        }
    }

    // ─── Deep Security Guards ──────────────────────────────────────────────────
    private fun applyHardRestrictions(locked: Boolean) {
        if (!isAdminActive()) return

        try {
            // Camera Block
            devicePolicyManager.setCameraDisabled(adminComponent, locked)

            // Device Owner only features
            if (isDeviceOwner()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    // 1. Block USB File Transfer (PC se connect kar k data ya hack nahi kar sakay ga)
                    setUserRestriction(UserManager.DISALLOW_USB_FILE_TRANSFER, locked)
                    // 2. Block Factory Reset (No manual wipe possible)
                    setUserRestriction(UserManager.DISALLOW_FACTORY_RESET, locked)
                    // 3. Block Safe Mode (Safe mode bypass blocked)
                    setUserRestriction(UserManager.DISALLOW_SAFE_BOOT, locked)
                    // 4. Block ADB / Debugging (Crucial for blocking software-based bypass tools)
                    setUserRestriction(UserManager.DISALLOW_DEBUGGING_FEATURES, locked)
                    // 5. Block System Settings Changes
                    setUserRestriction(UserManager.DISALLOW_CONFIG_WIFI, locked)
                    setUserRestriction(UserManager.DISALLOW_SMS, locked)
                    setUserRestriction(UserManager.DISALLOW_OUTGOING_CALLS, locked)
                    setUserRestriction(UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA, locked) // No SD card/OTG hacks
                }
                
                // 6. Block Status Bar Expansion (Locked hone par notification shade nahi khulay gi)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        devicePolicyManager.setStatusBarDisabled(adminComponent, locked)
                        Log.d("LOCK_MANAGER", "StatusBar expansion restricted: $locked")
                    } catch (e: Exception) { Log.e("LOCK_MANAGER", "StatusBar Error: ${e.message}") }
                }

                // 7. Block LockScreen (Optional: Skip standard lock to show our custom UI directly)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    devicePolicyManager.setKeyguardDisabled(adminComponent, locked)
                }
            }
            Log.d("LOCK_MANAGER", "Ultra-Hardware Restrictions Applied: $locked")
        } catch (e: Exception) {
            Log.e("LOCK_MANAGER", "Error applying restrictions: ${e.message}")
        }
    }

    private fun setUserRestriction(restriction: String, enfore: Boolean) {
        if (enfore) {
            devicePolicyManager.addUserRestriction(adminComponent, restriction)
        } else {
            devicePolicyManager.clearUserRestriction(adminComponent, restriction)
        }
    }

    // ─── Individual Control Methods for Dashboard ──────────────────────────────
    
    fun setUsbDataDisabled(disabled: Boolean) {
        if (!isAdminActive() || !isDeviceOwner()) {
            Log.w("LOCK_MANAGER", "USB block requires Device Owner! isAdmin=${isAdminActive()}, isOwner=${isDeviceOwner()}")
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setUserRestriction(UserManager.DISALLOW_USB_FILE_TRANSFER, disabled)
                Log.d("LOCK_MANAGER", "USB File Transfer Disabled: $disabled")
            }
        } catch (e: Exception) {
            Log.e("LOCK_MANAGER", "USB block error: ${e.message}")
        }
    }

    fun setCameraDisabled(disabled: Boolean) {
        if (!isAdminActive()) {
            Log.w("LOCK_MANAGER", "Camera block requires Device Admin!")
            return
        }
        try {
            devicePolicyManager.setCameraDisabled(adminComponent, disabled)
            Log.d("LOCK_MANAGER", "Camera Disabled: $disabled")
        } catch (e: Exception) {
            Log.e("LOCK_MANAGER", "Camera block error: ${e.message}")
        }
    }

    fun setAppInstallDisabled(disabled: Boolean) {
        if (isDeviceOwner()) {
            setUserRestriction(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES, disabled)
            setUserRestriction(UserManager.DISALLOW_INSTALL_APPS, disabled)
        }
    }

    fun setAppUninstallDisabled(disabled: Boolean) {
        if (isDeviceOwner()) {
            setUserRestriction(UserManager.DISALLOW_UNINSTALL_APPS, disabled)
        }
    }

    fun setOutgoingCallsDisabled(disabled: Boolean) {
        if (isDeviceOwner()) {
            setUserRestriction(UserManager.DISALLOW_OUTGOING_CALLS, disabled)
        }
    }

    fun setFactoryResetDisabled(disabled: Boolean) {
        if (isDeviceOwner()) {
            setUserRestriction(UserManager.DISALLOW_FACTORY_RESET, disabled)
        }
    }

    fun setSafeBootDisabled(disabled: Boolean) {
        if (isDeviceOwner()) {
            setUserRestriction(UserManager.DISALLOW_SAFE_BOOT, disabled)
        }
    }

    // ─── APP HIDING (U.S. Locker Style) ────────────────────────────────────────
    // setApplicationHidden completely removes the app from launcher and recents
    // This is the REAL way to block apps — no Accessibility needed!
    fun setAppHidden(appKey: String, hidden: Boolean): Boolean {
        if (!isDeviceOwner()) {
            Log.w("LOCK_MANAGER", "App hiding requires Device Owner! Falling back to Accessibility blocking for: $appKey")
            return false // Caller should fall back to SharedPrefs/Accessibility approach
        }

        val packages = APP_PACKAGE_MAP[appKey.lowercase()] ?: return false
        var anySuccess = false

        for (pkg in packages) {
            try {
                // Check if the package is actually installed
                context.packageManager.getPackageInfo(pkg, 0)
                val result = devicePolicyManager.setApplicationHidden(adminComponent, pkg, hidden)
                if (result) {
                    anySuccess = true
                    Log.d("LOCK_MANAGER", "App ${if (hidden) "HIDDEN" else "VISIBLE"}: $pkg")
                }
            } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                // App not installed, skip silently
            } catch (e: Exception) {
                Log.e("LOCK_MANAGER", "App hide error for $pkg: ${e.message}")
            }
        }
        return anySuccess
    }

    // ─── U.S. LOCKER ADVANCED UPDATES ──────────────────────────────────────────
    
    /**
     * Enforces critical restrictions that should NEVER be off on a customer device,
     * even if the device is currently "Unlocked" (EMI is paid).
     */
    fun enforcePermanentRestrictions(enforce: Boolean) {
        if (!isAdminActive() || !isDeviceOwner()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // 1. Block Factory Reset (Most Important)
                setUserRestriction(UserManager.DISALLOW_FACTORY_RESET, enforce)
                // 2. Block USB File Transfer
                setUserRestriction(UserManager.DISALLOW_USB_FILE_TRANSFER, enforce)
                // 3. Block ADB/Debugging
                setUserRestriction(UserManager.DISALLOW_DEBUGGING_FEATURES, enforce)
                
                Log.d("LOCK_MANAGER", "Permanent Restrictions Enforced: $enforce")
            }
        } catch (e: Exception) {
            Log.e("LOCK_MANAGER", "Permanent Enforce Error: ${e.message}")
        }
    }
    
    private var ringtone: Ringtone? = null

    fun toggleWarningAlarm(play: Boolean) {
        try {
            if (play) {
                val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ringtone = RingtoneManager.getRingtone(context, notification)
                ringtone?.play()
                Log.d("LOCK_MANAGER", "Warning Alarm Started")
            } else {
                ringtone?.stop()
                Log.d("LOCK_MANAGER", "Warning Alarm Stopped")
            }
        } catch (e: Exception) {
            Log.e("LOCK_MANAGER", "Alarm Error: ${e.message}")
        }
    }

    fun setWarningWallpaper(imageUrl: String?) {
        if (imageUrl.isNullOrBlank()) return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(imageUrl)
                val bitmap = BitmapFactory.decodeStream(url.openConnection().getInputStream())
                val wallpaperManager = WallpaperManager.getInstance(context)
                wallpaperManager.setBitmap(bitmap)
                Log.d("LOCK_MANAGER", "Wallpaper Updated Successfully")
            } catch (e: Exception) {
                Log.e("LOCK_MANAGER", "Wallpaper Update Failed: ${e.message}")
            }
        }
    }

    // ─── SELF DEACTIVATE (Shopkeeper-triggered remote release) ────────────────
    // This removes all Device Admin / Device Owner privileges from the app so
    // the customer can freely uninstall it via normal Settings > Apps.
    fun selfDeactivate() {
        try {
            // Step 1: Clear all user restrictions first (required before removing Device Owner)
            if (isDeviceOwner()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    // Remove every restriction we may have applied
                    listOf(
                        android.os.UserManager.DISALLOW_USB_FILE_TRANSFER,
                        android.os.UserManager.DISALLOW_FACTORY_RESET,
                        android.os.UserManager.DISALLOW_SAFE_BOOT,
                        android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
                        android.os.UserManager.DISALLOW_INSTALL_APPS,
                        android.os.UserManager.DISALLOW_UNINSTALL_APPS,
                        android.os.UserManager.DISALLOW_OUTGOING_CALLS
                    ).forEach { restriction ->
                        try {
                            devicePolicyManager.clearUserRestriction(adminComponent, restriction)
                        } catch (e: Exception) {
                            Log.w("LOCK_MANAGER", "Could not clear restriction $restriction: ${e.message}")
                        }
                    }
                    Log.d("LOCK_MANAGER", "All user restrictions cleared")

                    // Step 2: Remove Device Owner status
                    // After this the app behaves like a regular Device Admin
                    devicePolicyManager.clearDeviceOwnerApp(context.packageName)
                    Log.d("LOCK_MANAGER", "Device Owner removed")
                }
            }

            // Step 3: Remove Device Admin (now anyone can uninstall the app)
            if (isAdminActive()) {
                devicePolicyManager.removeActiveAdmin(adminComponent)
                Log.d("LOCK_MANAGER", "Device Admin removed — app can now be uninstalled")
            }

            // Step 4: Clear customer flag from SharedPrefs
            val prefs = context.getSharedPreferences("PKLockerPrefs", android.content.Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("is_customer", false)
                .putBoolean("is_locked", false)
                .putBoolean("settings_blocked", false)
                .putBoolean("auto_lock_enabled", false)
                .putStringSet("blocked_apps", emptySet())
                .commit()

            Log.d("LOCK_MANAGER", "selfDeactivate() complete — device fully released")
        } catch (e: Exception) {
            Log.e("LOCK_MANAGER", "selfDeactivate() error: ${e.message}")
        }
    }
}
