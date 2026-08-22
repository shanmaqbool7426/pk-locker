package com.pksafe.lock.manager.ui.provisioning

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.pksafe.lock.manager.util.ApkServer
import com.pksafe.lock.manager.util.ProvisioningQrScreenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.security.MessageDigest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvisioningQrScreen(
    title: String,
    isForInstallation: Boolean = false,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- State ---
    val currentAppSignature = remember { ProvisioningQrScreenHelper.getAppSignatureHash(context) }
    var serverRunning by remember { mutableStateOf(false) }
    var phoneIp by remember { mutableStateOf("") }
    var serverPort by remember { mutableStateOf(8080) }
    var apkUrl by remember { mutableStateOf("https://pk-locker-api.vercel.app/apk/update.apk") }
    var signature by remember { mutableStateOf(currentAppSignature) }
    var apkHash by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }
    var serverStatus by remember { mutableStateOf("Cloud Mode Ready! ✅") }
    var useLocalServer by remember { mutableStateOf(false) }
    var showWifiInput by remember { mutableStateOf(false) }
    var wifiSsid by remember { mutableStateOf("") }
    var wifiPassword by remember { mutableStateOf("") }
    var includeWifi by remember { mutableStateOf(false) }

    // Pre-flight checklist: shopkeeper confirms the phone is ready for QR provisioning
    var factoryResetChecked by remember { mutableStateOf(false) }
    var wifiReadyChecked by remember { mutableStateOf(false) }
    val preFlightComplete = factoryResetChecked && wifiReadyChecked

    // Auto-detect QR scan / provisioning progress
    var lastSeenApkRequest by remember { mutableStateOf(0L) }
    var provisioningCompletedAt by remember { mutableStateOf(0L) }

    val vercelUrl = "https://pk-locker-api.vercel.app/apk/update.apk"

    // Poll for provisioning progress while the screen is visible
    LaunchedEffect(Unit) {
        while (true) {
            lastSeenApkRequest = ApkServer.lastApkRequestTime
            provisioningCompletedAt = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
                .getLong("provisioning_completed_at", 0L)
            kotlinx.coroutines.delay(1500)
        }
    }

    // Auto-start local server and detect IP when mode changes
    LaunchedEffect(useLocalServer) {
        if (useLocalServer) {
            try {
                val server = withContext(Dispatchers.IO) { ApkServer.start(context, 8080) }
                serverRunning = true
                serverPort = server.actualPort

                val ip = ProvisioningQrScreenHelper.getBestDeviceIpAddress(context)
                if (ip != null) {
                    phoneIp = ip
                    apkUrl = "http://$ip:${server.actualPort}/pklocker.apk"
                    refreshHash(apkUrl, useLocalServer) { hash, status ->
                        apkHash = hash
                        serverStatus = status
                    }
                } else {
                    serverStatus = "WiFi not connected! Connect to WiFi or start Hotspot."
                }
            } catch (e: Exception) {
                serverStatus = "Server error: ${e.message}"
                serverRunning = false
            }
        } else {
            ApkServer.stop()
            serverRunning = false
            apkUrl = vercelUrl
            serverStatus = "Using Vercel URL"
            refreshHash(vercelUrl, useLocalServer) { hash, status ->
                apkHash = hash
                serverStatus = status
            }
        }
    }

    // Stop server when leaving screen
    DisposableEffect(Unit) {
        onDispose { ApkServer.stop() }
    }

    // Generate QR content with DevicePolicyManager constants
    val qrContent = remember(apkHash, apkUrl, signature, includeWifi, wifiSsid, wifiPassword) {
        buildQrPayload(context, apkUrl, signature, apkHash, includeWifi, wifiSsid, wifiPassword)
    }

    // Generate QR bitmap with high error correction
    val qrBitmap = remember(qrContent) {
        generateQrBitmap(qrContent)
    }

    val isReady = serverStatus.contains("Ready") && qrBitmap != null &&
            signature != "No Signature" && signature != "Error detecting signature"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = { ApkServer.stop(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))

            // === PRE-FLIGHT CHECKLIST ===
            PreFlightChecklist(
                factoryResetChecked = factoryResetChecked,
                onFactoryResetChecked = { factoryResetChecked = it },
                wifiReadyChecked = wifiReadyChecked,
                onWifiReadyChecked = { wifiReadyChecked = it }
            )

            Spacer(Modifier.height(16.dp))

            // === MODE SELECTION CARD ===
            ModeSelectionCard(
                useLocalServer = useLocalServer,
                onModeChange = { useLocalServer = it },
                serverRunning = serverRunning,
                serverStatus = serverStatus,
                phoneIp = phoneIp,
                serverPort = serverPort,
                apkUrl = apkUrl,
                signature = signature,
                apkHash = apkHash
            )

            Spacer(Modifier.height(16.dp))

            // === WIFI CREDENTIALS (optional) ===
            WifiCard(
                showInput = showWifiInput,
                onToggleShow = { showWifiInput = !showWifiInput },
                includeWifi = includeWifi,
                onIncludeChange = { includeWifi = it },
                wifiSsid = wifiSsid,
                onSsidChange = { wifiSsid = it },
                wifiPassword = wifiPassword,
                onPasswordChange = { wifiPassword = it }
            )

            Spacer(Modifier.height(16.dp))

            // === QR DISPLAY ===
            QrDisplayCard(
                isReady = isReady && preFlightComplete,
                qrBitmap = qrBitmap,
                serverStatus = serverStatus,
                onRefresh = {
                    scope.launch {
                        isVerifying = true
                        serverStatus = "Refreshing..."
                        try {
                            if (useLocalServer) {
                                val server = withContext(Dispatchers.IO) {
                                    ApkServer.start(context, serverPort)
                                }
                                serverRunning = true
                                serverPort = server.actualPort
                                val ip = ProvisioningQrScreenHelper.getBestDeviceIpAddress(context)
                                if (ip != null) {
                                    phoneIp = ip
                                    apkUrl = "http://$ip:${server.actualPort}/pklocker.apk"
                                }
                            }
                            refreshHash(apkUrl, useLocalServer) { hash, status ->
                                apkHash = hash
                                serverStatus = status
                            }
                        } catch (e: Exception) {
                            serverStatus = "Server error: ${e.message}"
                        }
                        isVerifying = false
                    }
                },
                isVerifying = isVerifying
            )

            Spacer(Modifier.height(16.dp))

            // === AUTO-DETECT PROVISIONING PROGRESS ===
            ProvisioningProgressCard(
                lastApkRequestTime = lastSeenApkRequest,
                provisioningCompletedAt = provisioningCompletedAt
            )

            Spacer(Modifier.height(16.dp))

            // === STEP-BY-STEP SHOPKEEPER GUIDE ===
            ShopkeeperGuideCard()

            Spacer(Modifier.height(16.dp))

            // === TROUBLESHOOTING / FALLBACK ===
            TroubleshootingCard(useLocalServer = useLocalServer)

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ModeSelectionCard(
    useLocalServer: Boolean,
    onModeChange: (Boolean) -> Unit,
    serverRunning: Boolean,
    serverStatus: String,
    phoneIp: String,
    serverPort: Int,
    apkUrl: String,
    signature: String,
    apkHash: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (serverStatus.contains("Ready")) Color(0xFFF0FDF4) else Color(0xFFFFF7ED)
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (useLocalServer) Icons.Default.Wifi else Icons.Default.Cloud,
                    contentDescription = null,
                    tint = if (useLocalServer) Color(0xFF16A34A) else Color(0xFF2563EB),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (useLocalServer) "📱 Phone Server Mode" else "☁️ Vercel Cloud Mode",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = if (useLocalServer)
                    "Same WiFi / Hotspot pe fast aur reliable. Customer phone shopkeeper ke phone se APK download karega."
                else
                    "Internet required. Vercel server se APK download hoga. Slower but remote kaam karta hai.",
                fontSize = 12.sp,
                color = Color.Gray,
                lineHeight = 16.sp
            )

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Phone Server use karein", fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(checked = useLocalServer, onCheckedChange = onModeChange)
            }

            Spacer(Modifier.height(10.dp))

            if (useLocalServer) {
                if (phoneIp.isNotEmpty()) {
                    InfoRow("📡 Phone IP", "$phoneIp:$serverPort")
                }
                InfoRow("🔗 APK URL", apkUrl)
            } else {
                InfoRow("🔗 Cloud URL", apkUrl)
            }
            InfoRow("🔑 Signature", "${signature.take(24)}...")
            if (apkHash.isNotEmpty()) {
                InfoRow("📦 APK Hash", "${apkHash.take(24)}...")
            }

            Spacer(Modifier.height(10.dp))

            Surface(
                color = if (serverStatus.contains("Ready")) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Status: $serverStatus",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (serverStatus.contains("Ready")) Color(0xFF16A34A) else Color(0xFFDC2626),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun WifiCard(
    showInput: Boolean,
    onToggleShow: () -> Unit,
    includeWifi: Boolean,
    onIncludeChange: (Boolean) -> Unit,
    wifiSsid: String,
    onSsidChange: (String) -> Unit,
    wifiPassword: String,
    onPasswordChange: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Wifi, null, tint = Color(0xFF2563EB))
                Spacer(Modifier.width(8.dp))
                Text("WiFi Auto-Connect (Optional)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Agar customer phone ko automatically aapke hotspot se connect karwana hai, toh yahan details likhein.",
                fontSize = 11.sp,
                color = Color.Gray,
                lineHeight = 15.sp
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onToggleShow) {
                Text(if (showInput) "Hide WiFi settings" else "Add WiFi credentials")
            }

            if (showInput) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeWifi, onCheckedChange = onIncludeChange)
                    Text("QR mein WiFi details include karein", fontSize = 12.sp)
                }
                OutlinedTextField(
                    value = wifiSsid,
                    onValueChange = onSsidChange,
                    label = { Text("WiFi Name / Hotspot SSID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = wifiPassword,
                    onValueChange = onPasswordChange,
                    label = { Text("WiFi Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun QrDisplayCard(
    isReady: Boolean,
    qrBitmap: Bitmap?,
    serverStatus: String,
    onRefresh: () -> Unit,
    isVerifying: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "📲 Customer Scan Karein",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(12.dp))

            if (isReady && qrBitmap != null) {
                Card(
                    modifier = Modifier.size(260.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(6.dp)
                ) {
                    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Provisioning QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "✅ QR tayyar hai. Customer isse scan kare.",
                    fontSize = 12.sp,
                    color = Color(0xFF16A34A),
                    fontWeight = FontWeight.Bold
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF2563EB))
                        Spacer(Modifier.height(12.dp))
                        Text("QR generate ho raha hai...", fontSize = 13.sp, color = Color.Gray)
                        Text("Status: $serverStatus", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onRefresh,
                enabled = !isVerifying,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
                } else {
                    Text("🔄 Refresh QR / Server")
                }
            }
        }
    }
}

@Composable
private fun ShopkeeperGuideCard() {
    Surface(
        color = Color(0xFFF0F9FF),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFBAE6FD))
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "📋 Step-by-Step Guide",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFF0369A1)
            )
            Spacer(Modifier.height(8.dp))

            val steps = listOf(
                "Factory Reset" to "Customer phone ko factory reset karein ya naya phone ho.",
                "WiFi / Hotspot" to "Dono phones same WiFi pe hon. Ya shopkeeper phone ka hotspot ON karein.",
                "Welcome Screen" to "Customer phone welcome screen par 6 dafa tap karein (QR scanner khulega).",
                "QR Scan" to "Yeh QR code scan karein. Phone automatically setup karega.",
                "Done" to "5-10 minute mein Device Owner active ho jayega. App auto launch hogi."
            )

            steps.forEachIndexed { index, (title, desc) ->
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    Surface(
                        color = Color(0xFF0369A1),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            "${index + 1}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0369A1))
                        Text(desc, fontSize = 11.sp, color = Color(0xFF0369A1), lineHeight = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TroubleshootingCard(useLocalServer: Boolean) {
    Surface(
        color = Color(0xFFFEF9C3),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFFDE047))
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "⚠️ Agar QR Fail Ho",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFFA16207)
            )
            Spacer(Modifier.height(6.dp))
            val tips = if (useLocalServer) {
                listOf(
                    "Dono phones same network pe hon?",
                    "Shopkeeper phone ka hotspot ON hai?",
                    "Phone server 'Ready' status show kar raha hai?",
                    "QR refresh karein aur dobara scan karein.",
                    "Samsung phone ho toh Cable Setup ya Wireless ADB use karein."
                )
            } else {
                listOf(
                    "Internet connection check karein.",
                    "Vercel APK updated hai?",
                    "Cloud URL reach kar raha hai?",
                    "Local server mode try karein."
                )
            }
            tips.forEach { tip ->
                Text(
                    "• $tip",
                    fontSize = 11.sp,
                    color = Color(0xFFA16207),
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun PreFlightChecklist(
    factoryResetChecked: Boolean,
    onFactoryResetChecked: (Boolean) -> Unit,
    wifiReadyChecked: Boolean,
    onWifiReadyChecked: (Boolean) -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (factoryResetChecked && wifiReadyChecked) Color(0xFFF0FDF4) else Color(0xFFFEF3C7)
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (factoryResetChecked && wifiReadyChecked) Color(0xFF16A34A) else Color(0xFFD97706),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Pehle yeh check karein",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = factoryResetChecked,
                    onCheckedChange = onFactoryResetChecked
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Customer phone Factory Reset ho chuka hai",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Welcome screen dikh rahi ho. Agar nahi, toh Settings > Reset karein.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = wifiReadyChecked,
                    onCheckedChange = onWifiReadyChecked
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "WiFi ya Hotspot ready hai",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Dono phones same network pe hon. Hotspot ke liye neeche button dabain.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = {
                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
                        intent.setClassName("com.android.settings", "com.android.settings.TetherSettings")
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // Fallback: open generic settings
                        try {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS).apply {
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        } catch (_: Exception) {
                            // Ignore
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C))
            ) {
                Icon(Icons.Default.Wifi, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Hotspot ON Karne Ke Liye Settings Kholein")
            }
        }
    }
}

@Composable
private fun ProvisioningProgressCard(
    lastApkRequestTime: Long,
    provisioningCompletedAt: Long
) {
    val now = System.currentTimeMillis()
    val apkRequestAgeMs = if (lastApkRequestTime > 0) now - lastApkRequestTime else Long.MAX_VALUE
    val provisioningAgeMs = if (provisioningCompletedAt > 0) now - provisioningCompletedAt else Long.MAX_VALUE

    val (containerColor, borderColor, icon, title, message) = when {
        provisioningCompletedAt > 0 && provisioningAgeMs < 120_000 -> {
            // Completed within last 2 minutes
            Quintet(
                Color(0xFFDCFCE7), Color(0xFF86EFAC),
                Icons.Default.CheckCircle,
                "✅ Provisioning Complete!",
                "Device Owner successfully set ho gaya. Customer phone pe app launch ho rahi hai."
            )
        }
        lastApkRequestTime > 0 && apkRequestAgeMs < 120_000 -> {
            // APK download started within last 2 minutes
            Quintet(
                Color(0xFFDBEAFE), Color(0xFF93C5FD),
                Icons.Default.Download,
                "📲 QR Scanned — APK Downloading",
                "Customer ne QR scan kar liya hai. APK download ho raha hai. Thora wait karein..."
            )
        }
        else -> {
            Quintet(
                Color(0xFFF3F4F6), Color(0xFFD1D5DB),
                Icons.Default.QrCodeScanner,
                "⏳ QR Scan Ka Intezar",
                "Abhi tak koi scan nahi hua. Customer ko welcome screen pe 6 dafa tap kar ke QR scanner khulwain."
            )
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = when {
                    provisioningCompletedAt > 0 && provisioningAgeMs < 120_000 -> Color(0xFF16A34A)
                    lastApkRequestTime > 0 && apkRequestAgeMs < 120_000 -> Color(0xFF2563EB)
                    else -> Color.Gray
                },
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                message,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                color = Color.DarkGray,
                lineHeight = 16.sp
            )
        }
    }
}

// Simple 5-value holder for the progress card theming
private data class Quintet(
    val containerColor: Color,
    val borderColor: Color,
    val icon: ImageVector,
    val title: String,
    val message: String
)

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(
            "$label: ",
            fontSize = 10.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Bold
        )
        Text(
            value,
            fontSize = 10.sp,
            color = Color.Gray,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Builds the Android Device Owner provisioning JSON payload.
 * Uses DevicePolicyManager constants for correctness.
 */
private fun buildQrPayload(
    context: Context,
    apkUrl: String,
    signature: String,
    apkHash: String,
    includeWifi: Boolean,
    wifiSsid: String,
    wifiPassword: String
): String {
    if (apkUrl.isEmpty()) return ""
    if (signature == "No Signature" || signature == "Error detecting signature") return ""

    return try {
        val json = JSONObject()
        val pkg = context.packageName
        val adminComponent = "$pkg/com.pksafe.lock.manager.receiver.AdminReceiver"

        // Mandatory
        json.put(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, adminComponent)
        json.put(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, pkg)
        json.put(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION, apkUrl)
        json.put(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM, signature)

        // Optional APK file hash for integrity
        if (apkHash.isNotEmpty()) {
            json.put(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM, apkHash)
        }

        // UX flags
        json.put(DevicePolicyManager.EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED, true)
        json.put(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true)
        json.put(DevicePolicyManager.EXTRA_PROVISIONING_USE_MOBILE_DATA, true)
        json.put(DevicePolicyManager.EXTRA_PROVISIONING_LOCALE, "en_US")
        json.put(DevicePolicyManager.EXTRA_PROVISIONING_TIME_ZONE, "Asia/Karachi")

        // Optional WiFi credentials
        if (includeWifi && wifiSsid.isNotBlank()) {
            json.put(DevicePolicyManager.EXTRA_PROVISIONING_WIFI_SSID, wifiSsid)
            if (wifiPassword.isNotBlank()) {
                json.put(DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PASSWORD, wifiPassword)
            }
            json.put(DevicePolicyManager.EXTRA_PROVISIONING_WIFI_SECURITY_TYPE, "WPA2_PSK")
        }

        // Custom extras
        json.put(
            DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE,
            JSONObject().apply { put("setup_source", "qr_code") }
        )

        json.toString()
    } catch (e: Exception) {
        ""
    }
}

/**
 * Generates a QR bitmap with high error correction so it scans reliably even on
 * low-quality cameras or slightly damaged screens.
 */
private fun generateQrBitmap(content: String): Bitmap? {
    if (content.isEmpty()) return null
    return try {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.MARGIN to 2
        )
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512, hints)
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565)
        for (x in 0 until 512) {
            for (y in 0 until 512) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}

/**
 * Fetches the APK from the given URL and computes its SHA-256 hash.
 * Network work runs on a background dispatcher; the result is delivered
 * on the Main dispatcher so Compose state can be updated safely.
 */
private suspend fun refreshHash(
    urlStr: String,
    isLocal: Boolean,
    onResult: (hash: String, status: String) -> Unit
) {
    val result = withContext(Dispatchers.IO) {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            URL(urlStr).openStream().use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            val hash = android.util.Base64.encodeToString(
                digest.digest(),
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
            ).trim()

            val mode = if (isLocal) "Phone Server" else "Vercel"
            Pair(hash, "Ready! ✅ ($mode)")
        } catch (e: Exception) {
            Pair("", "Hash failed: ${e.message}")
        }
    }
    withContext(Dispatchers.Main) {
        onResult(result.first, result.second)
    }
}
