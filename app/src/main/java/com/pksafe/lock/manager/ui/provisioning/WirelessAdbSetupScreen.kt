package com.pksafe.lock.manager.ui.provisioning

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.pksafe.lock.manager.util.AdbController
import com.pksafe.lock.manager.util.Constants
import com.pksafe.lock.manager.util.ProvisionWorkflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ThemeBrown = Color(0xFF8B4513)
private val LogBgColor = Color(0xFFF1F5F9)
private val TextSubColor = Color(0xFF555555)

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
                    appendLog("Connected. Ab Device Owner laga raha hoon...")
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

    Scaffold(
        topBar = {
            TopBar(onBack = {
                adbController.disconnect()
                onBack()
            })
        },
        containerColor = Color.White
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                "Asaan Wireless Setup",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color.Black
            )
            Text(
                "Cable nahi. Device Owner + lock permissions cable jaisi.",
                fontSize = 13.sp,
                color = TextSubColor,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            StepCard("1", "APK install") {
                Text(
                    "Customer is QR se app download kare. Install complete hone ka wait karo.",
                    fontSize = 12.5.sp,
                    color = TextSubColor,
                    lineHeight = 17.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        elevation = CardDefaults.cardElevation(2.dp),
                        modifier = Modifier.size(200.dp)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (downloadQrBitmap != null) {
                                Image(
                                    bitmap = downloadQrBitmap.asImageBitmap(),
                                    contentDescription = "Download APK QR",
                                    modifier = Modifier.size(170.dp)
                                )
                            } else {
                                Text("QR load...", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            StepCard("2", "Customer phone (2 minute)") {
                Text(
                    "• Google account ADD NA karo\n" +
                        "• Settings → About → Build number 7 dafa\n" +
                        "• Developer options → Wireless debugging ON\n" +
                        "• Dono phones same Wi-Fi\n" +
                        "• Wireless debugging → Pair device with pairing code\n" +
                        "• Dialog KHULA rakho",
                    fontSize = 12.5.sp,
                    color = TextSubColor,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            StepCard("3", "Code + 1 button") {
                OutlinedTextField(
                    value = pairingCode,
                    onValueChange = { if (it.length <= 6) pairingCode = it.filter { c -> c.isDigit() } },
                    label = { Text("6-digit pairing code") },
                    placeholder = { Text("115999") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ThemeBrown,
                        unfocusedBorderColor = Color.LightGray
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { runFullSetup() },
                    enabled = !isWorking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ThemeBrown)
                ) {
                    if (isWorking) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("CHAL RAHA HAI...", fontWeight = FontWeight.Bold)
                    } else {
                        Text("CONNECT + DEVICE OWNER ON", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
                Text(
                    "Yeh button connect karega, Device Owner set karega, SMS/location/overlay/anti-uninstall — cable wali saari permissions.",
                    fontSize = 11.sp,
                    color = TextSubColor,
                    modifier = Modifier.padding(top = 8.dp),
                    lineHeight = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            if (isConnected) Color(0xFF22C55E) else Color(0xFFEF4444),
                            shape = RoundedCornerShape(5.dp)
                        )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isConnected) "Connected" else "Not connected",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            TextButton(
                onClick = {
                    adbController.disconnect()
                    isConnected = false
                    pairingCode = ""
                    appendLog("Disconnected.")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("DISCONNECT", color = ThemeBrown, fontWeight = FontWeight.Bold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Log", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            copyToClipboard(context, logText)
                            Toast.makeText(context, "Logs copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, "Copy", tint = ThemeBrown, modifier = Modifier.size(18.dp))
                    }
                    Text("Copy", fontSize = 12.sp, color = ThemeBrown, fontWeight = FontWeight.SemiBold)
                }
            }

            Surface(
                color = LogBgColor,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 200.dp)
            ) {
                Text(
                    text = logText,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF334155),
                    lineHeight = 16.sp,
                    modifier = Modifier
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(onBack: () -> Unit) {
    TopAppBar(
        title = {
            Text("Asaan Wireless Setup", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Black)
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.Black)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
    )
}

@Composable
private fun StepCard(num: String, title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
        border = BorderStroke(1.dp, Color(0xFFFDE68A))
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("$num. $title", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ThemeBrown)
            Spacer(Modifier.height(8.dp))
            content()
        }
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
