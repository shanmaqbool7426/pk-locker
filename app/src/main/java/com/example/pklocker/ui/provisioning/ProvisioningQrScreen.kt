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
    // ── THE SOURCE OF TRUTH ──
    // Vercel cache is bypassed by uploading the file specifically as "v6_app.apk"
    val apkUrl = "https://pk-locker-api.vercel.app/dl/v6_app.apk"
    
    val qrContent = remember {
        if (isForInstallation) {
            apkUrl
        } else {
            val json = JSONObject()
            
            // 1. Mandatory Enrollment Fields
            json.put("android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME", "com.pklocker.enterprise/com.example.pklocker.receiver.AdminReceiver")
            json.put("android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION", apkUrl)
            
            // 2. FINAL VERIFIED Checksums (DO NOT CHANGE THESE)
            json.put("android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM", "1iQjA_ONpwgKEiR-LCCgmPBPxvn2jcou3qfwciD5r1Q")
            
            // 3. Setup Flags
            json.put("android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED", true)
            
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
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(vertical = 16.dp),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(32.dp)) {
                    if (qrBitmap != null) {
                        Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "QR Code", modifier = Modifier.fillMaxSize())
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Surface(color = Color(0xFFEFF6FF), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("VERIFIED QR CONTENT:", fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color(0xFF1E40AF))
                    Text("1. Signature Checksum has been updated.", fontSize = 13.sp, color = Color.DarkGray)
                    Text("2. Use ADB to test without Reset if possible.", fontSize = 13.sp, color = Color.Blue, fontWeight = FontWeight.Bold)
                    Text("3. Tap '6 times' on Welcome screen to scan.", fontSize = 13.sp, color = Color.DarkGray)
                }
            }
        }
    }
}
