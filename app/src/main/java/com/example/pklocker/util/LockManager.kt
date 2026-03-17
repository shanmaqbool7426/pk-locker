package com.example.pklocker.util

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
import com.example.pklocker.receiver.AdminReceiver
import com.example.pklocker.service.LockService

class LockManager(private val context: Context) {

    private val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(context, AdminReceiver::class.java)

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
                    // Block USB File Transfer (PC se connect kar k data ya hack nahi kar sakay ga)
                    setUserRestriction(UserManager.DISALLOW_USB_FILE_TRANSFER, locked)
                    // Block Factory Reset
                    setUserRestriction(UserManager.DISALLOW_FACTORY_RESET, locked)
                    // Block Safe Mode (Newer Androids)
                    setUserRestriction(UserManager.DISALLOW_SAFE_BOOT, locked)
                }
            }
            Log.d("LOCK_MANAGER", "Hardware Restrictions Applied: $locked")
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
        if (!isAdminActive() || !isDeviceOwner()) return
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
        if (!isAdminActive()) return
        try {
            devicePolicyManager.setCameraDisabled(adminComponent, disabled)
            Log.d("LOCK_MANAGER", "Camera Disabled: $disabled")
        } catch (e: Exception) {
            Log.e("LOCK_MANAGER", "Camera block error: ${e.message}")
        }
    }
}
