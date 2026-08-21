package com.pksafe.lock.manager.ui.provisioning

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pksafe.lock.manager.util.UsbAdbEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.KeyFactory
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import android.util.Base64

private const val PREFS_NAME      = "usb_adb_prefs"
private const val KEY_PRIVATE      = "rsa_private"
private const val KEY_PUBLIC       = "rsa_public"
private const val ACTION_USB_PERM  = "com.pksafe.USB_PERMISSION"

private val BgDark     = Color(0xFF0F172A)
private val CardDark   = Color(0xFF1E293B)
private val Green      = Color(0xFF22C55E)
private val Blue       = Color(0xFF3B82F6)
private val Yellow     = Color(0xFFF59E0B)
private val White      = Color.White
private val LogBgColor = Color(0xFF020617)

// ─── RSA Key persistence ──────────────────────────────────────────────────────
private fun loadOrGenerateKeyPair(context: Context): KeyPair {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val privB64 = prefs.getString(KEY_PRIVATE, null)
    val pubB64  = prefs.getString(KEY_PUBLIC,  null)
    return if (privB64 != null && pubB64 != null) {
        try {
            val kf = KeyFactory.getInstance("RSA")
            val priv: PrivateKey = kf.generatePrivate(PKCS8EncodedKeySpec(Base64.decode(privB64, Base64.DEFAULT)))
            val pub:  PublicKey  = kf.generatePublic(X509EncodedKeySpec(Base64.decode(pubB64, Base64.DEFAULT)))
            KeyPair(pub, priv)
        } catch (_: Exception) { generateAndSave(context) }
    } else generateAndSave(context)
}

private fun generateAndSave(context: Context): KeyPair {
    val kp = UsbAdbEngine.generateKeyPair()
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
        putString(KEY_PRIVATE, Base64.encodeToString(kp.private.encoded, Base64.DEFAULT))
        putString(KEY_PUBLIC,  Base64.encodeToString(kp.public.encoded,  Base64.DEFAULT))
        apply()
    }
    return kp
}

// ─── Screen ───────────────────────────────────────────────────────────────────
@Composable
fun ProvisioningCableScreen(onBack: () -> Unit) {
    val context    = LocalContext.current
    val scope      = rememberCoroutineScope()
    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    var usbDevice          by remember { mutableStateOf<UsbDevice?>(null) }
    var hasUsbPermission   by remember { mutableStateOf(false) }
    var isExecuting        by remember { mutableStateOf(false) }
    var isCompletedSuccess by remember { mutableStateOf(false) }
    var logText by remember { mutableStateOf(
        "⚡ Ready.\n\nSetup (Sirf 3 Steps):\n" +
        "1. Customer phone: USB Debugging ON\n" +
        "2. C-to-C cable dono phones mein lagao\n" +
        "3. ACTIVATE dabao → Customer phone pe 'Allow' dabao"
    )}
    fun appendLog(msg: String) { logText = "$logText\n$msg" }

    // Load RSA key pair (persisted — so customer phone remembers trust)
    val keyPair = remember { loadOrGenerateKeyPair(context) }

    var hasRequestedPermission by remember { mutableStateOf(false) }

    // USB device detect + status check loop
    LaunchedEffect(Unit) {
        while (true) {
            val found = UsbAdbEngine.findAdbDevice(usbManager)

            if (found != null) {
                if (usbDevice != found) {
                    usbDevice = found
                    hasRequestedPermission = false // Reset when new device connected
                }
                val hasPerm = usbManager.hasPermission(found)
                if (hasPerm && !hasUsbPermission) {
                    hasUsbPermission = true
                    appendLog("✅ USB Permission granted! ACTIVATE dabao.")
                }
                hasUsbPermission = hasPerm

                // Auto-request permission ONLY ONCE per device connection
                if (!hasPerm && !hasRequestedPermission) {
                    hasRequestedPermission = true
                    val intent = Intent(ACTION_USB_PERM).apply {
                        setPackage(context.packageName)
                    }
                    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        PendingIntent.FLAG_MUTABLE else 0
                    val pi = PendingIntent.getBroadcast(context, 0, intent, flags)
                    usbManager.requestPermission(found, pi)
                    appendLog("📱 USB device detected!\n⚠️ System dialog aya hoga — 'ALLOW' dabao.")
                }
            } else {
                if (usbDevice != null) {
                    usbDevice = null
                    hasUsbPermission = false
                    hasRequestedPermission = false
                    appendLog("⚠️ Cable unplug hua.")
                }
            }
            delay(2000)
        }
    }


    // USB permission BroadcastReceiver
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == ACTION_USB_PERM) {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    hasUsbPermission = granted
                    if (granted) appendLog("✅ Permission granted! ACTIVATE dabao.")
                    else appendLog("❌ Permission denied. Cable hatao aur dubara lagao.")
                }
            }
        }
        val filter = IntentFilter(ACTION_USB_PERM)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else
            context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }


    // Pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse"
    )

    Surface(modifier = Modifier.fillMaxSize(), color = BgDark) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Header ──────────────────────────────────────────────────────
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = White) }
                Spacer(Modifier.width(8.dp))
                Text("C-to-C Activation", color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))

            // ── USB Status Circle ────────────────────────────────────────────
            val circleColor  = when {
                isCompletedSuccess -> Green
                hasUsbPermission   -> Blue
                usbDevice != null  -> Yellow
                else               -> Color(0xFF334155)
            }
            val statusText = when {
                isCompletedSuccess -> "DONE ✅"
                hasUsbPermission   -> "Connected"
                usbDevice != null  -> "Allow?"
                else               -> "Cable Lagao"
            }
            val statusIcon = when {
                isCompletedSuccess -> Icons.Default.CheckCircle
                hasUsbPermission   -> Icons.Default.Usb
                usbDevice != null  -> Icons.Default.Warning
                else               -> Icons.Default.UsbOff
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(130.dp).scale(if (usbDevice != null && !isCompletedSuccess) pulse else 1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = circleColor.copy(0.12f),
                    border = BorderStroke(2.dp, circleColor),
                    modifier = Modifier.fillMaxSize()
                ) {}
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(statusIcon, null, modifier = Modifier.size(40.dp), tint = circleColor)
                    Spacer(Modifier.height(4.dp))
                    Text(statusText, color = circleColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                when {
                    isCompletedSuccess -> "✅ Customer Phone Fully Activated!"
                    hasUsbPermission   -> "✅ Ready — ACTIVATE dabao!"
                    usbDevice != null  -> "⚠️ Permission chahiye — button dabao"
                    else               -> "C-to-C Cable se Customer Phone connect karein"
                },
                color = if (usbDevice != null) circleColor else White.copy(0.7f),
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            // ── Checklist ────────────────────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("CHECKLIST (Sirf 3 Steps)", color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.2.sp)
                    Step(Icons.Default.BugReport,    "Customer phone: USB Debugging ON",                done = true, color = Green)
                    Step(Icons.Default.Cable,        "C-to-C Cable dono phones mein lagao",            done = usbDevice != null, color = Blue)
                    Step(Icons.Default.Lock,         "ACTIVATE dabao + customer 'Allow' kare",          done = hasUsbPermission, color = Yellow)
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Manual USB Permission Button (shows if device found but no permission) ──
            if (usbDevice != null && !hasUsbPermission && !isExecuting) {
                OutlinedButton(
                    onClick = {
                        val intent = Intent(ACTION_USB_PERM).apply {
                            setPackage(context.packageName)
                        }
                        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                            PendingIntent.FLAG_MUTABLE else 0
                        val pi = PendingIntent.getBroadcast(context, 0, intent, flags)
                        usbManager.requestPermission(usbDevice!!, pi)
                        appendLog("USB permission dialog request bheja gaya...")
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, Yellow),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Yellow)
                ) {
                    Icon(Icons.Default.Shield, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("USB Permission Request Karo", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
            }

            // ── ACTIVATE Button ──────────────────────────────────────────────
            val canActivate = usbDevice != null && hasUsbPermission && !isExecuting
            Button(
                onClick = {
                    val dev = usbDevice
                    if (dev == null) {
                        Toast.makeText(context, "Pehle C-to-C cable lagao!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (!hasUsbPermission) {
                        Toast.makeText(context, "Pehle USB permission allow karo!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isExecuting = true
                    isCompletedSuccess = false
                    logText = "⚡ Activation shuru...\n"
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            UsbAdbEngine.runFullSetup(usbManager, dev, keyPair) { log ->
                                scope.launch { appendLog(log) }
                            }
                        }
                        isExecuting = false
                        isCompletedSuccess = result.success
                        appendLog(if (result.success) "\n🎉 SUCCESS! Customer phone activated!" else "\n❌ ${result.message}")
                        Toast.makeText(context,
                            if (result.success) "✅ Activation Complete!" else "Failed — logs dekho",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(62.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor  = when { isCompletedSuccess -> Green; canActivate -> Green; else -> Color(0xFF334155) },
                    disabledContainerColor = Color(0xFF334155)
                ),
                enabled = canActivate
            ) {
                when {
                    isExecuting -> {
                        CircularProgressIndicator(Modifier.size(24.dp), color = White, strokeWidth = 2.5.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("ACTIVATING...", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = White)
                    }
                    isCompletedSuccess -> {
                        Icon(Icons.Default.CheckCircle, null, tint = White, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("ACTIVATED ✅", fontWeight = FontWeight.Black, fontSize = 16.sp, color = White)
                    }
                    else -> {
                        Icon(Icons.Default.FlashOn, null, tint = White, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("⚡ 1-CLICK ACTIVATE", fontWeight = FontWeight.Black, fontSize = 16.sp, color = White)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Console Log Header with Copy Button ──────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Terminal, null, tint = Blue, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Activation Log", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = White.copy(0.8f))
                }
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Provisioning Log", logText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "📋 Logs copied to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Logs", tint = Blue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy Logs", fontSize = 12.sp, color = Blue, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(6.dp))
            Surface(
                color = LogBgColor, shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 240.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(logText, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace,
                        color = Color(0xFF38BDF8), lineHeight = 16.sp)
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun Step(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, done: Boolean, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(shape = CircleShape, color = if (done) color.copy(.18f) else Color(0xFF334155), modifier = Modifier.size(32.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(if (done) Icons.Default.CheckCircle else icon, null,
                    tint = if (done) color else Color.Gray, modifier = Modifier.size(16.dp))
            }
        }
        Text(text, color = if (done) White else Color.Gray, fontSize = 12.5.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
    }
}
