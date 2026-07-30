package com.pksafe.lock.manager.receiver

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * MANDATORY for Android 12+ QR Device Owner provisioning.
 *
 * Android Setup Wizard calls this Activity during provisioning to ask our DPC:
 * "What mode do you want? Fully Managed Device or Work Profile?"
 *
 * We MUST respond with PROVISIONING_MODE_FULLY_MANAGED_DEVICE (1)
 * otherwise Samsung Knox shows "Something went wrong".
 */
class GetProvisioningModeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("PROVISIONING_MODE", "=== GetProvisioningModeActivity called ===")
        Log.d("PROVISIONING_MODE", "Intent action: ${intent?.action}")

        // Respond: We want Fully Managed Device (Device Owner) mode
        val resultIntent = Intent().apply {
            putExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_MODE,
                DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE
            )
        }

        Log.d("PROVISIONING_MODE", "Responding with PROVISIONING_MODE_FULLY_MANAGED_DEVICE")
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}
