package com.pksafe.lock.manager.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * AutoUpdater checks for application updates against the admin-configured update endpoint.
 *
 * Logic Flow:
 * 1. Fetches update config from /api/config/update (same endpoint admin manages).
 * 2. Checks isUpdateEnabled flag — if admin disabled updates, stops here.
 * 3. Compares serverVersionCode > currentVersionCode.
 * 4. Downloads the updated APK payload asynchronously to local app cache.
 * 5. Initializes a PackageInstaller session to write the APK stream.
 * 6. Commits the session with a PendingIntent bound to UpdateReceiver.
 * 7. On completion, UpdateReceiver displays an update confirmation popup (if non-Device Owner)
 *    or completes silent installation (if Device Owner).
 */
class AutoUpdater(private val context: Context) {
    private val TAG = "AutoUpdater"
    private val UPDATE_API = "${Constants.BASE_URL}config/update"
    private val PREFS_NAME = "PKLockerPrefs"

    /**
     * Result of an update check — used by UI to show update dialogs.
     */
    data class UpdateCheckResult(
        val updateAvailable: Boolean,
        val isForceUpdate: Boolean,
        val versionCode: Int = 0,
        val versionName: String = "",
        val downloadUrl: String = "",
        val title: String = "",
        val message: String = ""
    )

    /**
     * Checks for available updates WITHOUT downloading.
     * UI uses the result to show a confirmation dialog before installing.
     */
    suspend fun checkForUpdates(): UpdateCheckResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Checking for updates at $UPDATE_API...")
                val url = URL(UPDATE_API)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "Update check failed: ${connection.responseCode}")
                    return@withContext UpdateCheckResult(false, false)
                }

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)

                if (!json.optBoolean("success", false)) {
                    return@withContext UpdateCheckResult(false, false)
                }

                // Parse nested data object from admin config
                val data = json.optJSONObject("data") ?: return@withContext UpdateCheckResult(false, false)

                val isUpdateEnabled = data.optBoolean("isUpdateEnabled", false)
                if (!isUpdateEnabled) {
                    Log.d(TAG, "Update service is disabled by admin. Skipping.")
                    return@withContext UpdateCheckResult(false, false)
                }

                val serverVersionCode = data.optInt("versionCode", 0)
                val downloadUrl = data.optString("updateUrl", "")
                val versionName = data.optString("versionName", "")
                val title = data.optString("updateTitle", "Update Available")
                val message = data.optString("updateMessage", "")
                val isForceUpdate = data.optBoolean("isForceUpdate", false)

                val currentVersionCode = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionCode
                } catch (e: Exception) {
                    1
                }

                Log.d(TAG, "Server Version: $serverVersionCode | Current App Version: $currentVersionCode")

                if (serverVersionCode > currentVersionCode && downloadUrl.isNotEmpty()) {
                    // ── PER-USER UPDATE STATE (exactly-once per pushed version) ──
                    // Admin's push = global state ON (server versionCode). This
                    // device consumes the state when (a) the popup has been SHOWN
                    // to the logged-in user (update_seen_version) or (b) the user
                    // tapped UPDATE (update_triggered_version). Whichever is
                    // higher >= server versionCode means this user's state is
                    // FALSE — no popup until the admin pushes a newer version.
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val triggeredVersion = prefs.getInt("update_triggered_version", 0)
                    val seenVersion = prefs.getInt("update_seen_version", 0)
                    val consumedVersion = maxOf(triggeredVersion, seenVersion)
                    if (consumedVersion >= serverVersionCode) {
                        Log.d(TAG, "Update vCode $serverVersionCode already consumed (triggered=$triggeredVersion, seen=$seenVersion). Skipping.")
                        return@withContext UpdateCheckResult(false, false)
                    }

                    return@withContext UpdateCheckResult(
                        updateAvailable = true,
                        isForceUpdate = isForceUpdate,
                        versionCode = serverVersionCode,
                        versionName = versionName,
                        downloadUrl = downloadUrl,
                        title = title,
                        message = message
                    )
                } else {
                    Log.d(TAG, "App is already up to date. No action needed.")
                    return@withContext UpdateCheckResult(false, false)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Update check error: ${e.message}")
                return@withContext UpdateCheckResult(false, false)
            }
        }
    }

    /**
     * Records that the user has committed to updating for this versionCode.
     * Once called, checkForUpdates() will return updateAvailable=false for
     * this versionCode (or lower) on all future app launches — preventing
     * the repeat-popup problem after update + restart + re-login.
     */
    fun markUpdateTriggered(versionCode: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt("update_triggered_version", versionCode).apply()
        Log.d(TAG, "Marked update vCode $versionCode as triggered — popup blocked until newer version.")
    }

    /**
     * Flips this user's update state to FALSE. Called right AFTER the update
     * popup is shown to a logged-in shopkeeper/dealer: the state is consumed,
     * so the same popup never appears again on this device until the admin
     * pushes a strictly newer versionCode.
     */
    fun markUpdateSeen(versionCode: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt("update_seen_version", versionCode).apply()
        Log.d(TAG, "Update vCode $versionCode marked as SEEN — user state set to false.")
    }

    /**
     * Downloads the APK from the given URL and installs silently.
     * Sets a SharedPreferences flag so the app logs out after the update restarts it.
     */
    suspend fun downloadAndInstall(downloadUrl: String) {
        withContext(Dispatchers.IO) {
            try {
                // Set logout flag BEFORE installing — app will restart after update
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putBoolean("pending_update_logout", true).apply()

                Log.d(TAG, "Downloading update from $downloadUrl")
                val url = URL(downloadUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()

                val apkFile = File(context.cacheDir, "update.apk")
                if (apkFile.exists()) apkFile.delete()

                val inputStream = connection.inputStream
                val outputStream = FileOutputStream(apkFile)

                val buffer = ByteArray(1024)
                var len: Int
                while (inputStream.read(buffer).also { len = it } != -1) {
                    outputStream.write(buffer, 0, len)
                }
                outputStream.flush()
                outputStream.close()
                inputStream.close()

                Log.d(TAG, "Download complete. Starting silent install...")
                installSilently(apkFile)
            } catch (e: Exception) {
                Log.e(TAG, "Download/install error: ${e.message}")
            }
        }
    }

    private fun installSilently(apkFile: File) {
        try {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(context.packageName)
            
            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            val out = session.openWrite("PKLockerUpdate", 0, apkFile.length())
            val inputStream = FileInputStream(apkFile)

            val buffer = ByteArray(65536)
            var c: Int
            while (inputStream.read(buffer).also { c = it } != -1) {
                out.write(buffer, 0, c)
            }
            session.fsync(out)
            inputStream.close()
            out.close()

            Log.d(TAG, "Committing installation session...")
            
            // Receiver for install result
            val intent = Intent(context, UpdateReceiver::class.java)
            intent.action = "com.pksafe.lock.manager.UPDATE_STATUS"
            
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, flags)

            session.commit(pendingIntent.intentSender)
            Log.d(TAG, "Session committed! OS is now installing the update in the background.")

        } catch (e: Exception) {
            Log.e(TAG, "Install error: ${e.message}")
        }
    }
}
