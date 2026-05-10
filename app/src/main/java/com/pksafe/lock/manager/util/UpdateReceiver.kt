package com.pksafe.lock.manager.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log

class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.pksafe.lock.manager.UPDATE_STATUS") {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

            when (status) {
                PackageInstaller.STATUS_SUCCESS -> {
                    Log.d("UpdateReceiver", "✅ App updated successfully!")
                }
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    // For Shopkeepers (Not Device Owner), system asks for confirmation
                    Log.d("UpdateReceiver", "Prompting user for update confirmation...")
                    val confirmationIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    if (confirmationIntent != null) {
                        confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(confirmationIntent)
                    }
                }
                PackageInstaller.STATUS_FAILURE_ABORTED -> {
                    Log.w("UpdateReceiver", "⚠️ Update aborted by user/system. $message")
                }
                else -> {
                    Log.e("UpdateReceiver", "❌ Update failed! Status: $status, Message: $message")
                }
            }
        }
    }
}
