package com.pksafe.lock.manager.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log

/**
 * BroadcastReceiver responsible for handling installation status callbacks from PackageInstaller.
 * 
 * When an app update installation session is committed (via AutoUpdater), the Android OS
 * broadcasts the status to this receiver:
 * - If user interaction is required (e.g. non-Device Owner mode), STATUS_PENDING_USER_ACTION triggers
 *   the system installation confirmation popup dialog for the user.
 * - If Device Owner mode is active, the update completes seamlessly in the background with STATUS_SUCCESS.
 */
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
                    // Triggers the system update confirmation dialog / popup for non-Device Owner devices
                    Log.d("UpdateReceiver", "Prompting user with update confirmation popup...")
                    val confirmationIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    if (confirmationIntent != null) {
                        confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(confirmationIntent)
                    }
                }
                PackageInstaller.STATUS_FAILURE_ABORTED -> {
                    Log.w("UpdateReceiver", "⚠️ Update aborted by user or system. $message")
                }
                else -> {
                    Log.e("UpdateReceiver", "❌ Update failed! Status: $status, Message: $message")
                }
            }
        }
    }
}
