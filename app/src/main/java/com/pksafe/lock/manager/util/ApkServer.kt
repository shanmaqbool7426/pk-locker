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
 *
 * Uses dynamic port fallback: tries the requested port first, then
 * scans for an available port if it is in use.
 */
class ApkServer(
    private val context: Context,
    port: Int
) : NanoHTTPD(port) {

    private var apkFile: File? = null
    val actualPort: Int = port

    companion object {
        private const val TAG = "ApkServer"
        private const val DEFAULT_PORT = 8080
        private const val MAX_PORT_ATTEMPTS = 20
        private var instance: ApkServer? = null

        /** Timestamp of the most recent APK download request, or 0 if none yet. */
        @Volatile
        var lastApkRequestTime: Long = 0L
            private set

        /** Total bytes served so far (approximate). */
        @Volatile
        var totalBytesServed: Long = 0L
            private set

        /**
         * Starts the server. If [preferredPort] is in use, tries nearby ports.
         * Returns the running server with [ApkServer.actualPort] set.
         */
        fun start(context: Context, preferredPort: Int = DEFAULT_PORT): ApkServer {
            stop() // Stop any existing instance
            val appContext = context.applicationContext
            var server: ApkServer? = null
            var lastError: Exception? = null

            for (offset in 0 until MAX_PORT_ATTEMPTS) {
                val port = preferredPort + offset
                try {
                    server = ApkServer(appContext, port)
                    server.prepareApk()
                    server.start()
                    instance = server
                    Log.i(TAG, "APK Server started on port $port")
                    return server
                } catch (e: Exception) {
                    lastError = e
                    Log.w(TAG, "Port $port failed: ${e.message}")
                }
            }

            throw IllegalStateException(
                "Could not start APK server after $MAX_PORT_ATTEMPTS ports. Last error: ${lastError?.message}",
                lastError
            )
        }

        fun stop() {
            instance?.let {
                try {
                    it.stop()
                    Log.i(TAG, "APK Server stopped")
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping server: ${e.message}")
                }
            }
            instance = null
        }

        fun isRunning(): Boolean = instance?.isAlive == true

        fun getActualPort(): Int = instance?.actualPort ?: DEFAULT_PORT
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
        val method = session.method.name
        Log.i(TAG, "Request: $method $uri")

        // CORS / download friendly headers for every response
        fun Response.withCommonHeaders(): Response {
            addHeader("Access-Control-Allow-Origin", "*")
            addHeader("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
            addHeader("Access-Control-Allow-Headers", "*")
            return this
        }

        // OPTIONS preflight
        if (method == "OPTIONS") {
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "OK").withCommonHeaders()
        }

        // Serve the APK file
        if (uri == "/pklocker.apk" || uri == "/" || uri == "/app.apk") {
            val file = apkFile
            if (file != null && file.exists()) {
                // Track that a download started so the QR screen can show feedback
                lastApkRequestTime = System.currentTimeMillis()
                totalBytesServed += file.length()
                val fis = FileInputStream(file)
                val response = newFixedLengthResponse(
                    Response.Status.OK,
                    "application/vnd.android.package-archive",
                    fis,
                    file.length()
                )
                response.addHeader("Content-Disposition", "attachment; filename=\"pklocker.apk\"")
                return response.withCommonHeaders()
            }
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "APK not found")
                .withCommonHeaders()
        }

        // Health check endpoint
        if (uri == "/status") {
            val size = apkFile?.length() ?: 0
            return newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                """{"status":"ok","apk_size":$size,"port":$actualPort}"""
            ).withCommonHeaders()
        }

        return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "PKLocker APK Server Running")
            .withCommonHeaders()
    }
}
