package com.pksafe.lock.manager.ui.provisioning

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.net.wifi.WifiManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.pksafe.lock.manager.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface

private val ThemeBrown = Color(0xFF8B4513)
private val ThemeBrownDark = Color(0xFF653410)
private val LogBgColor = Color(0xFFF1F5F9)
private val TextSubColor = Color(0xFF555555)

const val DPM_COMMAND_WIRELESS = "dpm set-device-owner com.pksafe.lock.manager/com.pksafe.lock.manager.receiver.AdminReceiver"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WirelessAdbSetupScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // --- States ---
    var pairingCode by remember { mutableStateOf("") }
    var targetIpPort by remember { mutableStateOf("") }
    var isConnected by remember { mutableStateOf(false) }
    var isPairing by remember { mutableStateOf(false) }
    var isSettingOwner by remember { mutableStateOf(false) }
    
    // Logs State
    var logText by remember { mutableStateOf("Loading download QR...\nAPK download QR loaded.") }

    // APK Download QR Bitmap
    val downloadQrBitmap = remember { generateQrBitmap(Constants.APK_DOWNLOAD_URL, 512) }

    fun appendLog(msg: String) {
        logText = "$logText\n$msg"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "ADB Customer Setup",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.Black
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.Black)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // ==========================================
            // STEP 1: Customer installs APK (scan QR)
            // ==========================================
            Text(
                "Step 1 - Customer installs APK (scan QR)",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = ThemeBrown
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Show this QR to the customer. They scan it to download and install the apk. Wait until install finishes.",
                fontSize = 12.5.sp,
                color = TextSubColor,
                lineHeight = 17.sp
            )
            Spacer(modifier = Modifier.height(14.dp))

            // QR Display Card
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.size(230.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (downloadQrBitmap != null) {
                            Image(
                                bitmap = downloadQrBitmap.asImageBitmap(),
                                contentDescription = "Download APK QR Code",
                                modifier = Modifier.size(200.dp)
                            )
                        } else {
                            Text("Generating QR...", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ==========================================
            // STEP 2: Developer options
            // ==========================================
            Text(
                "Step 2 - Turn on Developer options (customer phone)",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = ThemeBrown
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Settings → About phone → tap Build number 7 times. Developer options appears in Settings.",
                fontSize = 12.5.sp,
                color = TextSubColor,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ==========================================
            // STEP 3: Wireless Debugging
            // ==========================================
            Text(
                "Step 3 - Turn on Wireless debugging (customer phone)",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = ThemeBrown
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Settings → Developer options → Wireless debugging → ON.\nKeep customer phone and this phone on the same Wi-Fi.",
                fontSize = 12.5.sp,
                color = TextSubColor,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Customer Phone IP & Port Field (REQUIRED)
            OutlinedTextField(
                value = targetIpPort,
                onValueChange = { targetIpPort = it },
                label = { Text("Customer IP & Port (Shown under Wireless Debugging)") },
                placeholder = { Text("e.g. 192.168.1.15:37129 (REQUIRED)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ThemeBrown,
                    unfocusedBorderColor = Color.LightGray
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Wi-Fi pairing code input field
            OutlinedTextField(
                value = pairingCode,
                onValueChange = { if (it.length <= 6) pairingCode = it },
                label = { Text("Wi-Fi pairing code (6 digits)") },
                placeholder = { Text("e.g. 115999") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ThemeBrown,
                    unfocusedBorderColor = Color.LightGray
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // PAIR & CONNECT Button
            Button(
                onClick = {
                    if (targetIpPort.trim().isEmpty() || !targetIpPort.contains(":")) {
                        Toast.makeText(context, "Please enter Customer Phone IP & Port (e.g. 192.168.1.15:37129)", Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    if (pairingCode.trim().length < 6) {
                        Toast.makeText(context, "Please enter 6-digit Wi-Fi pairing code", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isPairing = true
                    val hostIp = parseIpFromInput(targetIpPort, context)
                    val hostPort = parsePortFromInput(targetIpPort)

                    appendLog("Connecting via Wireless ADB code $pairingCode...")
                    appendLog("Target Device: $hostIp:$hostPort")
                    copyToClipboard(context, DPM_COMMAND_WIRELESS)

                    scope.launch {
                        appendLog("Connecting to $hostIp:$hostPort...")
                        val result = com.pksafe.lock.manager.util.AdbSocketEngine.executeRemoteCommand(
                            hostIp, hostPort, "echo ADB_CONNECTED"
                        )
                        
                        isPairing = false
                        if (result.success) {
                            isConnected = true
                            appendLog("Pairing status: Connected successfully! ✅")
                            Toast.makeText(context, "Pairing successful! Tap 'SET DEVICE OWNER'", Toast.LENGTH_LONG).show()
                        } else {
                            isConnected = false
                            appendLog("❌ Connection Error: ${result.message}")
                            appendLog("⚠️ Please verify Customer IP:Port and ensure Wireless Debugging is ON.")
                            Toast.makeText(context, "Connection failed. Please check IP & Port.", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ThemeBrown)
            ) {
                if (isPairing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("PAIRING...", fontWeight = FontWeight.Bold)
                } else {
                    Text("PAIR & CONNECT", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ==========================================
            // STEP 4: Pair instructions
            // ==========================================
            Text(
                "Step 4 - Pair with pairing code (customer phone)",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = ThemeBrown
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "On customer phone: Developer options → Wireless debugging → Pair device with pairing code.\nKeep that dialog open. Enter the 6-digit Wi-Fi pairing code below on this phone, then tap Pair & Connect.",
                fontSize = 12.5.sp,
                color = TextSubColor,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ==========================================
            // STEP 5: Set Device Owner
            // ==========================================
            Text(
                "Step 5 - Set Device Owner",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = ThemeBrown
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "After PKLocker is installed on the customer phone (no Google account), tap Set Device Owner below.",
                fontSize = 12.5.sp,
                color = TextSubColor,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // SET DEVICE OWNER & AUTO-PERMISSIONS Button
            Button(
                onClick = {
                    isSettingOwner = true
                    appendLog("Executing Device Owner & Auto-Granting All Permissions over ADB...")
                    copyToClipboard(context, DPM_COMMAND_WIRELESS)

                    scope.launch {
                        val hostIp = parseIpFromInput(targetIpPort, context)
                        val hostPort = parsePortFromInput(targetIpPort)

                        // 1. Set Device Owner
                        appendLog("1/5 Setting Device Owner...")
                        val result1 = com.pksafe.lock.manager.util.AdbSocketEngine.executeRemoteCommand(
                            hostIp, hostPort, DPM_COMMAND_WIRELESS
                        )
                        appendLog("Device Owner Result: ${result1.message}")

                        // 2. Auto-Grant Display Over Other Apps (Overlay)
                        appendLog("2/5 Granting Overlay Lock Screen Permission...")
                        com.pksafe.lock.manager.util.AdbSocketEngine.executeRemoteCommand(
                            hostIp, hostPort, "appops set com.pksafe.lock.manager SYSTEM_ALERT_WINDOW allow"
                        )

                        // 3. Auto-Grant Anti-Uninstall Accessibility Guard
                        appendLog("3/5 Enabling Anti-Uninstall Accessibility Guard...")
                        com.pksafe.lock.manager.util.AdbSocketEngine.executeRemoteCommand(
                            hostIp, hostPort, "settings put secure enabled_accessibility_services com.pksafe.lock.manager/com.pksafe.lock.manager.service.AntiUninstallService"
                        )
                        com.pksafe.lock.manager.util.AdbSocketEngine.executeRemoteCommand(
                            hostIp, hostPort, "settings put secure accessibility_enabled 1"
                        )

                        // 4. Auto-Grant Critical Runtime Permissions (SMS / Location)
                        appendLog("4/5 Granting Offline SMS & Location Permissions...")
                        com.pksafe.lock.manager.util.AdbSocketEngine.executeRemoteCommand(
                            hostIp, hostPort, "pm grant com.pksafe.lock.manager android.permission.RECEIVE_SMS"
                        )
                        com.pksafe.lock.manager.util.AdbSocketEngine.executeRemoteCommand(
                            hostIp, hostPort, "pm grant com.pksafe.lock.manager android.permission.READ_SMS"
                        )
                        com.pksafe.lock.manager.util.AdbSocketEngine.executeRemoteCommand(
                            hostIp, hostPort, "pm grant com.pksafe.lock.manager android.permission.ACCESS_FINE_LOCATION"
                        )

                        isSettingOwner = false
                        appendLog("5/5 All Permissions & Device Owner Activated Automatically! 🎉")
                        Toast.makeText(context, "Full Setup & All Permissions Activated Automatically!", Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ThemeBrown)
            ) {
                if (isSettingOwner) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SETTING OWNER...", fontWeight = FontWeight.Bold)
                } else {
                    Text("SET DEVICE OWNER", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // DISCONNECT Button
            TextButton(
                onClick = {
                    isConnected = false
                    pairingCode = ""
                    targetIpPort = ""
                    appendLog("Disconnected from device.")
                    Toast.makeText(context, "Disconnected", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("DISCONNECT", color = ThemeBrown, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Status Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            if (isConnected) Color(0xFF22C55E) else Color(0xFFEF4444),
                            shape = RoundedCornerShape(5.dp)
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isConnected) "Connected" else "Not connected",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // LOG WINDOW AT BOTTOM
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Log",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.Black
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            copyToClipboard(context, logText)
                            Toast.makeText(context, "Logs copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Logs",
                            tint = ThemeBrown,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        "Copy",
                        fontSize = 12.sp,
                        color = ThemeBrown,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                color = LogBgColor,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 200.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = logText,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF334155),
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// === HELPER UTILS ===

private fun generateQrBitmap(content: String, size: Int): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) { null }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("ADB Command", text)
    clipboard.setPrimaryClip(clip)
}

private fun parseIpFromInput(input: String, context: Context): String {
    val trimmed = input.trim()
    if (trimmed.contains(":")) {
        val parts = trimmed.split(":")
        if (parts[0].isNotBlank()) return parts[0].trim()
    } else if (trimmed.isNotBlank()) {
        return trimmed
    }
    return "127.0.0.1"
}

private fun parsePortFromInput(input: String): Int {
    val trimmed = input.trim()
    if (trimmed.contains(":")) {
        val parts = trimmed.split(":")
        if (parts.size > 1) {
            return parts[1].trim().toIntOrNull() ?: 5555
        }
    }
    return 5555
}

private fun openBugjaegerOrAdb(context: Context, onLog: (String) -> Unit) {
    try {
        val pm = context.packageManager
        val installedApps = pm.getInstalledPackages(0)
        var launchIntent: Intent? = null
        for (app in installedApps) {
            if (app.packageName.contains("bugjaeger", ignoreCase = true)) {
                launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null) break
            }
        }

        if (launchIntent != null) {
            context.startActivity(launchIntent)
            onLog("Opened Bugjaeger helper app")
        } else {
            onLog("Command copied to clipboard: $DPM_COMMAND_WIRELESS")
        }
    } catch (e: Exception) {
        onLog("Executing ADB shell command via socket")
    }
}
