package com.pksafe.lock.manager.ui.provisioning

import android.nfc.NfcAdapter
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.TapAndPlay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pksafe.lock.manager.util.NfcProvisioner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcSetupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val nfcAdapter = remember { NfcAdapter.getDefaultAdapter(context) }
    // val nfcProvisioner = remember { NfcProvisioner(context) }

    LaunchedEffect(Unit) {
        if (nfcAdapter == null) {
            Toast.makeText(context, "NFC is not supported on this device", Toast.LENGTH_LONG).show()
        } else if (!nfcAdapter.isEnabled) {
            Toast.makeText(context, "Please enable NFC in settings", Toast.LENGTH_LONG).show()
        } else {
            // NOTE: setNdefPushMessageCallback is deprecated and removed in recent Android versions.
            // For now, we are commenting this out to allow the project to build.
            // nfcAdapter.setNdefPushMessageCallback(nfcProvisioner, context as? android.app.Activity)
            Toast.makeText(context, "NFC Beaming is not supported on this Android version", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NFC Master Setup") },
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
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF3B82F6).copy(alpha = 0.1f),
                    border = BorderStroke(2.dp, Color(0xFF3B82F6)),
                    modifier = Modifier.fillMaxSize()
                ) {}
                Icon(
                    imageVector = Icons.Default.TapAndPlay,
                    contentDescription = null,
                    modifier = Modifier.size(60.dp),
                    tint = Color(0xFF3B82F6)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "READY TO BUMP",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Naye phone ko is phone ke sath back-to-back touch karein jab wo 'Welcome' screen par ho.",
                textAlign = TextAlign.Center,
                color = Color.Gray,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(48.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color(0xFF333333)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "INSTRUCTIONS / HIDAYAT",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color.Gray,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    InstructionItem("1", "Naye phone ko Factory Reset karein")
                    InstructionItem("2", "Dono phones ka NFC 'ON' hona chahiye")
                    InstructionItem("3", "Naye phone ko 'Welcome' screen par rakhein")
                    InstructionItem("4", "Phones ko back-to-back touch karein")
                    InstructionItem("5", "Screen par 'Touch to Beam' aaye toh tap karein")
                }
            }
        }
    }
}

@Composable
fun InstructionItem(num: String, text: String) {
    Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF3B82F6),
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(num, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, color = Color.White, fontSize = 14.sp)
    }
}
