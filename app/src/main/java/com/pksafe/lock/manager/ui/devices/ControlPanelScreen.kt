package com.pksafe.lock.manager.ui.devices

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.pksafe.lock.manager.data.ApiService
import com.pksafe.lock.manager.data.DeviceResponse
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// ═════════════════════════════════════════════════════════════════════════════
//  PK LOCKER — Professional Device Control Panel
//  3 Clean Tabs: Actions | Customer | Location
//  Same logic, cleaner UI.
// ═════════════════════════════════════════════════════════════════════════════

private val BgGray        = Color(0xFFF8FAFC)
private val CardWhite     = Color.White
private val Primary       = Color(0xFF2563EB)
private val PrimaryDark   = Color(0xFF1D4ED8)
private val TextPrimary   = Color(0xFF0F172A)
private val TextSecondary = Color(0xFF64748B)
private val Success       = Color(0xFF10B981)
private val Danger        = Color(0xFFEF4444)
private val Warning       = Color(0xFFF59E0B)
private val Border        = Color(0xFFE2E8F0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlPanelScreen(
    imei: String,
    customerName: String,
    onBack: () -> Unit,
    viewModel: DeviceListViewModel = viewModel()
) {
    val context = LocalContext.current
    val device = viewModel.devices.find { it.imei == imei }

    LaunchedEffect(Unit) {
        viewModel.fetchDevices(context)
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Actions" to Icons.Default.TouchApp, "Customer" to Icons.Default.Person, "Location" to Icons.Default.LocationOn, "Activity" to Icons.Default.History)

    var showLockDialog by remember { mutableStateOf(false) }
    var pendingLockState by remember { mutableStateOf(false) }
    var isOnlineMode by remember { mutableStateOf(true) }

    if (showLockDialog) {
        LockConfirmDialog(
            isLocking = pendingLockState,
            isOnline = isOnlineMode,
            customerName = customerName,
            onConfirm = {
                viewModel.toggleLock(context, imei, pendingLockState)
                showLockDialog = false
            },
            onDismiss = { showLockDialog = false }
        )
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(CardWhite)) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = customerName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = TextPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.fetchDevices(context) }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Primary)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CardWhite)
                )

                // Customer status row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFFDBEAFE), Color(0xFFBFDBFE)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = customerName.take(1).uppercase(),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = device?.phoneNumber ?: "No phone number",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        StatusChip(status = device?.status ?: "Unknown")
                        // Last Online indicator
                        val lastSeenText = formatLastSeen(device?.lastSeen)
                        if (lastSeenText != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (lastSeenText == "Just now" || lastSeenText.endsWith("m ago") || lastSeenText.endsWith("h ago")) Success
                                            else if (lastSeenText == "Offline") Danger
                                            else Color(0xFFF59E0B)
                                        )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Last online: $lastSeenText",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "IMEI: ${imei.take(12)}...",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        // Command Delivery Status
                        val cmd = device?.lastCommand
                        if (cmd != null) {
                            val sentAt = device.lastCommandSentAt
                            val ackAt = device.lastCommandAckAt
                            val isDelivered = ackAt != null && sentAt != null && ackAt >= sentAt
                            val label = when (cmd) {
                                "lock" -> "Lock"
                                "unlock", "state_change" -> "Unlock"
                                "deregister" -> "Deregister"
                                "unlock_all" -> "Unlock All"
                                else -> cmd.replaceFirstChar { it.uppercase() }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isDelivered) Success.copy(alpha = 0.1f) else Color(0xFFF59E0B).copy(alpha = 0.1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isDelivered) Icons.Default.CheckCircle else Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = if (isDelivered) Success else Color(0xFFF59E0B),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "$label: ${if (isDelivered) "Delivered" else "Pending"}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isDelivered) Success else Color(0xFFF59E0B)
                                    )
                                }
                            }
                        }
                    }
                }

                // Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = CardWhite,
                    contentColor = Primary,
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, (title, icon) ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 11.sp,
                                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            },
                            selectedContentColor = Primary,
                            unselectedContentColor = TextSecondary
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = CardWhite,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BottomActionButton(
                        text = "Lock Device",
                        icon = Icons.Default.Lock,
                        color = Danger,
                        modifier = Modifier.weight(1f),
                        onClick = { pendingLockState = true; showLockDialog = true }
                    )
                    BottomActionButton(
                        text = "Unlock Device",
                        icon = Icons.Default.LockOpen,
                        color = Success,
                        modifier = Modifier.weight(1f),
                        onClick = { pendingLockState = false; showLockDialog = true }
                    )
                }
            }
        },
        containerColor = BgGray
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (selectedTab) {
                0 -> ActionsTab(
                    viewModel = viewModel,
                    device = device,
                    imei = imei,
                    isOnlineMode = isOnlineMode,
                    onModeChange = { isOnlineMode = it }
                )
                1 -> CustomerTab(device = device, viewModel = viewModel, imei = imei)
                2 -> LocationTab(device = device, viewModel = viewModel, imei = imei)
                3 -> ActivityTab(imei = imei)
            }

            if (viewModel.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Primary)
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  STATUS CHIP
// ═════════════════════════════════════════════════════════════════════════════
@Composable
private fun StatusChip(status: String) {
    val isLocked = status.equals("Locked", ignoreCase = true)
    val bgColor = if (isLocked) Color(0xFFFEE2E2) else Color(0xFFD1FAE5)
    val textColor = if (isLocked) Danger else Success

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(textColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = status.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  LOCK CONFIRM DIALOG
// ═════════════════════════════════════════════════════════════════════════════
@Composable
private fun LockConfirmDialog(
    isLocking: Boolean,
    isOnline: Boolean,
    customerName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isLocking) "Lock $customerName?" else "Unlock $customerName?",
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Text(
                text = if (isLocking) {
                    "Device will be locked immediately. ${if (isOnline) "Command sent via internet." else "Command sent via SMS."}"
                } else {
                    "Device will be unlocked immediately. ${if (isOnline) "Command sent via internet." else "Command sent via SMS."}"
                },
                color = TextSecondary
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = if (isLocking) Danger else Success),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Confirm", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = CardWhite,
        shape = RoundedCornerShape(20.dp)
    )
}

// ═════════════════════════════════════════════════════════════════════════════
//  ACTIONS TAB
// ═════════════════════════════════════════════════════════════════════════════
@Composable
private fun ActionsTab(
    viewModel: DeviceListViewModel,
    device: DeviceResponse?,
    imei: String,
    isOnlineMode: Boolean,
    onModeChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Online / Offline mode selector
        ModeSelector(
            isOnline = isOnlineMode,
            onOnlineClick = { onModeChange(true) },
            onOfflineClick = { onModeChange(false) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isOnlineMode) {
            OnlineActionsContent(viewModel, device, imei)
        } else {
            OfflineActionsContent(device = device)
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun ModeSelector(
    isOnline: Boolean,
    onOnlineClick: () -> Unit,
    onOfflineClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.padding(6.dp)) {
            ModeButton(
                text = "Online",
                icon = Icons.Default.CloudSync,
                isSelected = isOnline,
                modifier = Modifier.weight(1f),
                onClick = onOnlineClick
            )
            ModeButton(
                text = "Offline SMS",
                icon = Icons.Default.Sms,
                isSelected = !isOnline,
                modifier = Modifier.weight(1f),
                onClick = onOfflineClick
            )
        }
    }
}

@Composable
private fun ModeButton(
    text: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) Primary else Color.Transparent
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = if (isSelected) Color.White else TextSecondary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                color = if (isSelected) Color.White else TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun OnlineActionsContent(
    viewModel: DeviceListViewModel,
    device: DeviceResponse?,
    imei: String
) {
    val context = LocalContext.current

    var showUnlockAllDialog by remember { mutableStateOf(false) }
    var showDeregisterDialog by remember { mutableStateOf(false) }

    if (showUnlockAllDialog) {
        AlertDialog(
            onDismissRequest = { showUnlockAllDialog = false },
            title = { Text("Reset All Controls?", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = { Text("All restrictions (USB, Camera, Apps) will be cleared.", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.unlockAllControls(context, imei)
                        showUnlockAllDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Reset All", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlockAllDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CardWhite,
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showDeregisterDialog) {
        AlertDialog(
            onDismissRequest = { showDeregisterDialog = false },
            title = { Text("Deregister Device?", fontWeight = FontWeight.Bold, color = Danger) },
            text = {
                Text(
                    "This device will be permanently removed and all restrictions cleared. The customer's device will be unlocked and freed.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeregisterDialog = false
                        viewModel.deregisterDevice(context, imei) { }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Danger),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Yes, Deregister", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeregisterDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CardWhite,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // ── SMS Fallback Dialog (shown after deregister) ──────────────────────
    val result = viewModel.deregisterResult
    if (result != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearDeregisterResult() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (result.fcmDelivered) Icons.Default.CheckCircle else Icons.Default.Warning,
                        null,
                        tint = if (result.fcmDelivered) Success else Warning,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (result.fcmDelivered) "Deregistered" else "SMS Bhejein",
                        fontWeight = FontWeight.Bold,
                        color = if (result.fcmDelivered) Success else Warning
                    )
                }
            },
            text = {
                if (result.fcmDelivered) {
                    Text(
                        "FCM command customer device tak bhej diya gaya hai. App automatic remove ho jayega.",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                } else {
                    Column {
                        Text(
                            "Customer ke device tak FCM nahi pohncha. App remove karne ke liye SMS bhejein:",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val phone = result.smsFallback?.customerPhone ?: ""
                        if (phone.isNotEmpty()) {
                            Text("Customer: $phone", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        val smsCode = "DEREGISTER#${result.smsFallback?.code ?: ""}"
                        Surface(
                            color = Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                smsCode,
                                fontSize = 10.sp,
                                color = TextPrimary,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Yeh SMS customer ke phone pe bhejein — app automatic remove ho jayega.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            },
            confirmButton = {
                if (result.fcmDelivered) {
                    Button(
                        onClick = { viewModel.clearDeregisterResult() },
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val smsCode = "DEREGISTER#${result.smsFallback?.code ?: ""}"
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("SMS Code", smsCode))
                                Toast.makeText(context, "Code copied!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy", fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                val phone = result.smsFallback?.customerPhone ?: ""
                                val smsCode = "DEREGISTER#${result.smsFallback?.code ?: ""}"
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("smsto:$phone")
                                    putExtra("sms_body", smsCode)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "SMS app not available", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Send, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Send SMS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            dismissButton = if (!result.fcmDelivered) {
                { TextButton(onClick = { viewModel.clearDeregisterResult() }) { Text("Later", color = TextSecondary) } }
            } else null,
            containerColor = CardWhite,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Security Controls
    SectionCard(title = "Security Controls") {
        ControlSwitch(
            label = "Auto-Lock on No Network",
            icon = Icons.Default.GppMaybe,
            checked = device?.controls?.autoLock ?: false,
            onCheckedChange = { viewModel.sendControl(context, imei, "autoLock", it) }
        )
        ControlDivider()
        ControlSwitch(
            label = "Auto-Lock on SIM Change",
            icon = Icons.Default.SimCardAlert,
            checked = device?.controls?.autoLockOnSimChange ?: false,
            onCheckedChange = { viewModel.sendControl(context, imei, "autoLockOnSimChange", it) }
        )
        ControlDivider()
        ControlSwitch(
            label = "Block USB Connection",
            icon = Icons.Default.Usb,
            checked = device?.controls?.usbLock ?: false,
            onCheckedChange = { viewModel.sendControl(context, imei, "usbLock", it) }
        )
        ControlDivider()
        ControlSwitch(
            label = "Block Camera",
            icon = Icons.Default.CameraAlt,
            checked = device?.controls?.cameraDisabled ?: false,
            onCheckedChange = { viewModel.sendControl(context, imei, "cameraDisabled", it) }
        )
        ControlDivider()
        ControlSwitch(
            label = "Block App Installation",
            icon = Icons.Default.AppRegistration,
            checked = device?.controls?.installBlocked ?: false,
            onCheckedChange = { viewModel.sendControl(context, imei, "installBlocked", it) }
        )
        ControlDivider()
        ControlSwitch(
            label = "Block Settings Access",
            icon = Icons.Default.Settings,
            checked = device?.controls?.settingsBlocked ?: false,
            onCheckedChange = { viewModel.sendControl(context, imei, "settingsBlocked", it) }
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // App Restrictions
    SectionCard(title = "App Restrictions") {
        ControlSwitch(
            label = "Block Instagram",
            icon = Icons.Default.Camera,
            checked = device?.appRestrictions?.instagram ?: false,
            onCheckedChange = { viewModel.sendControl(context, imei, "instagram", it) }
        )
        ControlDivider()
        ControlSwitch(
            label = "Block WhatsApp",
            icon = Icons.Default.Chat,
            checked = device?.appRestrictions?.whatsapp ?: false,
            onCheckedChange = { viewModel.sendControl(context, imei, "whatsapp", it) }
        )
        ControlDivider()
        ControlSwitch(
            label = "Block YouTube",
            icon = Icons.Default.PlayCircle,
            checked = device?.appRestrictions?.youtube ?: false,
            onCheckedChange = { viewModel.sendControl(context, imei, "youtube", it) }
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Utilities
    SectionCard(title = "Quick Actions") {
        ControlActionItem(
            label = "Refresh Location",
            icon = Icons.Default.LocationOn,
            onClick = { viewModel.sendControl(context, imei, "request_location", true) }
        )
        ControlDivider()
        ControlActionItem(
            label = "Play Warning Sound",
            icon = Icons.Default.VolumeUp,
            onClick = { viewModel.sendControl(context, imei, "warningAudio", true) }
        )
        ControlDivider()
        ControlActionItem(
            label = "Set Warning Wallpaper",
            icon = Icons.Default.Wallpaper,
            onClick = { viewModel.sendControl(context, imei, "warningWallpaper", "SET_DEFAULT") }
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // EMI Reminders
    SectionCard(title = "EMI Reminders") {
        val phone = device?.phoneNumber ?: ""
        val name = device?.customerName ?: "Customer"

        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReminderButton(
                text = "WhatsApp",
                icon = Icons.Default.Chat,
                color = Color(0xFF25D366),
                modifier = Modifier.weight(1f),
                onClick = { openWhatsApp(context, phone, name) }
            )
            ReminderButton(
                text = "SMS + Push",
                icon = Icons.Default.NotificationsActive,
                color = Primary,
                modifier = Modifier.weight(1f),
                onClick = {
                    openSmsReminder(context, phone, name)
                    viewModel.sendControl(
                        context, imei, "manual_notification",
                        mapOf(
                            "title" to "EMI Payment Reminder",
                            "body" to "Dear $name, your installment is due. Please pay to avoid device lock."
                        )
                    )
                    Toast.makeText(context, "Reminder sent", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Reset all controls
    DangerOutlineButton(
        text = "Reset All Controls",
        icon = Icons.Default.Refresh,
        onClick = { showUnlockAllDialog = true }
    )

    Spacer(modifier = Modifier.height(10.dp))

    // Deregister
    DangerOutlineButton(
        text = "De-register Device",
        icon = Icons.Default.Logout,
        onClick = { showDeregisterDialog = true }
    )
}

@Composable
private fun OfflineActionsContent(device: DeviceResponse?) {
    val context = LocalContext.current
    val customerPhone = device?.phoneNumber ?: ""
    val lockCode = device?.smsCodes?.lockCode ?: ""
    val unlockCode = device?.smsCodes?.unlockCode ?: ""
    val deregisterCode = device?.smsCodes?.deregisterCode
        ?: try {
            val hash = java.security.MessageDigest.getInstance("SHA-256")
                .digest("DEREGISTER_${device?.imei ?: ""}".toByteArray())
            hash.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) { "" }

    fun sendSms(body: String) {
        if (customerPhone.isBlank()) {
            Toast.makeText(context, "Phone number missing", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$customerPhone")).apply {
                putExtra("sms_body", body)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "No SMS app found", Toast.LENGTH_SHORT).show()
        }
    }

    SectionCard(title = "Offline SMS Commands") {
        SmsCommandButton(
            text = "Send Lock SMS",
            icon = Icons.Default.Lock,
            color = Danger,
            onClick = { sendSms("LOCK#$lockCode") }
        )
        SmsCommandButton(
            text = "Send Unlock SMS",
            icon = Icons.Default.LockOpen,
            color = Success,
            onClick = { sendSms("UNLOCK#$unlockCode") }
        )
        SmsCommandButton(
            text = "Send Deregister SMS",
            icon = Icons.Default.PersonRemove,
            color = Color(0xFF7C3AED),
            onClick = { sendSms("DEREGISTER#$deregisterCode") }
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    SectionCard(title = "Security Keys") {
        Column(modifier = Modifier.padding(16.dp)) {
            KeyCopyRow(label = "Lock Key", code = lockCode, context = context)
            Spacer(modifier = Modifier.height(10.dp))
            KeyCopyRow(label = "Unlock Key", code = unlockCode, context = context)
            Spacer(modifier = Modifier.height(10.dp))
            KeyCopyRow(label = "Deregister Key", code = deregisterCode, context = context)
            Spacer(modifier = Modifier.height(10.dp))
            // Emergency Master Code — SHA-256("MASTER_{imei}") first 8 hex chars
            val masterCode = try {
                val hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest("MASTER_${device?.imei ?: ""}".toByteArray())
                hash.joinToString("") { "%02x".format(it) }.take(8)
            } catch (_: Exception) { "N/A" }
            KeyCopyRow(label = "Emergency Code", code = masterCode, context = context)
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  CUSTOMER TAB
// ═════════════════════════════════════════════════════════════════════════════
@Composable
private fun CustomerTab(
    device: DeviceResponse?,
    viewModel: DeviceListViewModel,
    imei: String
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    LaunchedEffect(imei) {
        viewModel.fetchEmiSchedule(context, imei)
    }

    val emiData = viewModel.selectedEmiSchedule

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Customer Profile
        SectionCard(title = "Customer Information") {
            if (!device?.profilePicture.isNullOrBlank()) {
                ImageCard(label = "Profile Photo", imageUrl = device!!.profilePicture!!)
            }
            InfoRow(label = "Full Name", value = device?.customerName ?: "N/A", icon = Icons.Default.Person)
            ControlDivider()
            InfoRow(label = "Phone Number", value = device?.phoneNumber ?: "N/A", icon = Icons.Default.Call)
            ControlDivider()
            InfoRow(label = "CNIC Number", value = device?.cnic ?: "N/A", icon = Icons.Default.Badge)
            if (!device?.cnicProofImage.isNullOrBlank()) {
                ImageCard(label = "CNIC Proof", imageUrl = device!!.cnicProofImage!!)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Guarantor
        SectionCard(title = "Guarantor Information") {
            val guarantor = device?.guarantor
            InfoRow(label = "Name", value = guarantor?.name ?: "N/A", icon = Icons.Default.VerifiedUser)
            ControlDivider()
            InfoRow(label = "Phone", value = guarantor?.mobile ?: "N/A", icon = Icons.Default.Call)
            ControlDivider()
            InfoRow(label = "Address", value = guarantor?.address ?: "N/A", icon = Icons.Default.Home)
            if (!guarantor?.cnicProofImage.isNullOrBlank()) {
                ImageCard(label = "Guarantor CNIC Proof", imageUrl = guarantor!!.cnicProofImage!!)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // EMI Summary
        SectionCard(title = "EMI Details") {
            InfoRow(label = "Product", value = device?.productName ?: "N/A", icon = Icons.Default.Inventory2)
            ControlDivider()
            InfoRow(label = "Total Price", value = "PKR ${device?.totalPrice ?: 0}", icon = Icons.Default.AccountBalance)
            ControlDivider()
            InfoRow(label = "Down Payment", value = "PKR ${device?.downPayment ?: 0}", icon = Icons.Default.PriceCheck)
            ControlDivider()
            InfoRow(label = "Remaining Balance", value = "PKR ${device?.balance ?: 0}", icon = Icons.Default.Wallet)
            ControlDivider()
            InfoRow(label = "Monthly EMI", value = "PKR ${device?.emiAmount ?: 0}", icon = Icons.Default.Payments)
            ControlDivider()
            InfoRow(label = "Tenure", value = "${device?.emiTenure ?: 0} Months", icon = Icons.Default.Schedule)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // EMI Schedule
        if (emiData?.summary != null) {
            SectionCard(title = "Payment Schedule") {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SummaryPill("Total", emiData.summary.total.toString(), Primary)
                        SummaryPill("Paid", emiData.summary.paid.toString(), Success)
                        if (emiData.summary.partial > 0) {
                            SummaryPill("Partial", emiData.summary.partial.toString(), Color(0xFFF59E0B))
                        }
                        SummaryPill("Unpaid", emiData.summary.unpaid.toString(), Danger)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    emiData.schedule.take(6).forEach { installment ->
                        EmiScheduleRow(installment = installment, viewModel = viewModel, imei = imei)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun EmiScheduleRow(
    installment: com.pksafe.lock.manager.data.EmiInstallmentItem,
    viewModel: DeviceListViewModel? = null,
    imei: String = ""
) {
    val context = LocalContext.current
    val isPaid = installment.status.equals("Paid", ignoreCase = true)
    val isPartial = installment.status.equals("Partial", ignoreCase = true)
    val remaining = installment.amount - installment.paidAmount
    var showPaymentDialog by remember { mutableStateOf(false) }

    val statusColor = when {
        isPaid -> Success
        isPartial -> Color(0xFFF59E0B) // amber
        else -> Danger
    }
    val statusBg = when {
        isPaid -> Color(0xFFD1FAE5)
        isPartial -> Color(0xFFFEF3C7)
        else -> Color(0xFFFEE2E2)
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Installment ${installment.installmentNumber}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(installment.dueDate.take(10), fontSize = 11.sp, color = TextSecondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPartial) {
                    Text("PKR ${installment.paidAmount.toInt()}/${installment.amount.toInt()}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFFF59E0B))
                    Spacer(modifier = Modifier.width(6.dp))
                } else {
                    Text("PKR ${installment.amount.toInt()}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Surface(color = statusBg, shape = RoundedCornerShape(12.dp)) {
                    Text(
                        text = installment.status,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        // Progress bar for partial payments
        if (isPartial || isPaid) {
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { (installment.paidAmount / installment.amount).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                color = statusColor,
                trackColor = statusColor.copy(alpha = 0.15f),
            )
        }

        // Record payment button for unpaid/partial
        if (!isPaid) {
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick = { showPaymentDialog = true },
                modifier = Modifier.height(28.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Default.AttachMoney, contentDescription = null, modifier = Modifier.size(14.dp), tint = Primary)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Record Payment", fontSize = 11.sp, color = Primary)
            }
        }
    }

    // Partial Payment Dialog
    if (showPaymentDialog) {
        var amountText by remember { mutableStateOf(if (isPartial) remaining.toString() else installment.amount.toString()) }

        AlertDialog(
            onDismissRequest = { showPaymentDialog = false },
            title = { Text("Record Payment", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Installment #${installment.installmentNumber}", fontSize = 13.sp, color = TextSecondary)
                    Text("Total: PKR ${installment.amount.toInt()}", fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                    if (isPartial) {
                        Text("Already paid: PKR ${installment.paidAmount.toInt()}", fontSize = 12.sp, color = Color(0xFFF59E0B))
                        Text("Remaining: PKR ${remaining.toInt()}", fontSize = 12.sp, color = Danger, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount (PKR)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = Border,
                            focusedLabelColor = Primary,
                            unfocusedLabelColor = TextSecondary,
                            cursorColor = Primary
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amount = amountText.toDoubleOrNull()
                    if (amount != null && amount > 0) {
                        viewModel?.markEmiAsPaid(context, installment._id, imei, amount) { ok, msg ->
                            android.widget.Toast.makeText(
                                context,
                                if (ok) "✓ $msg" else "✗ $msg",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                        showPaymentDialog = false
                    }
                }) {
                    Text("Confirm", color = Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPaymentDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SummaryPill(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(color = color.copy(alpha = 0.1f), shape = RoundedCornerShape(10.dp)) {
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, fontSize = 10.sp, color = TextSecondary)
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  LOCATION TAB
// ═════════════════════════════════════════════════════════════════════════════
@Composable
private fun LocationTab(
    device: DeviceResponse?,
    viewModel: DeviceListViewModel,
    imei: String
) {
    val context = LocalContext.current
    val loc = device?.location
    val geofence = device?.geofence

    val currentPoint = loc?.let { it.lat to it.lng } ?: (0.0 to 0.0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Location Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clickable {
                    if (loc != null) {
                        val uri = Uri.parse("geo:${loc.lat},${loc.lng}?q=${loc.lat},${loc.lng}(Device Location)")
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        context.startActivity(intent)
                    }
                },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, Border),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (loc != null) Success else TextSecondary)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (loc != null) "Location Available" else "No Location Data",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Column {
                        if (loc != null) {
                            Text(
                                text = "Latitude: ${loc.lat}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Longitude: ${loc.lng}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        } else {
                            Text("Location not available yet.", fontSize = 14.sp, color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap to open in Maps",
                            fontSize = 11.sp,
                            color = Primary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Text(
                        text = "Last updated: ${loc?.updatedAt?.take(16) ?: "N/A"}",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Geofence
        SectionCard(title = "Geofence (City Limit)") {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GppMaybe, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Enable Geofence", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }
                    Switch(
                        checked = geofence?.isEnabled ?: false,
                        onCheckedChange = { enabled ->
                            val payload = mutableMapOf<String, Any>(
                                "isEnabled" to enabled,
                                "lat" to (loc?.lat ?: 0.0),
                                "lng" to (loc?.lng ?: 0.0),
                                "radius" to (geofence?.radius ?: 5.0)
                            )
                            viewModel.sendControl(context, imei, "geofence_update", payload)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Primary
                        )
                    )
                }

                if (geofence?.isEnabled == true) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Restriction Radius", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(5, 10, 25).forEach { r ->
                            val active = geofence.radius.toInt() == r
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        val payload = mapOf(
                                            "isEnabled" to true,
                                            "radius" to r.toDouble(),
                                            "lat" to (geofence.lat ?: loc?.lat ?: 0.0),
                                            "lng" to (geofence.lng ?: loc?.lng ?: 0.0)
                                        )
                                        viewModel.sendControl(context, imei, "geofence_update", payload)
                                    },
                                color = if (active) Primary.copy(alpha = 0.1f) else Color.Transparent,
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (active) Primary else Border
                                )
                            ) {
                                Box(modifier = Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        "$r KM",
                                        color = if (active) Primary else TextSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Device Identity
        SectionCard(title = "Device Identity") {
            InfoRow(label = "Product", value = device?.productName ?: "N/A", icon = Icons.Default.Inventory2)
            ControlDivider()
            InfoRow(label = "Model", value = "${device?.brand ?: ""} ${device?.model ?: ""}".trim().ifEmpty { "N/A" }, icon = Icons.Default.Smartphone)
            ControlDivider()
            InfoRow(label = "Android Version", value = device?.androidVersion ?: "N/A", icon = Icons.Default.Android)
            ControlDivider()
            InfoRow(label = "Primary IMEI", value = device?.imei ?: "N/A", icon = Icons.Default.QrCodeScanner)
            ControlDivider()
            InfoRow(label = "Secondary IMEI", value = device?.imei2 ?: "N/A", icon = Icons.Default.SimCard)
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  SHARED UI COMPONENTS
// ═════════════════════════════════════════════════════════════════════════════
@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFFEFF6FF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
            Text(value, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ImageCard(label: String, imageUrl: String) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
        Spacer(modifier = Modifier.height(6.dp))
        AsyncImage(
            model = imageUrl,
            contentDescription = label,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
    }
}

@Composable
private fun ControlSwitch(
    label: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    var isProcessing by remember { mutableStateOf(false) }

    LaunchedEffect(checked) { isProcessing = false }
    LaunchedEffect(isProcessing) {
        if (isProcessing) {
            kotlinx.coroutines.delay(10000)
            isProcessing = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isProcessing) {
                isProcessing = true
                onCheckedChange(!checked)
            }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0xFFF1F5F9)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
        if (isProcessing) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Primary)
        } else {
            Switch(
                checked = checked,
                onCheckedChange = {
                    isProcessing = true
                    onCheckedChange(it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Primary,
                    uncheckedTrackColor = Color(0xFFE2E8F0)
                )
            )
        }
    }
}

@Composable
private fun ControlActionItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0xFFF1F5F9)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ControlDivider() {
    HorizontalDivider(color = Border, thickness = 1.dp)
}

@Composable
private fun ReminderButton(
    text: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SmsCommandButton(
    text: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(vertical = 6.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
    }
}

@Composable
private fun KeyCopyRow(label: String, code: String, context: Context) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(label, fontSize = 11.sp, color = TextSecondary)
            Text(
                text = if (code.length > 20) code.take(18) + "..." else code,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = TextPrimary
            )
        }
        IconButton(onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(label, code))
            Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
        }) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Primary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun DangerOutlineButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFFEF2F2),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Danger, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Danger
            )
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Danger, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun BottomActionButton(
    text: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(14.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  HELPERS
// ═════════════════════════════════════════════════════════════════════════════
private fun openWhatsApp(context: Context, phoneNumber: String, customerName: String) {
    if (phoneNumber.isBlank()) {
        Toast.makeText(context, "No Phone Number Found", Toast.LENGTH_SHORT).show()
        return
    }
    val message = "Assalam-o-Alaikum *$customerName*,\n\n" +
            "PKLocker (EMI Management) ki taraf se reminder hai k aapki device ki installment abhi tak baqi hai.\n\n" +
            "Baraye meherbani lock se bachne ke liye installment jald az jald jama karwain.\n\n" +
            "Shukriya!\n*PKLocker Security Hub*"

    val cleanPhone = phoneNumber.replace("+", "").replace(" ", "").trim()
    val url = "https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(message)}"

    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
    }
}

private fun openSmsReminder(context: Context, phoneNumber: String, customerName: String) {
    if (phoneNumber.isBlank()) {
        Toast.makeText(context, "No Phone Number Found", Toast.LENGTH_SHORT).show()
        return
    }
    val message = "REMINDER: Mr. $customerName, your mobile EMI is pending. Pay immediately to keep your terminal active. - PKLocker"
    try {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phoneNumber")).apply {
            putExtra("sms_body", message)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "SMS Error", Toast.LENGTH_SHORT).show()
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  ACTIVITY TAB — Audit Trail / History Log
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun ActivityTab(imei: String) {
    val context = LocalContext.current
    var activities by remember { mutableStateOf<List<com.pksafe.lock.manager.data.ActivityLogItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentPage by remember { mutableIntStateOf(1) }
    var totalPages by remember { mutableIntStateOf(1) }

    // Fetch activity log
    LaunchedEffect(imei, currentPage) {
        isLoading = true
        errorMessage = null
        try {
            val prefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
            val token = prefs.getString("auth_token", "") ?: ""
            if (token.isBlank()) {
                errorMessage = "Not authenticated"
                isLoading = false
                return@LaunchedEffect
            }

            val retrofit = Retrofit.Builder()
                .baseUrl(com.pksafe.lock.manager.util.Constants.BASE_URL)
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build()
            val api = retrofit.create(ApiService::class.java)
            val response = api.getActivityLog("Bearer $token", imei, currentPage, 50)
            if (response.isSuccessful && response.body()?.success == true) {
                val body = response.body()!!
                activities = body.data
                totalPages = body.pagination?.pages ?: 1
            } else {
                errorMessage = "Failed to load activity log"
            }
        } catch (e: Exception) {
            errorMessage = e.message
        }
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Activity Log", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(
                "Page $currentPage of $totalPages",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary, modifier = Modifier.size(32.dp))
                }
            }
            errorMessage != null -> {
                Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Danger, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(errorMessage ?: "Unknown error", fontSize = 13.sp, color = Danger)
                    }
                }
            }
            activities.isEmpty() -> {
                Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.History, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No activity yet", fontSize = 14.sp, color = TextSecondary)
                        Text("Actions will appear here", fontSize = 12.sp, color = TextSecondary)
                    }
                }
            }
            else -> {
                activities.forEachIndexed { index, item ->
                    ActivityTimelineItem(
                        item = item,
                        isLast = index == activities.size - 1
                    )
                }

                // Pagination
                if (totalPages > 1) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentPage > 1) {
                            TextButton(onClick = { currentPage-- }) {
                                Text("Previous", color = Primary)
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        if (currentPage < totalPages) {
                            TextButton(onClick = { currentPage++ }) {
                                Text("Next", color = Primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityTimelineItem(item: com.pksafe.lock.manager.data.ActivityLogItem, isLast: Boolean) {
    val (icon, color) = when (item.action) {
        "lock" -> Icons.Default.Lock to Danger
        "unlock" -> Icons.Default.LockOpen to Success
        "deregister" -> Icons.Default.DeleteForever to Danger
        "registered" -> Icons.Default.AddCircle to Success
        "control_change" -> Icons.Default.Settings to Primary
        "app_restrict" -> Icons.Default.AppBlocking to Color(0xFFF59E0B) // amber
        "sim_changed" -> Icons.Default.SimCardAlert to Color(0xFFF59E0B)
        "geofence_breach" -> Icons.Default.LocationOff to Danger
        "unlock_all" -> Icons.Default.LockReset to Primary
        "command_ack" -> Icons.Default.CheckCircle to Success
        else -> Icons.Default.Info to TextSecondary
    }

    val timeAgo = try {
        val seenTime = java.time.Instant.parse(item.timestamp)
        val now = java.time.Instant.now()
        val duration = java.time.Duration.between(seenTime, now)
        when {
            duration.toMinutes() < 1 -> "Just now"
            duration.toMinutes() < 60 -> "${duration.toMinutes()}m ago"
            duration.toHours() < 24 -> "${duration.toHours()}h ago"
            duration.toDays() < 7 -> "${duration.toDays()}d ago"
            else -> "${duration.toDays() / 7}w ago"
        }
    } catch (_: Exception) { "" }

    Row(modifier = Modifier.fillMaxWidth()) {
        // Timeline dot + line
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp)
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(32.dp)
                        .background(color.copy(alpha = 0.2f))
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Content
        Column(modifier = Modifier.padding(bottom = if (isLast) 0.dp else 8.dp)) {
            Text(
                text = item.details,
                fontSize = 13.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = timeAgo,
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                if (item.performedBy.isNotBlank()) {
                    Text(" • ", fontSize = 11.sp, color = TextSecondary)
                    Text(
                        text = item.performedBy.replaceFirstChar { it.uppercase() },
                        fontSize = 11.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
