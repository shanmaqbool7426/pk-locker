package com.pksafe.lock.manager.util

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream

/**
 * Lightweight HTTP server that runs on the Shopkeeper's phone.
 * Serves the app's own APK so the target phone can download it
 * during QR provisioning — no laptop or external server needed.
 */
class ApkServer(
    private val context: Context,
    port: Int = 8080
) : NanoHTTPD(port) {

    private var apkFile: File? = null

    companion object {
        private const val TAG = "ApkServer"
        private var instance: ApkServer? = null

        fun start(context: Context, port: Int = 8080): ApkServer {
            stop() // Stop any existing instance
            val server = ApkServer(context.applicationContext, port)
            server.prepareApk()
            server.start()
            instance = server
            Log.i(TAG, "APK Server started on port $port")
            return server
        }

        fun stop() {
            instance?.let {
                it.stop()
                Log.i(TAG, "APK Server stopped")
            }
            instance = null
        }

        fun isRunning(): Boolean = instance?.isAlive == true
    }

    /**
     * Copy the currently installed APK to a serveable location.
     * This ensures the EXACT same APK (same signature) is served.
     */
    private fun prepareApk() {
        try {
            val sourceApk = File(context.applicationInfo.sourceDir)
            val serveDir = File(context.cacheDir, "apk_serve")
            serveDir.mkdirs()
            apkFile = File(serveDir, "pklocker.apk")
            sourceApk.copyTo(apkFile!!, overwrite = true)
            Log.i(TAG, "APK prepared: ${apkFile!!.absolutePath} (${apkFile!!.length()} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare APK", e)
        }
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        Log.i(TAG, "Request: $uri")

        // Serve the APK file
        if (uri == "/pklocker.apk" || uri == "/" || uri == "/app.apk") {
            val file = apkFile
            if (file != null && file.exists()) {
                val fis = FileInputStream(file)
                return newFixedLengthResponse(
                    Response.Status.OK,
                    "application/vnd.android.package-archive",
                    fis,
                    file.length()
                )
            }
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "APK not found")
        }

        // Health check endpoint
        if (uri == "/status") {
            val size = apkFile?.length() ?: 0
            return newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                """{"status":"ok","apk_size":$size}"""
            )
        }

        return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "PKLocker APK Server Running")
    }
}
