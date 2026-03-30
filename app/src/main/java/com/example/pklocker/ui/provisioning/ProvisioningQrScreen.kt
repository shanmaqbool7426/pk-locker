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
    // APK URL: This must be the actual APK path
    val apkUrl = "https://pk-locker-api.vercel.app/dl/app.apk"
    
    val qrContent = remember {
        if (isForInstallation) {
            apkUrl
        } else {
            val json = JSONObject()
            // 1. Component Name (Correct Package/Receiver)
            json.put("android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME", "com.example.pklocker/com.example.pklocker.receiver.AdminReceiver")
            
            // 2. Download Location
            json.put("android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION", apkUrl)
            
            // 3. CORRECT Signature Checksum (SHA-256 to URL-safe Base64)
            json.put("android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM", "TRUc8VW4MZjcNajo3pFnxmR6vY3sOmmrpPmu6HvUtwY")
            
            // 4. Critical Provisioning Flags
            json.put("android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED", true)
            json.put("android.app.extra.PROVISIONING_SKIP_ENCRYPTION", true)
            
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

            Surface(
                color = Color(0xFFEFF6FF),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = Color(0xFF3B82F6))
                        Spacer(Modifier.width(12.dp))
                        Text("PROVISIONING GUIDE", fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color(0xFF1E40AF))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("1. Factory Reset Target Phone", fontSize = 13.sp, color = Color.DarkGray)
                    Text("2. Tap 6 times on Welcome Screen", fontSize = 13.sp, color = Color.DarkGray)
                    Text("3. Connect WiFi & Scan this QR", fontSize = 13.sp, color = Color.DarkGray)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Text("Target Link: $apkUrl", fontSize = 10.sp, color = Color.Gray)
        }
    }
}
