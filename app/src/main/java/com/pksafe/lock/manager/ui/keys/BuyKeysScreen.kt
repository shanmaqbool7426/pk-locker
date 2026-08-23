package com.pksafe.lock.manager.ui.keys

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pksafe.lock.manager.data.*
import com.pksafe.lock.manager.ui.theme.*

// ═════════════════════════════════════════════════════════════════════════════
//  PK LOCKER — Buy Keys Screen (Professional Redesign)
//  All business logic preserved. UI completely refreshed.
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuyKeysScreen(
    onBack: () -> Unit,
    viewModel: BuyKeysViewModel = viewModel()
) {
    val context = LocalContext.current
    var showQrDialog by remember { mutableStateOf(false) }

    val keysCount = viewModel.numKeys.toIntOrNull() ?: 0
    // Dynamic pricing matching backend: Retail (430), Dealer 50+ (400), Wholesale 100+ (380)
    val unitPrice = when {
        keysCount >= 100 -> 380
        keysCount >= 50 -> 400
        else -> 430
    }
    val totalAmount = keysCount * unitPrice

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.handleImageSelection(context, it) }
    }

    LaunchedEffect(Unit) { viewModel.fetchHistory(context) }

    LaunchedEffect(viewModel.message) {
        viewModel.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.message = null
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Purchase Keys", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextTitle)
                        Text("License top-up for device locking", fontSize = 11.sp, color = TextMuted)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {

            // ═══ PAYMENT METHODS ═══════════════════════════════════════════════
            Text(
                "Payment Methods",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = TextTitle,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = WarningSurface),
                border = BorderStroke(1.dp, Warning.copy(alpha = 0.2f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // JazzCash Merchant
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Warning,
                            shape = CircleShape,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.AccountBalance, null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("JAZZCASH MERCHANT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Warning, letterSpacing = 0.5.sp)
                            Text("Till ID: 9829158", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextTitle)
                        }
                        IconButton(onClick = { showQrDialog = true }) {
                            Icon(Icons.Default.QrCode, null, tint = TextTitle, modifier = Modifier.size(24.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Warning.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(14.dp))

                    // Bank IBAN
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = BrandBlueLight,
                            shape = CircleShape,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.AccountBalance, null, tint = BrandBlue, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("BANK IBAN (RAAST)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSubtle, letterSpacing = 0.5.sp)
                            Text("PK90JCMA0507923069829158", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextTitle)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ═══ SELECT PACKAGE ════════════════════════════════════════════════
            Text(
                "Select Package",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = TextTitle,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val packages = listOf(
                    Triple("10", "Starter", "Rs.430/key"),
                    Triple("50", "Dealer", "Rs.400/key"),
                    Triple("100", "Pro", "Rs.380/key"),
                    Triple("300", "Bulk", "Rs.380/key")
                )
                items(packages) { (pkg, label, priceLabel) ->
                    val isSelected = viewModel.numKeys == pkg
                    val price = priceLabel
                    Surface(
                        modifier = Modifier.width(82.dp).clickable { viewModel.numKeys = pkg },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) BrandBlue else CardWhite,
                        border = if (isSelected) null else BorderStroke(1.dp, BorderLight),
                        shadowElevation = if (isSelected) 4.dp else 0.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                pkg,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else TextTitle
                            )
                            Text(
                                "Keys",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isSelected) Color.White.copy(alpha = 0.7f) else TextMuted
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) Color.White.copy(alpha = 0.15f) else SurfaceGray
                            ) {
                                Text(
                                    price,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else TextMuted,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Custom input
            OutlinedTextField(
                value = viewModel.numKeys,
                onValueChange = { if (it.all { c -> c.isDigit() }) viewModel.numKeys = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Or enter custom amount", fontSize = 13.sp) },
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandBlue,
                    unfocusedBorderColor = BorderLight,
                    focusedContainerColor = SurfaceGray.copy(alpha = 0.3f),
                    unfocusedContainerColor = SurfaceGray.copy(alpha = 0.2f),
                    focusedTextColor = TextTitle,
                    unfocusedTextColor = TextTitle,
                    focusedLabelColor = BrandBlue,
                    unfocusedLabelColor = TextMuted,
                    cursorColor = BrandBlue
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ═══ PAYMENT SCREENSHOT ════════════════════════════════════════════
            Text(
                "Payment Proof",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = TextTitle,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            Surface(
                modifier = Modifier.fillMaxWidth().clickable { imagePickerLauncher.launch("image/*") },
                shape = RoundedCornerShape(18.dp),
                color = if (viewModel.screenshotBase64 != null) SuccessSurface else CardWhite,
                border = BorderStroke(
                    2.dp,
                    if (viewModel.screenshotBase64 != null) Success.copy(alpha = 0.3f) else BorderLight
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        if (viewModel.screenshotBase64 != null) Icons.Default.CheckCircle else Icons.Default.AddPhotoAlternate,
                        null,
                        tint = if (viewModel.screenshotBase64 != null) Success else TextSubtle,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        if (viewModel.screenshotBase64 != null) "Screenshot Attached!" else "Upload Payment Screenshot",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (viewModel.screenshotBase64 != null) Success else TextTitle
                    )
                    Text(
                        "Tap to select from gallery",
                        fontSize = 12.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ═══ ORDER SUMMARY ═════════════════════════════════════════════════
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A))), RoundedCornerShape(20.dp))
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("PAYABLE AMOUNT", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("PKR $totalAmount", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            if (keysCount > 0) {
                                Text(
                                    "$keysCount keys × Rs.$unitPrice",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Button(
                            onClick = { viewModel.submitRequest(context) },
                            enabled = !viewModel.isLoading && keysCount > 0,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandBlue,
                                disabledContainerColor = BrandBlue,
                                disabledContentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            if (viewModel.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Submitting...", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                            } else {
                                Icon(Icons.Default.Send, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Submit", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ═══ ORDER HISTORY ═════════════════════════════════════════════════
            Text(
                "Recent Orders",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = TextTitle,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            if (viewModel.history.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    border = BorderStroke(1.dp, BorderLight)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.ReceiptLong, null, tint = TextSubtle, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No orders yet", color = TextMuted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            } else {
                viewModel.history.forEach { order ->
                    KeyOrderRow(order)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    // ─── QR Dialog ──────────────────────────────────────────────────────────
    if (showQrDialog) {
        Dialog(onDismissRequest = { showQrDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = CardWhite,
                modifier = Modifier.padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Merchant QR Code", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextTitle)
                    Spacer(modifier = Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .background(SurfaceGray, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.QrCode2, null, modifier = Modifier.size(80.dp), tint = TextSubtle)
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { showQrDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                    ) { Text("Close", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  ORDER HISTORY ROW
// ═════════════════════════════════════════════════════════════════════════════
@Composable
private fun KeyOrderRow(order: KeyOrderData) {
    val statusColor = when (order.status.lowercase()) {
        "approved", "success", "completed" -> Success
        "pending" -> Warning
        else -> Danger
    }
    val statusBg = when (order.status.lowercase()) {
        "approved", "success", "completed" -> SuccessLight
        "pending" -> WarningLight
        else -> DangerLight
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, BorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = SurfaceGray,
                shape = CircleShape,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.VpnKey, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("${order.numKeys} Keys", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextTitle)
                Text("PKR ${order.totalAmount}", fontSize = 12.sp, color = TextMuted)
            }
            Surface(
                color = statusBg,
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        order.status.uppercase(),
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp
                    )
                }
            }
        }
    }
}
