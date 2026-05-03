package com.pksafe.lock.manager.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pksafe.lock.manager.data.DeviceResponse
import com.pksafe.lock.manager.ui.theme.*

// Premium Theme Colors
private val SoftBg = Color(0xFFF8FAFC)
private val CardWhite = Color.White
private val BrandBlue = Color(0xFF2563EB)
private val TextTitle = Color(0xFF0F172A)
private val TextMuted = Color(0xFF64748B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    onDeviceClick: (imei: String, name: String) -> Unit,
    viewModel: DeviceListViewModel = viewModel()
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }
    var deviceToToggle by remember { mutableStateOf<DeviceResponse?>(null) }
    var targetStatus by remember { mutableStateOf("") }

    var showEmiSheet by remember { mutableStateOf(false) }
    var selectedDeviceForEmi by remember { mutableStateOf<DeviceResponse?>(null) }

    // Fetch devices on startup
    LaunchedEffect(Unit) {
        viewModel.fetchDevices(context)
    }

    if (showDialog && deviceToToggle != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Confirm ${targetStatus}", fontWeight = FontWeight.Bold, color = TextTitle) },
            text = { Text("Are you sure you want to ${targetStatus.lowercase()} ${deviceToToggle?.customerName ?: ""}'s device?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.toggleLock(context, deviceToToggle!!.imei, targetStatus == "Lock")
                        showDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (targetStatus == "Lock") Color(0xFFDC2626) else BrandBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("YES, CONFIRM", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("CANCEL", color = TextMuted)
                }
            },
            containerColor = CardWhite,
            shape = RoundedCornerShape(20.dp)
        )
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(CardWhite)) {
                CenterAlignedTopAppBar(
                    title = { Text("Customer Base", fontWeight = FontWeight.Black, color = TextTitle, letterSpacing = 0.5.sp) },
                    navigationIcon = {
                        IconButton(onClick = { /* Back handled by NavHost */ }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = TextTitle)
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.fetchDevices(context) }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = BrandBlue)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CardWhite)
                )
                
                // Modern Search Bar
                Box(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Enter Name or IMEI...", color = TextMuted, fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted, modifier = Modifier.size(20.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, null, tint = TextMuted)
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF1F5F9),
                            unfocusedContainerColor = Color(0xFFF1F5F9),
                            focusedBorderColor = BrandBlue,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        singleLine = true
                    )
                }
            }
        },
        containerColor = SoftBg
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (viewModel.isLoading && viewModel.devices.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = BrandBlue)
            } else if (!viewModel.isLoading && viewModel.devices.isEmpty()) {
                EmptyListPlaceholder { viewModel.fetchDevices(context) }
            } else {
                val filteredDevices = viewModel.devices.filter { 
                    it.customerName.contains(searchQuery, ignoreCase = true) || it.imei.contains(searchQuery)
                }
                
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(filteredDevices) { device ->
                        PremiumDeviceCard(
                            device = device,
                            onLockClick = {
                                deviceToToggle = device
                                targetStatus = "Lock"
                                showDialog = true
                            },
                            onUnlockClick = {
                                deviceToToggle = device
                                targetStatus = "Unlock"
                                showDialog = true
                            },
                            onViewDetail = { onDeviceClick(device.imei, device.customerName) },
                            onViewEmiClick = {
                                selectedDeviceForEmi = device
                                showEmiSheet = true
                                viewModel.fetchEmiSchedule(context, device.imei)
                            }
                        )
                    }
                }
                
                // Pull to refresh indicator
                if (viewModel.isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter), color = BrandBlue)
                }
            }
        }
    }

    // Modal Bottom Sheet for EMIs
    if (showEmiSheet && selectedDeviceForEmi != null) {
        ModalBottomSheet(
            onDismissRequest = { showEmiSheet = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            EmiBottomSheetContent(
                viewModel = viewModel,
                context = context,
                selectedDevice = selectedDeviceForEmi!!
            )
        }
    }
}

@Composable
fun PremiumDeviceCard(
    device: DeviceResponse,
    onLockClick: () -> Unit,
    onUnlockClick: () -> Unit,
    onViewDetail: () -> Unit,
    onViewEmiClick: () -> Unit
) {
    val isLocked = device.status.equals("Locked", ignoreCase = true)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Circular Portrait / Icon
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFFEFF6FF), Color(0xFFDBEAFE)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = device.customerName, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = TextTitle)
                    Surface(
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = "IMEI: ${device.imei.take(10)}...",
                            fontSize = 11.sp,
                            color = TextMuted,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                
                // Status Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isLocked) Color(0xFFFEF2F2) else Color(0xFFF0FDF4)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (isLocked) Color(0xFFEF4444) else Color(0xFF22C55E)))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isLocked) "LOCKED" else "ACTIVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isLocked) Color(0xFFDC2626) else Color(0xFF16A34A)
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), thickness = 1.dp, color = Color(0xFFF1F5F9))

            // Grid Info
            Row(modifier = Modifier.fillMaxWidth()) {
                InfoColumn("Phone", device.phoneNumber, Modifier.weight(1f))
                InfoColumn("Installment", "Rs. ${device.emiAmount.toInt()}/mo", Modifier.weight(1f))
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(modifier = Modifier.fillMaxWidth()) {
                InfoColumn("Reg. Date", device.registeredAt?.substringBefore("T") ?: "N/A", Modifier.weight(1f))
                InfoColumn("Tenure", "${device.emiTenure} Months", Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp) // Adjusted spacing slightly
            ) {
                // Detail Button (Primary Action)
                Button(
                    onClick = onViewDetail,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E293B), // Deep Slate
                        contentColor = Color.White 
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(Icons.Default.SettingsSuggest, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("PANEL", fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
                
                // EMI Management Button
                Button(
                    onClick = onViewEmiClick,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandBlue, // Brand Blue
                        contentColor = Color.White 
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(Icons.Default.Payments, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("EMIs", fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
                
                // Quick Lock/Unlock
                QuickIconButton(
                    icon = if(isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                    color = if(isLocked) Color(0xFF16A34A) else Color(0xFFDC2626),
                    onClick = if(isLocked) onUnlockClick else onLockClick,
                    modifier = Modifier.width(55.dp) // Fixed width for aesthetics
                )
            }
        }
    }
}

@Composable
fun InfoColumn(label: String, value: String, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TextTitle)
    }
}

@Composable
fun QuickIconButton(icon: ImageVector, color: Color, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun EmptyListPlaceholder(onRefresh: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Groups, null, modifier = Modifier.size(80.dp), tint = Color(0xFFE2E8F0))
        Spacer(modifier = Modifier.height(16.dp))
        Text("No Customers Found", fontWeight = FontWeight.Black, fontSize = 20.sp, color = TextTitle)
        Text("Try refreshing your list or register a new device.", color = TextMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRefresh,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
        ) {
            Text("REFRESH DATABASE")
        }
    }
}

@Composable
fun EmiBottomSheetContent(
    viewModel: DeviceListViewModel,
    context: android.content.Context,
    selectedDevice: DeviceResponse
) {
    val scheduleData = viewModel.selectedEmiSchedule

    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 20.dp, vertical = 8.dp)) {
        // Handle handle/grabber
        Box(
            modifier = Modifier.width(40.dp).height(4.dp).background(Color(0xFFE2E8F0), CircleShape).align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(16.dp))

    var showRescheduleDialog by remember { mutableStateOf(false) }
    
    // Header
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.size(40.dp).background(BrandBlue.copy(0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.ReceiptLong, null, tint = BrandBlue)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(selectedDevice.customerName, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = TextTitle)
            Text("EMI Schedule & Payments", fontSize = 12.sp, color = TextMuted)
        }
        
        // Reconfigure App Button
        if (scheduleData != null) {
            Surface(
                onClick = { showRescheduleDialog = true },
                shape = CircleShape,
                color = Color(0xFFF1F5F9)
            ) {
                Icon(Icons.Default.EditCalendar, null, tint = BrandBlue, modifier = Modifier.padding(8.dp).size(20.dp))
            }
        }
    }

    if (showRescheduleDialog && scheduleData != null) {
        EmiRescheduleDialog(
            scheduleData = scheduleData,
            onDismiss = { showRescheduleDialog = false },
            onConfirm = { addedDownpayment: Double, newTenure: Int, customAmount: Double ->
                val newDown = scheduleData.downPayment + addedDownpayment
                val newBal = scheduleData.balance - addedDownpayment
                
                val req = com.pksafe.lock.manager.data.RescheduleEmiRequest(
                    emiTenure = newTenure,
                    emiAmount = customAmount,
                    totalPrice = scheduleData.totalPrice,
                    downPayment = newDown,
                    balance = newBal.coerceAtLeast(0.0)
                )
                viewModel.rescheduleEmiPlan(context, scheduleData.imei, req)
                showRescheduleDialog = false
            }
        )
    }

    Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = Color(0xFFF1F5F9))
        
        if (viewModel.isFetchingEmi && scheduleData == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BrandBlue)
            }
        } else if (scheduleData == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Failed to load EMI schedule", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
            }
        } else {
            // Stats Row
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                EmiStatBox("Total Loan", "Rs. ${scheduleData.totalPrice.toInt()}", Color(0xFFF1F5F9), TextTitle, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                EmiStatBox("Paid", "Rs. ${scheduleData.summary.paidTotal.toInt()} (${scheduleData.summary.paid})", Color(0xFFF0FDF4), Color(0xFF16A34A), Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                EmiStatBox("Remaining", "Rs. ${scheduleData.summary.unpaidTotal.toInt()} (${scheduleData.summary.unpaid})", Color(0xFFFEF2F2), Color(0xFFDC2626), Modifier.weight(1f))
            }

            // List of Installments
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(scheduleData.schedule) { installment ->
                    val isPaid = installment.status == "Paid"
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = if(isPaid) Color(0xFFF8FAFC) else CardWhite),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if(isPaid) Color.Transparent else Color(0xFFE2E8F0))
                    ) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            // Month Banner
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(45.dp)) {
                                Text("M${installment.installmentNumber}", fontWeight = FontWeight.Black, fontSize = 14.sp, color = if(isPaid) TextMuted else BrandBlue)
                            }
                            
                            Spacer(Modifier.width(12.dp))
                            
                            // Detis
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Rs. ${installment.amount.toInt()}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = if(isPaid) TextMuted else TextTitle)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Event, null, modifier = Modifier.size(12.dp), tint = TextMuted)
                                    Spacer(Modifier.width(4.dp))
                                    Text(installment.dueDate.substringBefore("T"), fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                                }
                            }
                            
                            // Action / Status
                            if (isPaid) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("PAID", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF16A34A))
                                }
                            } else {
                                Button(
                                    onClick = { viewModel.markEmiAsPaid(context, installment._id, scheduleData.imei) },
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 12.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("MARK PAID", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmiRescheduleDialog(
    scheduleData: com.pksafe.lock.manager.data.EmiScheduleData,
    onDismiss: () -> Unit,
    onConfirm: (extraDown: Double, newTenure: Int, customEmi: Double) -> Unit
) {
    var extraDpStr by remember { mutableStateOf("") }
    var newTenureStr by remember { mutableStateOf(scheduleData.summary.unpaid.toString()) }
    var customEmiStr by remember { mutableStateOf("") } // 0 means auto-calculate

    // Calculate real-time preview
    val extraDp = extraDpStr.toDoubleOrNull() ?: 0.0
    val newBal = (scheduleData.balance - extraDp).coerceAtLeast(0.0)
    val tenure = newTenureStr.toIntOrNull() ?: 1
    val overrideEmi = customEmiStr.toDoubleOrNull()

    val estimatedEmi = overrideEmi ?: if(tenure > 0) newBal / tenure else 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, null, tint = BrandBlue)
                Spacer(Modifier.width(8.dp))
                Text("Reconfigure Plan", fontWeight = FontWeight.Bold, color = TextTitle)
            }
        },
        text = {
            Column {
                Text(
                    "Balance: Rs. ${scheduleData.balance.toInt()}", 
                    fontSize = 13.sp, 
                    fontWeight = FontWeight.Bold, 
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
 
                OutlinedTextField(
                    value = extraDpStr,
                    onValueChange = { extraDpStr = it },
                    label = { Text("Add Down Payment (Optional)", fontSize = 12.sp) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = newTenureStr,
                    onValueChange = { newTenureStr = it },
                    label = { Text("Remaining Tenure (Months)", fontSize = 12.sp) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = customEmiStr,
                    onValueChange = { customEmiStr = it },
                    label = { Text("Custom Monthly EMI (Optional)", fontSize = 12.sp) },
                    placeholder = { Text("Auto-calculates if empty") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFEFF6FF),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("PREVIEW", fontSize = 10.sp, fontWeight = FontWeight.Black, color = BrandBlue)
                        Spacer(Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("New Balance:", fontSize = 12.sp, color = TextMuted)
                            Text("Rs. ${newBal.toInt()}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextTitle)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("New EMI:", fontSize = 12.sp, color = TextMuted)
                            Text("Rs. ${estimatedEmi.toInt()}/mo", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF16A34A))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        extraDpStr.toDoubleOrNull() ?: 0.0,
                        newTenureStr.toIntOrNull() ?: 1,
                        customEmiStr.toDoubleOrNull() ?: estimatedEmi
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("APPLY & RE-GENERATE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = TextMuted)
            }
        },
        containerColor = CardWhite,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun EmiStatBox(label: String, value: String, bgColor: Color, textColor: Color, modifier: Modifier = Modifier) {
    Surface(color = bgColor, shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Black, color = textColor)
        }
    }
}
