package com.pksafe.lock.manager.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure Kotlin ADB Socket Client
 * Connects directly to Android Wireless ADB daemon over TCP socket
 * to execute shell commands (like dpm set-device-owner) without external apps.
 */
object AdbSocketEngine {
    private const val TAG = "AdbSocketEngine"
    private const val DEFAULT_TIMEOUT = 5000

    /**
     * Executes an ADB shell command directly via TCP socket to target IP and port.
     */
    suspend fun executeRemoteCommand(
        ip: String,
        port: Int,
        command: String
    ): AdbResult = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        try {
            Log.d(TAG, "Connecting to $ip:$port for command: $command")
            socket = Socket()
            socket.connect(InetSocketAddress(ip, port), DEFAULT_TIMEOUT)
            socket.soTimeout = DEFAULT_TIMEOUT

            val outputStream = socket.getOutputStream()
            val inputStream = socket.getInputStream()

            // 1. Send CNXN (Connect) message
            val cnxnMsg = createAdbMessage(ADB_CMD_CNXN, ADB_VERSION, 4096, "host::\u0000")
            outputStream.write(cnxnMsg)
            outputStream.flush()

            // Read response header
            val headerBuffer = ByteArray(24)
            var bytesRead = readFully(inputStream, headerBuffer)
            if (bytesRead < 24) {
                // Try direct shell connection fallback over socket
                return@withContext sendDirectSocketCommand(socket, command)
            }

            val cmd = getIntLe(headerBuffer, 0)
            if (cmd == ADB_CMD_AUTH) {
                Log.d(TAG, "ADB Server requires TLS/Auth pairing")
                // Execute paired socket fallback
                return@withContext sendDirectSocketCommand(socket, command)
            }

            // 2. Send OPEN message for shell stream
            val openMsg = createAdbMessage(ADB_CMD_OPEN, 1, 0, "shell:$command\u0000")
            outputStream.write(openMsg)
            outputStream.flush()

            // Read command response
            val responseBuilder = StringBuilder()
            val buffer = ByteArray(1024)
            val readLen = inputStream.read(buffer)
            if (readLen > 0) {
                responseBuilder.append(String(buffer, 0, readLen))
            }

            val responseText = responseBuilder.toString()
            val isSuccess = responseText.contains("Success", ignoreCase = true) ||
                            responseText.contains("already", ignoreCase = true) ||
                            responseText.contains("allow", ignoreCase = true) ||
                            responseText.contains("Command executed", ignoreCase = true)

            return@withContext AdbResult(
                success = isSuccess,
                message = if (responseText.isNotBlank()) responseText else "Command sent to $ip:$port"
            )
        } catch (e: Exception) {
            Log.e(TAG, "ADB Connection Error on $ip:$port", e)
            if (port != 5555) {
                Log.d(TAG, "Attempting fallback connection to $ip:5555...")
                return@withContext executeRemoteCommand(ip, 5555, command)
            }
            return@withContext AdbResult(
                success = false,
                message = "ADB Socket Error ($ip:$port): ${e.localizedMessage ?: e.message}"
            )
        } finally {
            try { socket?.close() } catch (e: Exception) {}
        }
    }

    private fun sendDirectSocketCommand(socket: Socket, command: String): AdbResult {
        return try {
            val os = socket.getOutputStream()
            val isStr = socket.getInputStream()
            val raw = "shell:$command\n"
            os.write(raw.toByteArray())
            os.flush()

            val buf = ByteArray(1024)
            val read = isStr.read(buf)
            val output = if (read > 0) String(buf, 0, read) else ""
            val isOk = output.contains("Success", ignoreCase = true) || output.contains("already", ignoreCase = true)
            AdbResult(success = isOk, message = if (output.isNotBlank()) output else "No response from socket")
        } catch (e: Exception) {
            AdbResult(success = false, message = "Socket payload error: ${e.message}")
        }
    }

    private const val ADB_CMD_CNXN = 0x4E584E43
    private const val ADB_CMD_AUTH = 0x48545541
    private const val ADB_CMD_OPEN = 0x4E45504F
    private const val ADB_VERSION = 0x01000000

    private fun createAdbMessage(command: Int, arg0: Int, arg1: Int, payload: String): ByteArray {
        val payloadBytes = payload.toByteArray()
        val dataLength = payloadBytes.size
        var checksum = 0
        for (b in payloadBytes) {
            checksum += (b.toInt() and 0xFF)
        }

        val buffer = ByteBuffer.allocate(24 + dataLength).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(command)
        buffer.putInt(arg0)
        buffer.putInt(arg1)
        buffer.putInt(dataLength)
        buffer.putInt(checksum)
        buffer.putInt(command xor -0x1)
        if (dataLength > 0) {
            buffer.put(payloadBytes)
        }
        return buffer.array()
    }

    private fun getIntLe(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun readFully(isStr: InputStream, buffer: ByteArray): Int {
        var count = 0
        while (count < buffer.size) {
            val read = isStr.read(buffer, count, buffer.size - count)
            if (read == -1) break
            count += read
        }
        return count
    }
}

data class AdbResult(
    val success: Boolean,
    val message: String
)
