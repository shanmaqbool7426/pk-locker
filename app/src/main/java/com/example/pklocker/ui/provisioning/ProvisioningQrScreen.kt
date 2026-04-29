package com.example.pklocker.ui.provisioning

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvisioningQrScreen(
    title: String,
    isForInstallation: Boolean = false,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var apkHash by remember { mutableStateOf("") } // SHA-256 of the APK file
    
    val apkUrl = "https://pk-locker-api.vercel.app/dl/v6_app.apk"
    
    val signatureChecksum = remember {
        try {
            val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNATURES)
            }

            val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo?.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo?.signatures
            }

            val signature = signatures?.getOrNull(0)
            if (signature != null) {
                val md = java.security.MessageDigest.getInstance("SHA-256")
                md.update(signature.toByteArray())
                android.util.Base64.encodeToString(md.digest(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
            } else {
                "ERROR"
            }
        } catch (e: Exception) {
            "ERROR"
        }
    }

    val qrContent = remember(signatureChecksum, apkHash) {
        if (isForInstallation) {
            apkUrl
        } else {
            val json = JSONObject()
            val packageName = context.packageName
            
            json.put("android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME", "$packageName/com.example.pklocker.receiver.AdminReceiver")
            json.put("android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION", apkUrl)
            json.put("android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM", signatureChecksum)
            
            // Critical for Samsung: Add APK File Checksum if provided
            if (apkHash.isNotBlank()) {
                json.put("android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM", apkHash.trim())
            }
            
            json.put("android.app.extra.PROVISIONING_LOCALE", "en_US")
            json.put("android.app.extra.PROVISIONING_TIME_ZONE", "Asia/Karachi")
            json.put("android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED", true)
            json.put("android.app.extra.PROVISIONING_SKIP_ENCRYPTION", true)
            
            val extras = JSONObject()
            extras.put("is_new_enrollment", "true")
            json.put("android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE", extras)
            
            json.toString()
        }
    }

    val qrBitmap = remember(qrContent) {
        try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(qrContent, BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Enterprise Enrollment", color = Color.White.copy(0.7f), fontSize = 11.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        },
        containerColor = Color(0xFFF8FAFC)
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(32.dp)) {
                    if (qrBitmap != null) {
                        Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "QR Code", modifier = Modifier.fillMaxSize())
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // APK Hash Input for Samsung strictness
            OutlinedTextField(
                value = apkHash,
                onValueChange = { apkHash = it },
                label = { Text("APK SHA-256 (Required for Samsung)") },
                placeholder = { Text("Paste APK Base64 Hash here") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(color = Color(0xFFEFF6FF), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("SIGNATURE CHECKSUM:", fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color(0xFF1E40AF))
                    Text(signatureChecksum, fontSize = 10.sp, color = Color.DarkGray, modifier = Modifier.padding(top = 4.dp))
                    
                    Spacer(Modifier.height(12.dp))
                    
                    Text("ENROLLMENT GUIDE:", fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color(0xFF1E40AF))
                    Text("1. Master phone MUST run the Release APK.", fontSize = 12.sp, color = Color.DarkGray)
                    Text("2. APK URL must be a direct file link.", fontSize = 12.sp, color = Color.DarkGray)
                    Text("3. Samsung A05 needs the APK Hash above.", fontSize = 12.sp, color = Color.DarkGray)
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
