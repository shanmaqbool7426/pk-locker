package com.pksafe.lock.manager.receiver

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * MANDATORY for Android 12+ QR Device Owner provisioning.
 *
 * After provisioning mode is selected, Android 12+ Setup Wizard calls this Activity:
 * "Enforce your admin policies now, and tell us when complete."
 *
 * We respond RESULT_OK to inform Android Setup Wizard that compliance is complete.
 */
class AdminPolicyComplianceActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("POLICY_COMPLIANCE", "=== AdminPolicyComplianceActivity called ===")
        Log.d("POLICY_COMPLIANCE", "Intent action: ${intent?.action}")

        // Save provisioning complete state
        getSharedPreferences("PKLockerPrefs", MODE_PRIVATE)
            .edit()
            .putBoolean("provisioning_complete", true)
            .putBoolean("is_customer", true)
            .putString("provisioning_method", "qr")
            .apply()

        Log.d("POLICY_COMPLIANCE", "Policy compliance completed, returning RESULT_OK")
        setResult(RESULT_OK)
        finish()
    }
}
