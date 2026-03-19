package com.example.pklocker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.SmsMessage
import android.util.Log
import com.example.pklocker.service.LockService
import com.example.pklocker.util.LockManager
import java.security.MessageDigest

/**
 * PKLocker SMS Receiver
 * ─────────────────────────────────────────────────────────────
 * Offline SMS locking system — works WITHOUT internet.
 *
 * Shopkeeper sends SMS to customer's phone:
 *   LOCK#<lockCode>    → locks the device
 *   UNLOCK#<unlockCode> → unlocks the device
 *
 * Codes are SHA-256 of: "LOCK_{imei}" / "UNLOCK_{imei}"
 * Same algorithm as the backend — deterministic, no internet needed.
 * ─────────────────────────────────────────────────────────────
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PKL_SMS"
        private const val PREFS = "PKLockerPrefs"

        // Generate SHA-256 code — must match backend logic in device.js
        fun generateSmsCode(prefix: String, imei: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val input = "${prefix}_$imei"
            return digest.digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        // Only act on customer devices
        val isCustomer = prefs.getBoolean("is_customer", false)
        if (!isCustomer) {
            Log.d(TAG, "Not a customer device — ignoring SMS")
            return
        }

        val messages = extractSmsMessages(intent) ?: return

        for (sms in messages) {
            val body = sms.messageBody?.trim() ?: continue
            val upperBody = body.uppercase()

            Log.d(TAG, "SMS received (first 30 chars): ${body.take(30)}")

            // Get IMEI from prefs (saved during provisioning)
            val imei = prefs.getString("device_imei", null)
            if (imei.isNullOrBlank()) {
                Log.e(TAG, "device_imei not found in prefs — SMS lock cannot verify code")
                return
            }

            // Get codes: prefer saved codes, fallback to generating from IMEI
            val expectedLockCode   = prefs.getString("sms_lock_code", null)
                ?: generateSmsCode("LOCK", imei)
            val expectedUnlockCode = prefs.getString("sms_unlock_code", null)
                ?: generateSmsCode("UNLOCK", imei)

            when {
                // ── LOCK ──────────────────────────────────────────────────
                upperBody.startsWith("LOCK#") -> {
                    val receivedCode = body.substringAfter("#").trim()
                    if (receivedCode == expectedLockCode) {
                        Log.d(TAG, "✅ Valid LOCK code — locking device")
                        abortBroadcast() // Hide SMS from default SMS app

                        prefs.edit().putBoolean("is_locked", true).commit()

                        // Start LockService (overlay screen) gracefully
                        try {
                            val svcIntent = Intent(context, LockService::class.java)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(svcIntent)
                            } else {
                                context.startService(svcIntent)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to start LockService in background: ${e.message}")
                            // We still have DPM lock triggering next, so device gets locked anyway!
                        }

                        // Also call DPM lock
                        Handler(Looper.getMainLooper()).postDelayed({
                            try { LockManager(context).lockDevice() } catch (e: Exception) {
                                Log.e(TAG, "DPM lock error: ${e.message}")
                            }
                        }, 800)
                    } else {
                        Log.w(TAG, "❌ Invalid LOCK code")
                    }
                }

                // ── UNLOCK ────────────────────────────────────────────────
                upperBody.startsWith("UNLOCK#") -> {
                    val receivedCode = body.substringAfter("#").trim()
                    if (receivedCode == expectedUnlockCode) {
                        Log.d(TAG, "✅ Valid UNLOCK code — unlocking device")
                        abortBroadcast()

                        prefs.edit().putBoolean("is_locked", false).commit()
                        context.stopService(Intent(context, LockService::class.java))

                        Handler(Looper.getMainLooper()).post {
                            try { LockManager(context).unlockDevice() } catch (e: Exception) {
                                Log.e(TAG, "DPM unlock error: ${e.message}")
                            }
                        }
                    } else {
                        Log.w(TAG, "❌ Invalid UNLOCK code")
                    }
                }

                else -> {
                    Log.d(TAG, "SMS does not match PKLocker format — ignoring")
                }
            }
        }
    }

    private fun extractSmsMessages(intent: Intent): Array<SmsMessage>? {
        return try {
            val bundle: Bundle = intent.extras ?: return null
            val pdus = bundle.get("pdus") as? Array<*> ?: return null
            val format = bundle.getString("format")
            pdus.mapNotNull { pdu ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    SmsMessage.createFromPdu(pdu as ByteArray, format)
                } else {
                    @Suppress("DEPRECATION")
                    SmsMessage.createFromPdu(pdu as ByteArray)
                }
            }.toTypedArray()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract SMS: ${e.message}")
            null
        }
    }
}
