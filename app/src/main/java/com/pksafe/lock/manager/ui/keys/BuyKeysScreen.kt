package com.pksafe.lock.manager.ui.keys

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pksafe.lock.manager.data.*
import com.pksafe.lock.manager.util.Constants
import com.pksafe.lock.manager.ui.dashboard.*
import com.pksafe.lock.manager.ui.emi.PrimaryBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuyKeysScreen(
    onBack: () -> Unit,
    viewModel: BuyKeysViewModel = viewModel()
) {
    val context = LocalContext.current
    var showQrDialog by remember { mutableStateOf(false) }
    
    val keysCount = viewModel.numKeys.toIntOrNull() ?: 0
    val unitPrice = if (keysCount >= 300) 240 else 400
    val totalAmount = keysCount * unitPrice

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.handleImageSelection(context, it) }
    }

    LaunchedEffect(Unit) { viewModel.fetchHistory(context) }

    // Toast Messages
    LaunchedEffect(viewModel.message) {
        viewModel.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.message = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Purchase Keys", fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Text("SECURE LICENSE TOP-UP", fontSize = 10.sp, color = PrimaryBlue, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF8FAFC)
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // --- Payment Methods Section (NO PHONE NUMBER EXPOSED) ---
            Text("Payment Methods", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFF1E293B))
            Spacer(Modifier.height(12.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFFFEB3B).copy(0.1f),
                border = BorderStroke(1.dp, Color(0xFFFFEB3B)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = Color(0xFFFFEB3B), shape = CircleShape, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.AccountBalance, null, tint = Color(0xFF856404), modifier = Modifier.padding(10.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("JAZZCASH MERCHANT TILL", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF856404))
                            Text("TILL ID: 9829158", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFF1E293B))
                        }
                        IconButton(onClick = { showQrDialog = true }) {
                            Icon(Icons.Default.QrCode, null, tint = Color(0xFF1E293B))
                        }
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    Divider(color = Color(0xFF856404).copy(0.1f))
                    Spacer(Modifier.height(12.dp))

                    Text("BANK IBAN (RAAST SUPPORTED)", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Gray)
                    Text("PK90JCMA0507923069829158", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                }
            }

            Spacer(Modifier.height(24.dp))

            // --- Order Input ---
            Text("Order Details", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFF1E293B))
            Spacer(Modifier.height(12.dp))
            
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val packages = listOf("10", "50", "100", "300")
                items(packages) { pkg ->
                    val isSelected = viewModel.numKeys == pkg
                    Surface(
                        modifier = Modifier.width(80.dp).clickable { viewModel.numKeys = pkg },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) PrimaryBlue else Color.White,
                        border = if (isSelected) null else BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(modifier = Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(pkg, fontSize = 18.sp, fontWeight = FontWeight.Black, color = if(isSelected) Color.White else Color(0xFF1E293B))
                            Text("KEYS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if(isSelected) Color.White.copy(0.7f) else Color.Gray)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = viewModel.numKeys,
                onValueChange = { if (it.all { char -> char.isDigit() }) viewModel.numKeys = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Enter Number of Keys") },
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue)
            )

            Spacer(Modifier.height(24.dp))

            // --- Screenshot Upload ---
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { imagePickerLauncher.launch("image/*") },
                shape = RoundedCornerShape(20.dp),
                color = if (viewModel.screenshotBase64 != null) Color(0xFFECFDF5) else Color.White,
                border = BorderStroke(2.dp, if (viewModel.screenshotBase64 != null) Color(0xFF10B981) else Color(0xFFE2E8F0))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        if (viewModel.screenshotBase64 != null) Icons.Default.CheckCircle else Icons.Default.AddPhotoAlternate,
                        null,
                        tint = if (viewModel.screenshotBase64 != null) Color(0xFF10B981) else Color.Gray,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (viewModel.screenshotBase64 != null) "Screenshot Attached!" else "Upload Payment Screenshot",
                        fontWeight = FontWeight.Bold,
                        color = if (viewModel.screenshotBase64 != null) Color(0xFF10B981) else Color(0xFF1E293B)
                    )
                    Text("Tap to select from gallery", fontSize = 12.sp, color = Color.Gray)
                }
            }

            Spacer(Modifier.height(24.dp))

            // --- Order Summary Card ---
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF1E293B)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("PAYABLE AMOUNT", color = Color.White.copy(0.5f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                            Text("PKR $totalAmount", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        }
                        Button(
                            onClick = { viewModel.submitRequest(context) },
                            enabled = !viewModel.isLoading && keysCount > 0,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (viewModel.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text("Submit Request", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // --- History Section ---
            Text("Recent Orders", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFF1E293B))
            Spacer(Modifier.height(12.dp))
            
            if (viewModel.history.isEmpty()) {
                Text("No orders yet", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            } else {
                viewModel.history.forEach { order ->
                    KeyOrderCompactRow(order)
                    Spacer(Modifier.height(10.dp))
                }
            }
            
            Spacer(Modifier.height(40.dp))
        }
    }

    // QR Dialog
    if (showQrDialog) {
        Dialog(onDismissRequest = { showQrDialog = false }) {
            Surface(shape = RoundedCornerShape(24.dp), color = Color.White, modifier = Modifier.padding(20.dp)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Merchant QR", fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Spacer(Modifier.height(20.dp))
                    Box(modifier = Modifier.size(200.dp).background(Color(0xFFF8FAFC), RoundedCornerShape(16.dp)).border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.QrCode2, null, modifier = Modifier.size(100.dp), tint = Color.LightGray)
                    }
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { showQrDialog = false }, modifier = Modifier.fillMaxWidth()) { Text("Close") }
                }
            }
        }
    }
}

@Composable
fun KeyOrderCompactRow(order: KeyOrderData) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Color(0xFFF8FAFC), shape = CircleShape, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Receipt, null, modifier = Modifier.padding(10.dp), tint = Color.Gray)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("${order.numKeys} Keys", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("PKR ${order.totalAmount}", fontSize = 12.sp, color = Color.Gray)
            }
            Surface(
                color = when(order.status.lowercase()) {
                    "approved", "success", "completed" -> Color(0xFFDCFCE7)
                    "pending" -> Color(0xFFFEF9C3)
                    else -> Color(0xFFFEF2F2)
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    order.status.uppercase(),
                    color = when(order.status.lowercase()) {
                        "approved", "success", "completed" -> Color(0xFF15803D)
                        "pending" -> Color(0xFF854D0E)
                        else -> Color(0xFFB91C1C)
                    },
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
