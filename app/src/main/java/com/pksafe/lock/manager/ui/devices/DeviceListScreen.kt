package com.pksafe.lock.manager.ui.devices

import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pksafe.lock.manager.data.DeviceResponse
import com.pksafe.lock.manager.ui.theme.*

// ═════════════════════════════════════════════════════════════════════════════
//  PK LOCKER — Device List Screen (Professional Redesign)
//  All business logic preserved. UI completely refreshed.
// ═════════════════════════════════════════════════════════════════════════════

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

    LaunchedEffect(Unit) {
        viewModel.fetchDevices(context)
    }

    // ─── Lock / Unlock Confirmation ──────────────────────────────────────────
    if (showDialog && deviceToToggle != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            icon = {
                Icon(
                    if (targetStatus == "Lock") Icons.Default.Lock else Icons.Default.LockOpen,
                    null,
                    tint = if (targetStatus == "Lock") Danger else Success,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    "${targetStatus} ${deviceToToggle?.customerName ?: ""}?",
                    fontWeight = FontWeight.Bold,
                    color = TextTitle
                )
            },
            text = {
                Text(
                    "Device will be ${targetStatus.lowercase()}ed immediately via FCM push notification.",
                    color = TextMuted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.toggleLock(context, deviceToToggle!!.imei, targetStatus == "Lock")
                        showDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (targetStatus == "Lock") Danger else Success
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("CONFIRM", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("CANCEL", color = TextMuted, fontWeight = FontWeight.Medium)
                }
            },
            containerColor = CardWhite,
            shape = RoundedCornerShape(24.dp)
        )
    }

    // ─── Compute Filtered List ───────────────────────────────────────────────
    val filteredDevices = viewModel.devices.filter {
        it.customerName.contains(searchQuery, ignoreCase = true) || it.imei.contains(searchQuery)
    }
    val lockedCount = viewModel.devices.count { it.status.equals("Locked", ignoreCase = true) }
    val totalDevices = viewModel.devices.size

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(CardWhite)) {
                // ── App Bar ──────────────────────────────────────────────────
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Customers",
                            fontWeight = FontWeight.Bold,
                            color = TextTitle,
                            fontSize = 20.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { /* NavHost handles back */ }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextTitle)
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.fetchDevices(context) }) {
                            Icon(Icons.Default.Refresh, null, tint = BrandBlue)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CardWhite)
                )

                // ── Quick Stats Strip ────────────────────────────────────────
                if (totalDevices > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MiniStatChip(
                            label = "Total",
                            value = "$totalDevices",
                            color = BrandBlue,
                            modifier = Modifier.weight(1f)
                        )
                        MiniStatChip(
                            label = "Active",
                            value = "${totalDevices - lockedCount}",
                            color = Success,
                            modifier = Modifier.weight(1f)
                        )
                        MiniStatChip(
                            label = "Locked",
                            value = "$lockedCount",
                            color = if (lockedCount > 0) Danger else TextMuted,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // ── Search Bar ───────────────────────────────────────────────
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClear = { searchQuery = "" },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
        },
        containerColor = SoftBg
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {

            when {
                // ── Initial Loading ──────────────────────────────────────────
                viewModel.isLoading && viewModel.devices.isEmpty() -> {
                    Box(modifier = Modifier.align(Alignment.Center)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = BrandBlue, strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Loading devices...", color = TextMuted, fontSize = 14.sp)
                        }
                    }
                }

                // ── Empty State ──────────────────────────────────────────────
                !viewModel.isLoading && viewModel.devices.isEmpty() -> {
                    EmptyDevicesState(onRefresh = { viewModel.fetchDevices(context) })
                }

                // ── No Search Results ────────────────────────────────────────
                filteredDevices.isEmpty() && searchQuery.isNotBlank() -> {
                    NoSearchResultsState(query = searchQuery)
                }

                // ── Device List ──────────────────────────────────────────────
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredDevices, key = { it.imei }) { device ->
                            DeviceCard(
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

                    // ── Refreshing Indicator ─────────────────────────────────
                    if (viewModel.isLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                            color = BrandBlue,
                            trackColor = BrandBlueLight
                        )
                    }
                }
            }
        }
    }

    // ─── EMI Bottom Sheet ────────────────────────────────────────────────────
    if (showEmiSheet && selectedDeviceForEmi != null) {
        ModalBottomSheet(
            onDismissRequest = { showEmiSheet = false },
            containerColor = CardWhite,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .background(BorderLight, CircleShape)
                        .padding(top = 12.dp)
                )
            }
        ) {
            EmiBottomSheetContent(
                viewModel = viewModel,
                context = context,
                selectedDevice = selectedDeviceForEmi!!
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  DEVICE CARD
// ═════════════════════════════════════════════════════════════════════════════
@Composable
private fun DeviceCard(
    device: DeviceResponse,
    onLockClick: () -> Unit,
    onUnlockClick: () -> Unit,
    onViewDetail: () -> Unit,
    onViewEmiClick: () -> Unit
) {
    val isLocked = device.status.equals("Locked", ignoreCase = true)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.animateContentSize().padding(20.dp)) {

            // ── Row 1: Avatar + Name + Status ────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                if (isLocked) listOf(Color(0xFFFEE2E2), Color(0xFFFECACA))
                                else listOf(Color(0xFFDBEAFE), Color(0xFFBFDBFE))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = device.customerName.take(1).uppercase(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isLocked) Danger else BrandBlue
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.customerName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Phone, null, tint = TextSubtle, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = device.phoneNumber,
                            fontSize = 12.sp,
                            color = TextMuted,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    // Last Online indicator
                    val lastSeenText = formatLastSeen(device.lastSeen)
                    if (lastSeenText != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (lastSeenText == "Just now" || lastSeenText.endsWith("m ago") || lastSeenText.endsWith("h ago")) Success
                                        else if (lastSeenText == "Offline") Danger
                                        else Color(0xFFF59E0B) // amber for days/weeks
                                    )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = lastSeenText,
                                fontSize = 11.sp,
                                color = TextSubtle,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Status Badge
                StatusBadge(isLocked = isLocked)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Row 2: Key Info Grid ─────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SurfaceGray.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    InfoBlock(
                        label = "IMEI",
                        value = device.imei.take(10) + "...",
                        icon = Icons.Default.SimCard,
                        modifier = Modifier.weight(1f)
                    )
                    VerticalDivider(
                        modifier = Modifier.height(36.dp),
                        color = BorderLight
                    )
                    InfoBlock(
                        label = "EMI",
                        value = "Rs. ${device.emiAmount.toInt()}/mo",
                        icon = Icons.Default.Payments,
                        modifier = Modifier.weight(1f)
                    )
                    VerticalDivider(
                        modifier = Modifier.height(36.dp),
                        color = BorderLight
                    )
                    InfoBlock(
                        label = "Tenure",
                        value = "${device.emiTenure} mo",
                        icon = Icons.Default.Schedule,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Row 3: Action Buttons ────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Control Panel Button
                ActionButton(
                    text = "Panel",
                    icon = Icons.Default.Dashboard,
                    bgColor = Color(0xFF1E293B),
                    contentColor = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = onViewDetail
                )

                // EMI Button
                ActionButton(
                    text = "EMI",
                    icon = Icons.Default.ReceiptLong,
                    bgColor = BrandBlue,
                    contentColor = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = onViewEmiClick
                )

                // Lock / Unlock Quick Action
                QuickLockButton(
                    isLocked = isLocked,
                    onClick = if (isLocked) onUnlockClick else onLockClick
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  SHARED UI COMPONENTS
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun StatusBadge(isLocked: Boolean) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isLocked) DangerLight else SuccessLight
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isLocked) Danger else Success)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isLocked) "LOCKED" else "ACTIVE",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isLocked) Danger else Success,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun MiniStatChip(label: String, value: String, color: Color, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = color)
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Search by name or IMEI...", color = TextSubtle, fontSize = 14.sp) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted, modifier = Modifier.size(20.dp)) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }
        },
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = SurfaceGray,
            unfocusedContainerColor = SurfaceGray,
            focusedBorderColor = BrandBlue,
            unfocusedBorderColor = Color.Transparent,
            focusedTextColor = TextTitle,
            unfocusedTextColor = TextTitle
        ),
        singleLine = true
    )
}

@Composable
private fun InfoBlock(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = TextSubtle, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, fontSize = 10.sp, color = TextSubtle, fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TextTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ActionButton(
    text: String,
    icon: ImageVector,
    bgColor: Color,
    contentColor: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bgColor, contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = 8.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun QuickLockButton(isLocked: Boolean, onClick: () -> Unit) {
    val color = if (isLocked) Success else Danger
    Surface(
        onClick = onClick,
        modifier = Modifier.width(52.dp).height(44.dp),
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                if (isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun EmptyDevicesState(onRefresh: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = BrandBlue.copy(alpha = 0.08f),
            modifier = Modifier.size(100.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PeopleOutline, null, tint = BrandBlue.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("No Customers Yet", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextTitle)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Register your first device to get started\nwith EMI management.",
            color = TextMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = onRefresh,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
            modifier = Modifier.height(48.dp)
        ) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("REFRESH", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun NoSearchResultsState(query: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.SearchOff, null, tint = TextSubtle, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(20.dp))
        Text("No Results Found", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextTitle)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "No devices matching \"$query\"",
            color = TextMuted,
            fontSize = 14.sp
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  EMI BOTTOM SHEET  (logic fully preserved)
// ═════════════════════════════════════════════════════════════════════════════
@Composable
fun EmiBottomSheetContent(
    viewModel: DeviceListViewModel,
    context: android.content.Context,
    selectedDevice: DeviceResponse
) {
    val scheduleData = viewModel.selectedEmiSchedule

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .padding(horizontal = 20.dp)
            .padding(bottom = 8.dp)
    ) {
        var showRescheduleDialog by remember { mutableStateOf(false) }

        // ── Header ──────────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(BrandBlue.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ReceiptLong, null, tint = BrandBlue, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(selectedDevice.customerName, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextTitle)
                Text("EMI Schedule & Payments", fontSize = 12.sp, color = TextMuted)
            }
            if (scheduleData != null) {
                Surface(
                    onClick = { showRescheduleDialog = true },
                    shape = CircleShape,
                    color = SurfaceGray
                ) {
                    Icon(Icons.Default.EditCalendar, null, tint = BrandBlue, modifier = Modifier.padding(10.dp).size(20.dp))
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

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = BorderSoft)

        when {
            viewModel.isFetchingEmi && scheduleData == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = BrandBlue, strokeWidth = 3.dp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Loading schedule...", color = TextMuted, fontSize = 13.sp)
                    }
                }
            }

            scheduleData == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Failed to load EMI schedule", color = Danger, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            else -> {
                // ── Summary Stats ───────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EmiStatBox(
                        "Total Loan",
                        "Rs. ${scheduleData.totalPrice.toInt()}",
                        SurfaceGray,
                        TextTitle,
                        Modifier.weight(1f)
                    )
                    EmiStatBox(
                        "Paid",
                        "Rs. ${scheduleData.summary.paidTotal.toInt()} (${scheduleData.summary.paid})",
                        SuccessSurface,
                        Success,
                        Modifier.weight(1f)
                    )
                    EmiStatBox(
                        "Due",
                        "Rs. ${scheduleData.summary.unpaidTotal.toInt()} (${scheduleData.summary.unpaid})",
                        DangerSurface,
                        Danger,
                        Modifier.weight(1f)
                    )
                }

                // ── Installments List ───────────────────────────────────────
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(scheduleData.schedule) { installment ->
                        val isPaid = installment.status == "Paid"
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isPaid) SurfaceGray.copy(alpha = 0.5f) else CardWhite
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isPaid) Color.Transparent else BorderLight
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Month pill
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isPaid) SurfaceGray else BrandBlueLight,
                                    modifier = Modifier.width(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text(
                                            "M${installment.installmentNumber}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (isPaid) TextMuted else BrandBlue
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Rs. ${installment.amount.toInt()}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = if (isPaid) TextMuted else TextTitle
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Event, null, modifier = Modifier.size(11.dp), tint = TextSubtle)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            installment.dueDate.substringBefore("T"),
                                            fontSize = 11.sp,
                                            color = TextSubtle,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                if (isPaid) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = SuccessLight
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.CheckCircle, null, tint = Success, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("PAID", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Success)
                                        }
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
}

// ═════════════════════════════════════════════════════════════════════════════
//  EMI RESCHEDULE DIALOG  (logic fully preserved)
// ═════════════════════════════════════════════════════════════════════════════
@Composable
fun EmiRescheduleDialog(
    scheduleData: com.pksafe.lock.manager.data.EmiScheduleData,
    onDismiss: () -> Unit,
    onConfirm: (extraDown: Double, newTenure: Int, customEmi: Double) -> Unit
) {
    var extraDpStr by remember { mutableStateOf("") }
    var newTenureStr by remember { mutableStateOf(scheduleData.summary.unpaid.toString()) }
    var customEmiStr by remember { mutableStateOf("") }

    val extraDp = extraDpStr.toDoubleOrNull() ?: 0.0
    val newBal = (scheduleData.balance - extraDp).coerceAtLeast(0.0)
    val tenure = newTenureStr.toIntOrNull() ?: 1
    val overrideEmi = customEmiStr.toDoubleOrNull()
    val estimatedEmi = overrideEmi ?: if (tenure > 0) newBal / tenure else 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, null, tint = BrandBlue)
                Spacer(modifier = Modifier.width(8.dp))
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
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextTitle,
                        unfocusedTextColor = TextTitle,
                        focusedBorderColor = BrandBlue,
                        unfocusedBorderColor = BorderLight,
                        focusedContainerColor = SurfaceGray.copy(alpha = 0.3f),
                        unfocusedContainerColor = SurfaceGray.copy(alpha = 0.2f),
                        focusedLabelColor = BrandBlue,
                        unfocusedLabelColor = TextMuted,
                        cursorColor = BrandBlue
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = newTenureStr,
                    onValueChange = { newTenureStr = it },
                    label = { Text("Remaining Tenure (Months)", fontSize = 12.sp) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextTitle,
                        unfocusedTextColor = TextTitle,
                        focusedBorderColor = BrandBlue,
                        unfocusedBorderColor = BorderLight,
                        focusedContainerColor = SurfaceGray.copy(alpha = 0.3f),
                        unfocusedContainerColor = SurfaceGray.copy(alpha = 0.2f),
                        focusedLabelColor = BrandBlue,
                        unfocusedLabelColor = TextMuted,
                        cursorColor = BrandBlue
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = customEmiStr,
                    onValueChange = { customEmiStr = it },
                    label = { Text("Custom Monthly EMI (Optional)", fontSize = 12.sp) },
                    placeholder = { Text("Auto-calculates if empty", color = TextMuted) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextTitle,
                        unfocusedTextColor = TextTitle,
                        focusedBorderColor = BrandBlue,
                        unfocusedBorderColor = BorderLight,
                        focusedContainerColor = SurfaceGray.copy(alpha = 0.3f),
                        unfocusedContainerColor = SurfaceGray.copy(alpha = 0.2f),
                        focusedLabelColor = BrandBlue,
                        unfocusedLabelColor = TextMuted,
                        cursorColor = BrandBlue
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BrandBlueSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("PREVIEW", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BrandBlue)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("New Balance:", fontSize = 12.sp, color = TextMuted)
                            Text("Rs. ${newBal.toInt()}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextTitle)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("New EMI:", fontSize = 12.sp, color = TextMuted)
                            Text("Rs. ${estimatedEmi.toInt()}/mo", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Success)
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
                shape = RoundedCornerShape(12.dp)
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
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun EmiStatBox(label: String, value: String, bgColor: Color, textColor: Color, modifier: Modifier = Modifier) {
    Surface(color = bgColor, shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = TextMuted)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Formats a lastSeen ISO timestamp into a human-readable "time ago" string.
 * Returns null if the timestamp is missing or unparseable.
 */
fun formatLastSeen(lastSeen: String?): String? {
    if (lastSeen.isNullOrBlank()) return null
    return try {
        val seenTime = java.time.Instant.parse(lastSeen)
        val now = java.time.Instant.now()
        val duration = java.time.Duration.between(seenTime, now)
        when {
            duration.toMinutes() < 1 -> "Just now"
            duration.toMinutes() < 60 -> "${duration.toMinutes()}m ago"
            duration.toHours() < 24 -> "${duration.toHours()}h ago"
            duration.toDays() < 7 -> "${duration.toDays()}d ago"
            duration.toDays() < 30 -> "${duration.toDays() / 7}w ago"
            else -> "Offline"
        }
    } catch (_: Exception) { null }
}
