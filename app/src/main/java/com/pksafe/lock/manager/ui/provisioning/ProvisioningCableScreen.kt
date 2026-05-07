package com.pksafe.lock.manager.ui.provisioning

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

private val BgDark = Color(0xFF0A0A0A)
private val CardDark = Color(0xFF1A1A1A)
private val Green = Color(0xFF22C55E)
private val Blue = Color(0xFF3B82F6)
private val Yellow = Color(0xFFFBBF24)
private val White = Color.White

private const val DPM_COMMAND =
    "dpm set-device-owner com.pksafe.lock.manager/com.pksafe.lock.manager.receiver.AdminReceiver"

// ─── Persistent notification so command is accessible inside Bugjaeger ───────
private fun showCommandNotification(context: Context) {
    val channelId = "adb_command_channel"
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        nm.createNotificationChannel(
            NotificationChannel(channelId, "ADB Command Helper", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Shows the Device Owner command while you use Bugjaeger"
            }
        )
    }

    // Copy-action intent (copies command to clipboard when tapped from notification)
    val copyIntent = Intent(context, context.javaClass).apply {
        action = "COPY_ADB_CMD"
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    val copyPi = PendingIntent.getActivity(
        context, 0, copyIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_menu_share)
        .setContentTitle("📋 Bugjaeger mein PASTE karein")
        .setContentText(DPM_COMMAND)
        .setStyle(NotificationCompat.BigTextStyle().bigText(
            "1. Bugjaeger mein Shell tab open karein\n" +
            "2. Command field mein LONG PRESS → Paste\n" +
            "3. ▶️ Play button dabein\n\n" +
            "Command:\n$DPM_COMMAND"
        ))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setOngoing(true)
        .addAction(android.R.drawable.ic_menu_edit, "Command Copy Karein", copyPi)
        .setAutoCancel(false)
        .build()

    try {
        nm.notify(1001, notification)
    } catch (e: SecurityException) {
        // Notification permission might be missing on Android 13+
    }
}

private fun dismissCommandNotification(context: Context) {
    (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(1001)
}

// ─── Main Screen ─────────────────────────────────────────────────────────────
@Composable
fun ProvisioningCableScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    var isDeviceConnected by remember { mutableStateOf(false) }
    var showPostGuide by remember { mutableStateOf(false) }
    var isThirdStepDone by remember { mutableStateOf(false) }

    // Request Notification Permission for Android 13+ (API 33)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showCommandNotification(context)
        }
    }

    // USB check loop
    LaunchedEffect(Unit) {
        while (true) {
            isDeviceConnected = usbManager.deviceList.isNotEmpty()
            delay(2000)
        }
    }

    // Dismiss notification when screen closes
    DisposableEffect(Unit) {
        onDispose { dismissCommandNotification(context) }
    }

    // Pulse animation for USB icon
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse"
    )

    // ─── Post-Activation Guide Dialog ────────────────────────────────────────
    if (showPostGuide) {
        AlertDialog(
            onDismissRequest = { showPostGuide = false },
            containerColor = CardDark,
            shape = RoundedCornerShape(24.dp),
            icon = {
                Surface(shape = CircleShape, color = Blue.copy(.12f), modifier = Modifier.size(56.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Terminal, null, tint = Blue, modifier = Modifier.size(28.dp))
                    }
                }
            },
            title = {
                Text("Bugjaeger Mein Yeh Karein", color = White, fontWeight = FontWeight.Black, fontSize = 18.sp, textAlign = TextAlign.Center)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Command box
                    Surface(color = Color(0xFF111111), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Blue.copy(.3f))) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(DPM_COMMAND, color = Green, fontSize = 9.5.sp, modifier = Modifier.weight(1f), lineHeight = 14.sp)
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(DPM_COMMAND))
                                Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                            }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.ContentCopy, null, tint = Blue, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    GuideStep(1, "Bugjaeger mein upar '<>' (Shell) tab open karein", Blue)
                    GuideStep(2, "Neechay Text field mein LONG PRESS karein → 'Paste' select karein", Yellow)
                    GuideStep(3, "▶️ Play button dabein → DONE!", Green)

                    // Notification tip
                    Surface(color = Yellow.copy(.08f), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Yellow.copy(.25f))) {
                        Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.NotificationsActive, null, tint = Yellow, modifier = Modifier.size(16.dp))
                            Text("Notification bar mein bhi command mojud hai — wahan se bhi copy kar sakte hain!", color = Yellow, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showPostGuide = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Blue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Samajh Gaya, OK ▶️", fontWeight = FontWeight.Bold, color = White)
                }
            }
        )
    }

    // ─── Main UI ─────────────────────────────────────────────────────────────
    Surface(modifier = Modifier.fillMaxSize(), color = BgDark) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = White)
                }
                Spacer(Modifier.width(8.dp))
                Text("Device Owner Activation", color = White, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }

            Spacer(Modifier.height(28.dp))

            // ─── USB Status Circle ────────────────────────────────────────
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp).scale(if (isDeviceConnected) pulse else 1f)) {
                Surface(
                    shape = CircleShape,
                    color = (if (isDeviceConnected) Green else Blue).copy(.1f),
                    border = BorderStroke(2.dp, if (isDeviceConnected) Green else Blue),
                    modifier = Modifier.fillMaxSize()
                ) {}
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (isDeviceConnected) Icons.Default.Usb else Icons.Default.PortableWifiOff,
                        null,
                        modifier = Modifier.size(44.dp),
                        tint = if (isDeviceConnected) Green else Blue
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (isDeviceConnected) "Connected" else "Waiting...",
                        color = if (isDeviceConnected) Green else Blue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                if (isDeviceConnected) "✅ Phone Connected — Ready!" else "Cable lagayein phir proceed karein",
                color = if (isDeviceConnected) Green else White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))

            // ─── Pre-Steps Card ───────────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFF2D2D2D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("KARNE KA TARIQA", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.5.sp)

                    PreStep(icon = Icons.Default.PersonRemove, text = "Customer phone se sab Gmail accounts hata dein", done = true, color = Green)
                    PreStep(icon = Icons.Default.Cable, text = "C-to-C cable se dono phones connect karein", done = isDeviceConnected, color = Blue)
                    PreStep(icon = Icons.Default.DeveloperMode, text = "'Allow USB Debugging' ko OK karein (customer phone pe)", done = isDeviceConnected, color = Yellow)
                    PreStep(icon = Icons.Default.FlashOn, text = "Niche ka button dabein → Bugjaeger khulega", done = isThirdStepDone, color = Blue)
                }
            }

            Spacer(Modifier.height(24.dp))

            // ─── MAIN BUTTON ──────────────────────────────────────────────
            Button(
                onClick = {
                    if (!isDeviceConnected) {
                        Toast.makeText(context, "Pehle cable connect karein!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    // 1. Copy command to clipboard
                    clipboardManager.setText(AnnotatedString(DPM_COMMAND))

                    // 2. Show persistent notification for easy access inside Bugjaeger
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        showCommandNotification(context)
                    }

                    // 3. Try to open Bugjaeger
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
                        } else {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=Bugjaeger")))
                            } catch (e: Exception) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=Bugjaeger")))
                            }
                            Toast.makeText(context, "Bugjaeger install karein!", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Bugjaeger ko manually open karein.", Toast.LENGTH_LONG).show()
                    }

                    // 4. Show the visual guide in our app
                    isThirdStepDone = true
                    showPostGuide = true
                },
                modifier = Modifier.fillMaxWidth().height(68.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDeviceConnected) White else Color(0xFF2D2D2D)
                )
            ) {
                Icon(Icons.Default.FlashOn, null, tint = if (isDeviceConnected) Color.Black else Color.Gray, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        "ACTIVATE VIA BUGJAEGER",
                        color = if (isDeviceConnected) Color.Black else Color.Gray,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp
                    )
                    Text(
                        if (isDeviceConnected) "Command auto-copy hogi aur Bugjaeger khulega"
                        else "Pehle phone cable se connect karein",
                        color = (if (isDeviceConnected) Color.Black else Color.Gray).copy(.6f),
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

@Composable
fun PreStep(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, done: Boolean, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = CircleShape,
            color = if (done) color.copy(.2f) else Color(0xFF2D2D2D),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (done) Icons.Default.CheckCircle else icon,
                    null,
                    tint = if (done) color else Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(text, color = if (done) White else Color.Gray, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
fun GuideStep(num: Int, text: String, color: Color) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(shape = CircleShape, color = color.copy(.18f), modifier = Modifier.size(24.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(num.toString(), color = color, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
        }
        Text(text, color = White.copy(.85f), fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
    }
}
