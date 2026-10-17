package com.pksafe.lock.manager.ui.provisioning

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.pksafe.lock.manager.ui.theme.*
import com.pksafe.lock.manager.util.AdbController
import com.pksafe.lock.manager.util.AdbShell
import com.pksafe.lock.manager.util.Constants
import com.pksafe.lock.manager.util.ProvisionWorkflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ═════════════════════════════════════════════════════════════════════════════
//  PK LOCKER — Wireless ADB Setup Screen (Professional Redesign)
//  All business logic preserved. UI completely refreshed.
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WirelessAdbSetupScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pairingCode by remember { mutableStateOf("") }
    var isWorking by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }
    var showPairingSuccess by remember { mutableStateOf(false) }
    var logText by remember { mutableStateOf("Ready. Customer phone pe pairing code kholo, yahan type karo, phir 1 button dabao.") }

    val downloadQrBitmap = remember { generateQrBitmap(Constants.APK_DOWNLOAD_URL, 512) }
    val adbController = remember { AdbController(context) }

    fun appendLog(msg: String) {
        logText = "$logText\n$msg"
    }

    fun runFullSetup() {
        if (pairingCode.trim().length < 6 && !adbController.isConnected()) {
            Toast.makeText(context, "Pehle 6-digit pairing code likho", Toast.LENGTH_SHORT).show()
            return
        }

        isWorking = true
        appendLog("Poora setup shuru: connect + Device Owner + saari permissions...")

        scope.launch {
            try {
                if (!adbController.isConnected()) {
                    withContext(Dispatchers.IO) {
                        adbController.pairAndConnectFromCode(pairingCode, object : AdbController.ProgressListener {
                            override fun onLog(message: String) {
                                appendLog(message)
                            }
                        })
                    }
                    isConnected = true
                    appendLog("✅ Pairing Successful! Device connected.")
                    // Show success popup
                    showPairingSuccess = true
                }

                var ownerOk = false
                var ownerMsg = ""
                val workflow = ProvisionWorkflow(context)
                withContext(Dispatchers.IO) {
                    workflow.runDeviceOwnerOnly(object : ProvisionWorkflow.Listener {
                        override fun onLog(message: String) {
                            appendLog(message)
                        }
                        override fun onComplete(success: Boolean, message: String) {
                            ownerOk = success
                            ownerMsg = message
                        }
                    })
                }
                isConnected = adbController.isConnected()
                appendLog(ownerMsg)
                if (ownerOk) {
                    appendLog("Complete: Device Owner + cable wali saari permissions.")
                    Toast.makeText(context, "Full Device Owner ON ho gaya", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Fail: $ownerMsg", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                isConnected = false
                appendLog("Error: ${e.message}")
                appendLog("Check:")
                appendLog("• Same Wi-Fi pe dono phones")
                appendLog("• Wireless debugging ON hai target pe")
                appendLog("• 'Pair with pairing code' dialog KHULA hai")
                appendLog("• Code expire nahi hua (2 min mein expire hota hai)")
                appendLog("• Google account add nahi hai target pe")
                appendLog("Try again: fresh pairing code lein aur button dabain.")
                Toast.makeText(context, "Connection fail: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isWorking = false
            }
        }
    }

    // ─── PAIRING SUCCESS POPUP ─────────────────────────────────────────────
    if (showPairingSuccess) {
        AlertDialog(
            onDismissRequest = { showPairingSuccess = false },
            containerColor = Color(0xFF1A1A1A),
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    "✅ Connected!",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF22C55E)
                )
            },
            text = {
                Text(
                    "Device paired successfully. Setting up permissions...",
                    color = Color(0xFFCBD5E1),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showPairingSuccess = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))
                ) {
                    Text("OK", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Wireless ADB Setup", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextTitle)
                        Text("No cable needed", fontSize = 11.sp, color = TextMuted)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { adbController.disconnect(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextTitle)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CardWhite)
            )
        },
        containerColor = SoftBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ── Connection Status Bar ─────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isConnected) SuccessSurface else DangerSurface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isConnected) Success.copy(alpha = 0.2f) else Danger.copy(alpha = 0.2f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) Success else Danger)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        if (isConnected) "Connected to device" else "Not connected",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (isConnected) Success else Danger
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (isConnected) {
                        TextButton(
                            onClick = {
                                adbController.disconnect()
                                isConnected = false
                                pairingCode = ""
                                appendLog("Disconnected.")
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("DISCONNECT", color = Danger, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ═══ STEP 1: APK INSTALL ══════════════════════════════════════════
            StepCard(step = "1", title = "Install APK on Customer Phone", icon = Icons.Default.Download) {
                Text(
                    "Customer phone pe yeh QR scan karke app download karein. Install complete hone ka wait karein.",
                    fontSize = 13.sp,
                    color = TextMuted,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                        elevation = CardDefaults.cardElevation(2.dp),
                        modifier = Modifier.size(200.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (downloadQrBitmap != null) {
                                Image(
                                    bitmap = downloadQrBitmap.asImageBitmap(),
                                    contentDescription = "Download APK QR",
                                    modifier = Modifier.size(170.dp)
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = BrandBlue, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Loading QR...", fontSize = 11.sp, color = TextMuted)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ═══ STEP 2: PHONE SETUP ═════════════════════════════════════════
            StepCard(step = "2", title = "Prepare Customer Phone", icon = Icons.Default.Settings) {
                SetupInstructionItem("Google account ADD NA karo")
                SetupInstructionItem("Settings → About → Build number 7 dafa tap karo")
                SetupInstructionItem("Developer options → Wireless debugging ON karo")
                SetupInstructionItem("Dono phones same Wi-Fi pe connect karo")
                SetupInstructionItem("Wireless debugging → Pair device with pairing code")
                SetupInstructionItem("Pairing dialog KHULA rakho")
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ═══ STEP 3: CONNECT ═════════════════════════════════════════════
            StepCard(step = "3", title = "Enter Code & Connect", icon = Icons.Default.Link) {
                OutlinedTextField(
                    value = pairingCode,
                    onValueChange = { if (it.length <= 6) pairingCode = it.filter { c -> c.isDigit() } },
                    label = { Text("6-digit pairing code", fontSize = 13.sp) },
                    placeholder = { Text("e.g. 115999") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandBlue,
                        unfocusedBorderColor = BorderLight,
                        focusedContainerColor = SurfaceGray.copy(alpha = 0.5f),
                        unfocusedContainerColor = SurfaceGray.copy(alpha = 0.3f),
                        focusedTextColor = TextTitle,
                        unfocusedTextColor = TextTitle,
                        focusedLabelColor = BrandBlue,
                        unfocusedLabelColor = TextMuted
                    )
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = { runFullSetup() },
                    enabled = !isWorking,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandBlue,
                        disabledContainerColor = BrandBlue,
                        disabledContentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    if (isWorking) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("CONNECTING...", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    } else {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("CONNECT & SETUP", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BrandBlueSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp)) {
                        Icon(Icons.Default.Info, null, tint = BrandBlue, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Yeh button connect karega, Device Owner set karega, SMS/location/overlay/anti-uninstall — cable wali saari permissions automatically.",
                            fontSize = 11.sp,
                            color = BrandBlue,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ═══ STEP 4: GOOGLE FRP PROTECTION ═══ COMMENTED OUT TEMPORARILY ═══
            // var frpSetupDone by remember { mutableStateOf(false) }
            // var frpVerifying by remember { mutableStateOf(false) }
            // var frpAccountFound by remember { mutableStateOf(false) }
            // var frpAccountLocked by remember { mutableStateOf(false) }

            /*
            StepCard(step = "4", title = "Google FRP Protection (IMPORTANT)", icon = Icons.Default.Security) {
                Text(
                    "Google account add karo customer phone pe. Is se FRP (Factory Reset Protection) activate hoga — agar koi somehow factory reset bhi kar le, toh bhi phone unlock NAHI hoga.",
                    fontSize = 13.sp,
                    color = TextMuted,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = WarningSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp)) {
                        Icon(Icons.Default.Warning, null, tint = Warning, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Yeh step BOHOT ZAROORI hai. Account ke bina device fully secure nahi hoga.",
                            fontSize = 11.sp,
                            color = Color(0xFF92400E),
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        scope.launch {
                            try {
                                val shell = AdbShell(context)
                                withContext(Dispatchers.IO) {
                                    shell.run("am start -a android.settings.ADD_ACCOUNT_SETTINGS")
                                }
                                appendLog("→ Account settings opened on customer phone")
                                appendLog("→ Customer phone pe Google account SIGN IN karo")
                                Toast.makeText(context, "Customer phone pe account add karo", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                appendLog("FRP Error: ${e.message}")
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Warning)
                ) {
                    Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("OPEN ACCOUNT SETUP ON CUSTOMER PHONE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        frpVerifying = true
                        scope.launch {
                            try {
                                val shell = AdbShell(context)
                                val result = withContext(Dispatchers.IO) {
                                    shell.run("dumpsys account | grep -m1 'Accounts:'")
                                }
                                frpAccountFound = result.contains("Accounts: 0").not() && result.contains("Accounts:")
                                appendLog("FRP Check: $result")
                                if (frpAccountFound) {
                                    appendLog("✅ Google account found — FRP ACTIVE!")
                                    Toast.makeText(context, "FRP Active — Device Fully Protected!", Toast.LENGTH_LONG).show()
                                } else {
                                    appendLog("❌ No account found — FRP NOT active")
                                    Toast.makeText(context, "Account nahi mila — pehle account add karo", Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Exception) {
                                appendLog("Verify Error: ${e.message}")
                            } finally {
                                frpVerifying = false
                            }
                        }
                    },
                    enabled = !frpVerifying,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                ) {
                    if (frpVerifying) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("VERIFYING...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    } else {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("VERIFY ACCOUNT ADDED", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                if (frpAccountFound) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SuccessSurface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = Success, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Google account found — FRP Active!",
                                fontSize = 12.sp,
                                color = Success,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (!frpAccountLocked) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        val workflow = ProvisionWorkflow(context)
                                        withContext(Dispatchers.IO) {
                                            workflow.lockAccountViaAdb(object : ProvisionWorkflow.Listener {
                                                override fun onLog(message: String) { appendLog(message) }
                                                override fun onComplete(success: Boolean, message: String) {}
                                            })
                                        }
                                        frpAccountLocked = true
                                        appendLog("✅ Account removal locked on customer phone")
                                        Toast.makeText(context, "Account locked — remove nahi ho sakta!", Toast.LENGTH_LONG).show()
                                    } catch (e: Exception) {
                                        appendLog("Lock Error: ${e.message}")
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Success)
                        ) {
                            Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("LOCK ACCOUNT (Prevent Removal)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    } else {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SuccessSurface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, null, tint = Success, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Account locked — cannot be removed!",
                                    fontSize = 12.sp,
                                    color = Success,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
            */

            Spacer(modifier = Modifier.height(20.dp))

            // ═══ LOG OUTPUT ════════════════════════════════════════════════════
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Terminal, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Output Log", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextTitle)
                }
                Surface(
                    onClick = {
                        copyToClipboard(context, logText)
                        Toast.makeText(context, "Logs copied", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = SurfaceGray
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ContentCopy, null, tint = BrandBlue, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy", fontSize = 11.sp, color = BrandBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 130.dp, max = 220.dp)
            ) {
                Text(
                    text = logText,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFE2E8F0),
                    lineHeight = 17.sp,
                    modifier = Modifier
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState())
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  SHARED COMPONENTS
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun StepCard(step: String, title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Step header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = BrandBlue,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(step, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextTitle)
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun SetupInstructionItem(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(BrandBlue)
                .padding(top = 6.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(text, fontSize = 13.sp, color = TextBody, lineHeight = 18.sp)
    }
}

private fun generateQrBitmap(content: String, size: Int): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("ADB Log", text))
}
