package com.example.pklocker.ui.provisioning

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pklocker.util.Constants
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import android.graphics.Bitmap
import androidx.compose.material.icons.filled.Download
import android.graphics.Color as AndroidColor
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvisioningQrScreen(
    title: String,
    isForInstallation: Boolean = false,
    onBack: () -> Unit
) {
    val apkUrl = Constants.BASE_URL.replace("/api/", "/dl/app.apk")
    
    // ── STEP 1: Generate Content (JSON for Provisioning, Raw URL for Install) ──
    val qrContent = remember {
        if (isForInstallation) {
            apkUrl
        } else {
            val json = JSONObject()
            json.put("android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME", "com.example.pklocker/com.example.pklocker.receiver.AdminReceiver")
            json.put("android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION", apkUrl)
            json.put("android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM", "I6S9bI9X9Z-n2bI9V9Z-n2bI9V9Z-n2bI9V9Z-o=") 
            json.put("android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED", true)
            json.toString()
        }
    }

    // ── STEP 2: Generate QR Bitmap ──────────────────────────────────────────
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
                        Text(
                            if (isForInstallation) "Quick App Install" else "Enterprise Enrollment",
                            color = Color.White.copy(0.7f),
                            fontSize = 11.sp
                        )
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // --- PREMIUM SCANNABLE CARD ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(32.dp)) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        CircularProgressIndicator(color = Color(0xFF3B82F6))
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- INSTRUCTIONS HUB ---
            Surface(
                color = if (isForInstallation) Color(0xFFF0FDF4) else Color(0xFFEFF6FF),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isForInstallation) Icons.Default.Download else Icons.Default.Info,
                        null,
                        tint = if (isForInstallation) Color(0xFF16A34A) else Color(0xFF3B82F6)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = if (isForInstallation) {
                            "Simply scan this with any phone camera to download and install the PK Locker app instantly."
                        } else {
                            "Tap 6 times on a New Tablet's welcome screen to open the scanner, then scan this code."
                        },
                        fontSize = 13.sp,
                        color = if (isForInstallation) Color(0xFF166534) else Color(0xFF1E40AF),
                        fontWeight = FontWeight.Bold,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Target APK: $apkUrl",
                fontSize = 11.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

