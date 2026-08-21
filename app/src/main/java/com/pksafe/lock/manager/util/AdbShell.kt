package com.pksafe.lock.manager.util

import android.content.Context
import io.github.muntashirakon.adb.AdbInputStream
import io.github.muntashirakon.adb.AdbStream
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Executes shell commands on the connected ADB device.
 * Uses the muntashirakon ADB library for proper TLS-based communication.
 */
class AdbShell(private val context: Context) {

    /**
     * Runs a shell command on the connected ADB device and returns the output.
     * @param command The shell command to execute
     * @return The command output as a string
     * @throws Exception if not connected or command fails
     */
    @Throws(Exception::class)
    fun run(command: String): String {
        val connectionManager = AdbConnectionManager.getInstance(context)
        if (!connectionManager.isConnected) {
            throw IllegalStateException("Not connected to target device ADB")
        }

        var stream: AdbStream? = null
        try {
            stream = connectionManager.openStream("shell:" + command + ") 2>&1; echo '---CMD_END---'")
            val sb = StringBuilder()
            val inputStream = stream.openInputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val read = inputStream.read(buffer)
                if (read == -1) break
                sb.append(String(buffer, 0, read, StandardCharsets.UTF_8))
                if (sb.indexOf("---CMD_END---") >= 0) break
            }
            return sb.toString()
                .replace("---CMD_END---\n", "")
                .replace("---CMD_END---", "")
                .trim()
        } finally {
            stream?.let {
                try {
                    it.close()
                } catch (e: IOException) {
                    // Ignore
                }
            }
        }
    }
}
