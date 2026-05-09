package com.pksafe.lock.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.SmsMessage
import android.util.Log
import com.pksafe.lock.manager.service.LockService
import com.pksafe.lock.manager.util.LockManager
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

            // Get both IMEIs from prefs (saved during provisioning)
            val imei1 = prefs.getString("device_imei", null)
            val imei2 = prefs.getString("device_imei2", null)
            
            if (imei1.isNullOrBlank() && imei2.isNullOrBlank()) {
                Log.e(TAG, "No IMEI found in prefs — cannot verify SMS protocol")
                return
            }

            // Collect all possible valid codes for this device
            val validLockCodes = mutableSetOf<String>()
            val validUnlockCodes = mutableSetOf<String>()

            // 1. Add codes from prefs (if backend sent them) — force lowercase
            prefs.getString("sms_lock_code", null)?.lowercase()?.let { validLockCodes.add(it) }
            prefs.getString("sms_unlock_code", null)?.lowercase()?.let { validUnlockCodes.add(it) }

            // 2. Generate codes from both IMEIs as fallback
            imei1?.let {
                validLockCodes.add(generateSmsCode("LOCK", it))
                validUnlockCodes.add(generateSmsCode("UNLOCK", it))
            }
            imei2?.let {
                validLockCodes.add(generateSmsCode("LOCK", it))
                validUnlockCodes.add(generateSmsCode("UNLOCK", it))
            }

            Log.d(TAG, "Expecting Lock Codes: $validLockCodes")
            Log.d(TAG, "Expecting Unlock Codes: $validUnlockCodes")

            when {
                // ── LOCK ──────────────────────────────────────────────────
                upperBody.startsWith("LOCK#") -> {
                    val receivedCode = body.substringAfter("#").trim().lowercase()
                    Log.d(TAG, "Received LOCK attempt with code: $receivedCode")
                    
                    if (validLockCodes.contains(receivedCode)) {
                        Log.d(TAG, "✅ Valid LOCK code matched")
                        abortBroadcast() // Hide SMS from default SMS app

                        prefs.edit().putBoolean("is_locked", true).commit()

                        // Call LockManager directly (it handles starting LockService and hardware lock)
                        try { 
                            LockManager(context).lockDevice() 
                        } catch (e: Exception) {
                            Log.e(TAG, "DPM lock error: ${e.message}")
                        }
                    } else {
                        Log.w(TAG, "❌ Invalid LOCK code")
                    }
                }

                // ── UNLOCK ────────────────────────────────────────────────
                upperBody.startsWith("UNLOCK#") -> {
                    val receivedCode = body.substringAfter("#").trim().lowercase()
                    Log.d(TAG, "Received UNLOCK attempt with code: $receivedCode")
                    
                    if (validUnlockCodes.contains(receivedCode)) {
                        Log.d(TAG, "✅ Valid UNLOCK code matched")
                        abortBroadcast()

                        prefs.edit().putBoolean("is_locked", false).commit()

                        try { 
                            LockManager(context).unlockDevice() 
                        } catch (e: Exception) {
                            Log.e(TAG, "DPM unlock error: ${e.message}")
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
