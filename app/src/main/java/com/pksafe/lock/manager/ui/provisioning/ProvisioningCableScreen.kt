package com.pksafe.lock.manager.ui.provisioning

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ProvisioningCableScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    
    var isDeviceConnected by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Waiting for Connection...") }

    // Periodic check for USB devices
    LaunchedEffect(Unit) {
        while (true) {
            val deviceList = usbManager.deviceList
            isDeviceConnected = deviceList.isNotEmpty()
            statusText = if (isDeviceConnected) "Device Detected via OTG" else "Connect C-to-C Cable Now"
            delay(2000)
        }
    }

    val adbCommand = "dpm set-device-owner com.pksafe.lock.manager/com.pksafe.lock.manager.receiver.AdminReceiver"

    val scrollState = rememberScrollState()

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0A0A0A)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("P.K Locker - Master Setup", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier =   Modifier.height(24.dp))

            // Pulse Animation for Connection
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(130.dp)) {
                Surface(
                    shape = CircleShape,
                    color = (if (isDeviceConnected) Color(0xFF22C55E) else Color(0xFF3B82F6)).copy(alpha = 0.1f),
                    border = BorderStroke(2.dp, if (isDeviceConnected) Color(0xFF22C55E) else Color(0xFF3B82F6)),
                    modifier = Modifier.fillMaxSize()
                ) {}
                Icon(
                    imageVector = if (isDeviceConnected) Icons.Default.Usb else Icons.Default.PortableWifiOff,
                    contentDescription = null,
                    modifier = Modifier.size(50.dp),
                    tint = if (isDeviceConnected) Color(0xFF22C55E) else Color(0xFF3B82F6)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                statusText,
                color = if (isDeviceConnected) Color(0xFF22C55E) else Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            
            Text(
                "Niche wala Button dabein, phir Bugjaeger mein ja kar Command PASTE kar ke PLAY ▶️ ka button dabein.",
                color = Color.Yellow,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

    var isThirdStepDone by remember { mutableStateOf(false) }

    // Step Card
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color(0xFF333333)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("PROCEDURE / TARIQA", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 2.sp)
            Spacer(modifier = Modifier.height(16.dp))
            
            ProvisioningStep(0, "Customer phone se sary GMAIL Accounts hta dein (Zaruri!)", true)
            ProvisioningStep(1, "Phones ko C-to-C cable se connect rkhein", isDeviceConnected)
            ProvisioningStep(2, "Customer phone pr 'Allow USB Debugging' ko OK krein", isDeviceConnected)
            ProvisioningStep(3, "Niche 'ACTIVATE CONTROL' button pr click krein", isThirdStepDone)
        }
    }

            Spacer(modifier = Modifier.weight(1f))

            // Professional ADB Copy Tool
            Text(
                "Manual Command (Optional):", 
                color = Color(0xFF3B82F6), 
                fontSize = 11.sp, 
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
            )
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                color = Color(0xFF111111),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF222222))
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        adbCommand, 
                        color = Color.Gray, 
                        fontSize = 10.sp, 
                        maxLines = 1, 
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(adbCommand))
                        Toast.makeText(context, "Command Copied!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // The Magic Button - Now simplified for Bugjaeger
            Button(
                onClick = { 
                    if (isDeviceConnected) {
                        // 1. Copy command to clipboard
                        clipboardManager.setText(AnnotatedString(adbCommand))
                        Toast.makeText(context, "Command Copied! Opening Bugjaeger...", Toast.LENGTH_LONG).show()
                        
                        // 2. Try to find Bugjaeger dynamically among all installed apps
                        try {
                            val pm = context.packageManager
                            val installedApps = pm.getInstalledPackages(0)
                            var launchIntent: Intent? = null
                            
                            // Scan all apps for "bugjaeger" in their package name
                            for (app in installedApps) {
                                if (app.packageName.contains("bugjaeger", ignoreCase = true)) {
                                    launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                                    if (launchIntent != null) break
                                }
                            }
                            
                            if (launchIntent != null) {
                                context.startActivity(launchIntent)
                            } else {
                                // If still not found, try to open the one on Play Store but with a generic search if details fail
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=Bugjaeger"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=Bugjaeger"))
                                    context.startActivity(intent)
                                }
                                Toast.makeText(context, "Bugjaeger ko KHUD (Manually) open karein!", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Command Copy ho gayi hai! Ab Bugjaeger manually kholein.", Toast.LENGTH_LONG).show()
                        }
                        isThirdStepDone = true
                    } else {
                        Toast.makeText(context, "Pehle Cable Connect Karein", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDeviceConnected) Color.White else Color(0xFF333333)
                )
            ) {
                Icon(Icons.Default.FlashOn, null, tint = Color.Black, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        "ACTIVATE (VIA BUGJAEGER)", 
                        color = Color.Black, 
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )
                    Text(
                        "Command copy hogi aur Bugjaeger khulay ga", 
                        color = Color.Black.copy(alpha = 0.6f), 
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ProvisioningStep(num: Int, text: String, isDone: Boolean) {
    Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = if (isDone) Color(0xFF22C55E) else Color(0xFF333333),
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isDone) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                } else {
                    Text(num.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, color = if (isDone) Color.White else Color.Gray, fontSize = 14.sp)
    }
}
