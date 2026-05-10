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

class AutoUpdater(private val context: Context) {
    private val TAG = "AutoUpdater"
    private val VERSION_API = "${Constants.BASE_URL}version"

    suspend fun checkForUpdatesAndInstall() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Checking for updates at $VERSION_API...")
                // 1. Fetch version info
                val url = URL(VERSION_API)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "Version check failed: ${connection.responseCode}")
                    return@withContext
                }

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)

                if (!json.optBoolean("success", false)) return@withContext

                val serverVersionCode = json.optInt("versionCode", 0)
                val downloadUrl = json.optString("downloadUrl", "")

                val currentVersionCode = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionCode
                } catch (e: Exception) {
                    1 // Default
                }

                Log.d(TAG, "Server Version: $serverVersionCode | Current App Version: $currentVersionCode")

                // 2. Compare versions
                if (serverVersionCode > currentVersionCode && downloadUrl.isNotEmpty()) {
                    Log.d(TAG, "New version found! Downloading silently from $downloadUrl")
                    downloadAndInstall(downloadUrl)
                } else {
                    Log.d(TAG, "App is already up to date. No action needed.")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Update check error: ${e.message}")
            }
        }
    }

    private fun downloadAndInstall(apkUrl: String) {
        try {
            val url = URL(apkUrl)
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

            Log.d(TAG, "Download complete. Starting silent install using Device Owner privileges...")
            installSilently(apkFile)

        } catch (e: Exception) {
            Log.e(TAG, "Download error: ${e.message}")
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
