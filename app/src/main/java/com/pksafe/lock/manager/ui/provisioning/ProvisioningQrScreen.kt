package com.pksafe.lock.manager.ui.provisioning

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.wifi.WifiManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.pksafe.lock.manager.util.ApkServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
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
    val currentAppSignature = remember { getAppSignatureHash(context) }
    var serverRunning by remember { mutableStateOf(false) }
    var phoneIp by remember { mutableStateOf("") }
    var apkUrl by remember { mutableStateOf("https://pk-locker-api.vercel.app/apk/v6_app.apk") }
    var signature by remember { mutableStateOf(currentAppSignature) }
    var apkHash by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }
    var serverStatus by remember { mutableStateOf("Cloud Mode Ready! ✅") }
    var useLocalServer by remember { mutableStateOf(false) } // Default to Cloud Mode for reliability

    // Fallback Vercel URL
    val vercelUrl = "https://pk-locker-api.vercel.app/apk/v6_app.apk"

    // Auto-start local server and detect IP
    LaunchedEffect(useLocalServer) {
        if (useLocalServer) {
            withContext(Dispatchers.IO) {
                try {
                    // Start APK server on shopkeeper phone
                    ApkServer.start(context, 8080)
                    serverRunning = true

                    // Detect phone's WiFi IP
                    val ip = getDeviceIpAddress(context)
                    if (ip != null) {
                        phoneIp = ip
                        apkUrl = "http://$ip:8080/pklocker.apk"
                        
                        // Compute hash of the APK being served
                        val hash = fetchServerHash(apkUrl)
                        if (hash != null) {
                            apkHash = hash
                            serverStatus = "Ready! ✅"
                        } else {
                            serverStatus = "Server started but hash failed"
                        }
                    } else {
                        serverStatus = "WiFi not connected! Connect to WiFi first."
                    }
                } catch (e: Exception) {
                    serverStatus = "Server error: ${e.message}"
                    serverRunning = false
                }
            }
        } else {
            ApkServer.stop()
            serverRunning = false
            apkUrl = vercelUrl
            serverStatus = "Using Vercel URL"
            // Fetch hash from Vercel
            withContext(Dispatchers.IO) {
                val hash = fetchServerHash(vercelUrl)
                if (hash != null) {
                    apkHash = hash
                    serverStatus = "Vercel Ready! ✅"
                } else {
                    serverStatus = "Vercel hash fetch failed"
                }
            }
        }
    }

    // Stop server when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            ApkServer.stop()
        }
    }

    // Generate QR content
    val qrContent = remember(apkHash, apkUrl, signature) {
        if (apkUrl.isEmpty()) return@remember ""
        try {
            val json = JSONObject()
            val pkg = context.packageName
            val adminComponent = "$pkg/com.pksafe.lock.manager.receiver.AdminReceiver"

            // Core Provisioning Extras
            json.put("android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME", adminComponent)
            json.put("android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME", pkg)
            json.put("android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION", apkUrl)
            
            // Signature Checksum (The Base64 encoded SHA-256 hash of the signing certificate)
            // This is CRITICAL for Device Owner provisioning via QR.
            json.put("android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM", signature)

            // Package Checksum (SHA-256 of the APK file)
            if (apkHash.isNotEmpty()) {
                json.put("android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM", apkHash)
            }

            // Important UI/System Flags
            json.put("android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED", true)
            json.put("android.app.extra.PROVISIONING_SKIP_ENCRYPTION", true)
            json.put("android.app.extra.PROVISIONING_USE_MOBILE_DATA", true) // Allow download over data
            json.put("android.app.extra.PROVISIONING_LOCALE", "en_US")
            json.put("android.app.extra.PROVISIONING_TIME_ZONE", "GMT")
            
            // Pass extras to the app after setup
            json.put("android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE", JSONObject().apply {
                put("setup_source", "qr_code")
                put("is_full_control", true)
            })

            json.toString()
        } catch (e: Exception) { "" }
    }

    val qrBitmap = remember(qrContent) {
        if (qrContent.isEmpty()) return@remember null
        try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(qrContent, BarcodeFormat.QR_CODE, 512, 512)
            val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565)
            for (x in 0 until 512) for (y in 0 until 512)
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
            bitmap
        } catch (e: Exception) { null }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(title) }, navigationIcon = {
                IconButton(onClick = { ApkServer.stop(); onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                }
            })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(10.dp))

            // === SERVER MODE TOGGLE ===
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (serverRunning && serverStatus.contains("Ready"))
                        Color(0xFFF0FDF4) else Color(0xFFFFF7ED)
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (serverRunning) Icons.Default.Wifi else Icons.Default.Cloud,
                            null,
                            tint = if (serverRunning) Color(0xFF16A34A) else Color(0xFF2563EB),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (useLocalServer) "📱 Phone Server Mode (Recommended)"
                            else "☁️ Vercel Cloud Mode",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Phone se serve karo (no laptop needed)", fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = useLocalServer,
                            onCheckedChange = { useLocalServer = it }
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    if (useLocalServer) {
                        if (phoneIp.isNotEmpty()) {
                            Text("📡 Phone IP: $phoneIp", fontSize = 11.sp, color = Color(0xFF16A34A))
                            Text("🔗 APK URL: $apkUrl", fontSize = 10.sp, color = Color.Gray, maxLines = 1)
                        }
                        Text("🔑 Signature: ${signature.take(20)}...", fontSize = 10.sp, color = Color.Gray)
                        if (apkHash.isNotEmpty()) {
                            Text("📦 Hash: ${apkHash.take(20)}...", fontSize = 10.sp, color = Color.Gray)
                        }
                    } else {
                        Text("🔗 URL: $vercelUrl", fontSize = 10.sp, color = Color.Gray, maxLines = 1)
                    }

                    Spacer(Modifier.height(8.dp))

                    // Status
                    Surface(
                        color = if (serverStatus.contains("Ready")) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "Status: $serverStatus",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (serverStatus.contains("Ready")) Color(0xFF16A34A) else Color(0xFFDC2626),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Refresh button
                    Button(
                        onClick = {
                            scope.launch {
                                isVerifying = true
                                serverStatus = "Refreshing..."
                                withContext(Dispatchers.IO) {
                                    if (useLocalServer) {
                                        ApkServer.start(context, 8080)
                                        serverRunning = true
                                    }
                                    val hash = fetchServerHash(apkUrl)
                                    if (hash != null) {
                                        apkHash = hash
                                        serverStatus = "Ready! ✅"
                                    } else {
                                        serverStatus = "Hash fetch failed"
                                    }
                                }
                                isVerifying = false
                            }
                        },
                        enabled = !isVerifying,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        if (isVerifying) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
                        else Text("🔄 REFRESH")
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // === QR DISPLAY ===
            if (serverStatus.contains("Ready") && qrBitmap != null) {
                Card(
                    modifier = Modifier.size(280.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Image(qrBitmap.asImageBitmap(), "QR", modifier = Modifier.fillMaxSize())
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF2563EB))
                            Spacer(Modifier.height(12.dp))
                            Text("QR generate ho raha hai...", fontSize = 13.sp, color = Color.Gray)
                            Text("Wait for 'Ready' status", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // === INSTRUCTIONS ===
            Surface(
                color = Color(0xFFF0F9FF),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFBAE6FD))
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "📋 NAYE PHONE KO SETUP KARNE KA TAREEQA:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF0369A1)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "1️⃣ Naye phone ko Factory Reset karein\n" +
                        "2️⃣ Welcome screen par 6 dafa tap karein (QR scanner khulega)\n" +
                        "3️⃣ Pehle WiFi se connect karein (usi WiFi se jis par shopkeeper phone hai)\n" +
                        "4️⃣ Yeh QR code scan karein\n" +
                        "5️⃣ Setup automatically complete hoga! ✅",
                        fontSize = 11.sp,
                        color = Color(0xFF0369A1),
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Warning card
            if (useLocalServer && phoneIp.isEmpty()) {
                Surface(
                    color = Color(0xFFFEF2F2),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFFECACA))
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "⚠️ WiFi CONNECT KAREIN!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.Red
                        )
                        Text(
                            "Aapka phone WiFi se connect nahi hai. Hotspot ON karein ya WiFi se connect karein.",
                            fontSize = 11.sp,
                            color = Color.Red
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

// === UTILITY FUNCTIONS ===

/** Get device's WiFi IP address */
private fun getDeviceIpAddress(context: Context): String? {
    try {
        // Method 1: WifiManager
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val wifiInfo = wifiManager?.connectionInfo
        val ipInt = wifiInfo?.ipAddress ?: 0
        if (ipInt != 0) {
            val ip = String.format(
                "%d.%d.%d.%d",
                ipInt and 0xff,
                ipInt shr 8 and 0xff,
                ipInt shr 16 and 0xff,
                ipInt shr 24 and 0xff
            )
            if (ip != "0.0.0.0") return ip
        }

        // Method 2: NetworkInterface (works with hotspot too)
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val netInterface = interfaces.nextElement()
            if (netInterface.isLoopback || !netInterface.isUp) continue
            val addresses = netInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val addr = addresses.nextElement()
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    return addr.hostAddress
                }
            }
        }
    } catch (e: Exception) { /* ignore */ }
    return null
}

/** Get SHA-256 hash of the app's signing certificate */
private fun getAppSignatureHash(context: Context): String {
    return try {
        val pm = context.packageManager
        val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        }

        val firstSig = signatures?.getOrNull(0) ?: return "No Signature"
        val md = MessageDigest.getInstance("SHA-256")
        md.update(firstSig.toByteArray())
        val digest = md.digest()
        android.util.Base64.encodeToString(
            digest,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        )
    } catch (e: Exception) {
        "Error detecting signature"
    }
}

/** Download file and compute its SHA-256 hash */
private suspend fun fetchServerHash(urlStr: String): String? = withContext(Dispatchers.IO) {
    try {
        val digest = MessageDigest.getInstance("SHA-256")
        URL(urlStr).openStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) digest.update(buffer, 0, read)
        }
        android.util.Base64.encodeToString(
            digest.digest(),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        ).trim()
    } catch (e: Exception) { null }
}
