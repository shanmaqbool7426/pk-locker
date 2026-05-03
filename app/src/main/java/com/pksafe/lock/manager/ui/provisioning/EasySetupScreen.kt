package com.pksafe.lock.manager.ui.provisioning

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File

/**
 * Easy Customer Setup Screen — Shopkeeper can install PKLocker on customer phone
 * WITHOUT laptop, QR code, or Device Owner setup.
 *
 * Flow:
 * 1. Shopkeeper taps "Share APK" to send app via WhatsApp/Bluetooth/ShareIt
 * 2. Customer installs and opens app on their phone
 * 3. App auto-requests: Device Admin → Overlay → Accessibility
 * 4. Customer enters IMEI → linked to shopkeeper's account
 * 5. Shopkeeper can lock/unlock remotely via dashboard
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EasySetupScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Easy Customer Setup") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // === HEADER ===
            Surface(
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.PhoneAndroid,
                        null,
                        tint = Color(0xFF3B82F6),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Naye Phone Ko Setup Karein",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Bina laptop ke, sirf apne phone se!",
                        fontSize = 13.sp,
                        color = Color.White.copy(0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // === STEP-BY-STEP GUIDE ===

            // Step 1: Share APK
            SetupStepCard(
                stepNumber = "1",
                title = "APK Share Karein",
                description = "Customer ke phone mein app bhejein WhatsApp, Bluetooth, ya ShareIt se",
                icon = Icons.Default.Share,
                accentColor = Color(0xFF3B82F6),
                buttonText = "SHARE APK",
                onClick = { shareApk(context) }
            )

            Spacer(Modifier.height(12.dp))

            // Alternative: Download Link
            SetupStepCard(
                stepNumber = "1b",
                title = "Ya Download Link Bhejein",
                description = "Customer ko ye link bhejein — wo khud download kar le",
                icon = Icons.Default.Link,
                accentColor = Color(0xFF8B5CF6),
                buttonText = "COPY DOWNLOAD LINK",
                onClick = {
                    val downloadUrl = "https://pk-locker-api.vercel.app/apk/v6_app.apk"
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("APK Link", downloadUrl))
                    Toast.makeText(context, "Link copy ho gaya! WhatsApp par paste karein", Toast.LENGTH_LONG).show()
                }
            )

            Spacer(Modifier.height(12.dp))

            // Step 2: Install
            SetupStepCard(
                stepNumber = "2",
                title = "Customer Phone Par Install Karein",
                description = "Customer APK install kare. 'Unknown Sources' allow karna hoga",
                icon = Icons.Default.InstallMobile,
                accentColor = Color(0xFF10B981),
                buttonText = null,
                onClick = {}
            )

            Spacer(Modifier.height(12.dp))

            // Step 3: Open App
            SetupStepCard(
                stepNumber = "3",
                title = "App Kholein Aur Permissions Dein",
                description = "Customer phone par app kholein. App khud permissions maangega:\n" +
                    "✅ Device Admin → App uninstall block\n" +
                    "✅ Overlay → Lock screen show\n" +
                    "✅ Accessibility → Settings block",
                icon = Icons.Default.Security,
                accentColor = Color(0xFFF59E0B),
                buttonText = null,
                onClick = {}
            )

            Spacer(Modifier.height(12.dp))

            // Step 4: Enter IMEI
            SetupStepCard(
                stepNumber = "4",
                title = "IMEI Enter Karein",
                description = "Customer phone par jo IMEI maangega, wahi IMEI daalein jo aapne Register Device mein diya tha",
                icon = Icons.Default.Dialpad,
                accentColor = Color(0xFFEF4444),
                buttonText = null,
                onClick = {}
            )

            Spacer(Modifier.height(12.dp))

            // Step 5: Done!
            SetupStepCard(
                stepNumber = "✓",
                title = "Setup Complete!",
                description = "Ab aap Dashboard se is phone ko remotely Lock/Unlock kar sakte hain",
                icon = Icons.Default.CheckCircle,
                accentColor = Color(0xFF22C55E),
                buttonText = null,
                onClick = {}
            )

            Spacer(Modifier.height(24.dp))

            // === INFO BOX ===
            Surface(
                color = Color(0xFFFFF7ED),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFFED7AA))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "ℹ️ Device Owner vs Device Admin",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF9A3412)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Is method se Device ADMIN set hota hai (Device Owner nahi). " +
                        "Fark sirf itna hai ke Device Owner mein Factory Reset bhi block hota hai. " +
                        "Baqi sab — Lock Screen, Camera Block, App Block — sab kuch kaam karta hai! " +
                        "Device Owner ke liye QR code ya laptop (ADB) chahiye.",
                        fontSize = 11.sp,
                        color = Color(0xFF9A3412),
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SetupStepCard(
    stepNumber: String,
    title: String,
    description: String,
    icon: ImageVector,
    accentColor: Color,
    buttonText: String?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Step number badge
            Surface(
                color = accentColor,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        stepNumber,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text(description, fontSize = 12.sp, color = Color.Gray, lineHeight = 18.sp)

                if (buttonText != null) {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onClick,
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(icon, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(buttonText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/** Share the app's own APK via Android share sheet (WhatsApp, Bluetooth, ShareIt, etc.) */
private fun shareApk(context: Context) {
    try {
        val sourceApk = File(context.applicationInfo.sourceDir)
        val shareDir = File(context.cacheDir, "share")
        shareDir.mkdirs()
        val shareApk = File(shareDir, "PKLocker.apk")
        sourceApk.copyTo(shareApk, overwrite = true)

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            shareApk
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, apkUri)
            putExtra(Intent.EXTRA_TEXT, "PKLocker Security App install karein. EMI protection ke liye zaroori hai.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(Intent.createChooser(shareIntent, "PKLocker APK Share Karein"))
    } catch (e: Exception) {
        Toast.makeText(context, "APK share nahi ho saki: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
