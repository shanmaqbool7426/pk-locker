package com.example.pklocker.ui.devices

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.example.pklocker.data.DeviceResponse
import com.example.pklocker.ui.theme.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset

// Consistent Premium Theme Colors
private val SoftBg = Color(0xFFF8FAFC)
private val CardWhite = Color.White
private val BrandBlue = Color(0xFF2563EB)
private val TextTitle = Color(0xFF0F172A)
private val TextMuted = Color(0xFF64748B)

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
    val tabs = listOf("Secure Control", "Hardware Tech", "Customer Profile", "EMI Ledger")
    var isOnlineMode by remember { mutableStateOf(true) }

    var showConfirmDialog by remember { mutableStateOf(false) }
    var pendingLockState by remember { mutableStateOf(false) }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(if (pendingLockState) "Restrict Device" else "Release Restriction", fontWeight = FontWeight.Black, color = TextTitle) },
            text = { Text("Immediately apply security protocols to this terminal? System will sync via ${if(isOnlineMode) "Cloud" else "SMS"}.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.toggleLock(context, imei, pendingLockState)
                        showConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (pendingLockState) Color(0xFFDC2626) else BrandBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("EXECUTE", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Cancel", color = TextMuted) }
            },
            containerColor = CardWhite,
            shape = RoundedCornerShape(20.dp)
        )
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(CardWhite)) {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("ADMIN PANEL", fontWeight = FontWeight.Black, color = TextTitle, fontSize = 14.sp, letterSpacing = 2.sp)
                            val statusText = device?.status ?: "Unknown"
                            val statusColor = if (statusText == "Locked") Color(0xFFDC2626) else Color(0xFF16A34A)
                            Text(statusText.uppercase(), fontSize = 10.sp, color = statusColor, fontWeight = FontWeight.Black)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
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

                // Compact Avatar Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFFEFF6FF), Color(0xFFDBEAFE)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(customerName.take(1).uppercase(), fontSize = 24.sp, fontWeight = FontWeight.Black, color = BrandBlue)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(customerName, color = TextTitle, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text("Terminal ID: $imei", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                    }
                }

                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = CardWhite,
                    edgePadding = 20.dp,
                    divider = {},
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = BrandBlue,
                            height = 3.dp
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontSize = 12.sp, fontWeight = if(selectedTab == index) FontWeight.Black else FontWeight.Bold) },
                            selectedContentColor = BrandBlue,
                            unselectedContentColor = TextMuted
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = CardWhite,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = { pendingLockState = true; showConfirmDialog = true }, 
                        modifier = Modifier.weight(1f).height(56.dp), 
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827)), // Midnight Blue/Black for Authority
                        shape = RoundedCornerShape(16.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp), tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("SECURE LOCK", fontWeight = FontWeight.Black, color = Color.White)
                    }
                    Button(
                        onClick = { pendingLockState = false; showConfirmDialog = true }, 
                        modifier = Modifier.weight(1f).height(56.dp), 
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue), 
                        shape = RoundedCornerShape(16.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Icon(Icons.Default.LockOpen, null, modifier = Modifier.size(18.dp), tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("RELEASE", fontWeight = FontWeight.Black, color = Color.White)
                    }
                }
            }
        },
        containerColor = SoftBg
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column {
                when (selectedTab) {
                    0 -> {
                        Row(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PremiumModeButton("Sync Mode", isOnlineMode, { isOnlineMode = true }, Modifier.weight(1f), Icons.Default.CloudSync)
                            PremiumModeButton("Direct SMS", !isOnlineMode, { isOnlineMode = false }, Modifier.weight(1f), Icons.Default.Sms)
                        }
                        if (isOnlineMode) ActionTabContent(viewModel, device, imei, onBack)
                        else SmsTabContent(device)
                    }
                    1 -> HardwareTechTab(device)
                    2 -> CustomerProfileTab(device)
                    3 -> EmiLedgerTab(device)
                }
            }
            if (viewModel.isLoading) {
                Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandBlue)
                }
            }
        }
    }
}

@Composable
fun PremiumModeButton(text: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier, icon: ImageVector) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) BrandBlue else CardWhite,
        border = if (!isSelected) BorderStroke(1.dp, Color(0xFFE2E8F0)) else null,
        shadowElevation = if (isSelected) 4.dp else 0.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = if (isSelected) Color.White else TextMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, color = if (isSelected) Color.White else TextMuted, fontWeight = FontWeight.Black, fontSize = 13.sp)
        }
    }
}

@Composable
fun ActionTabContent(viewModel: DeviceListViewModel, device: DeviceResponse?, imei: String, onBack: () -> Unit) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    var showUnlockAllDialog by remember { mutableStateOf(false) }

    if (showUnlockAllDialog) {
        AlertDialog(
            onDismissRequest = { showUnlockAllDialog = false },
            title = { Text("Factory Reset Controls?", fontWeight = FontWeight.Black, color = TextTitle) },
            text = { Text("WARNING: This will immediately clear ALL active restrictions (USB, Camera, Apps) on the terminal.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.unlockAllControls(context, imei)
                        showUnlockAllDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("RESET ALL", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlockAllDialog = false }) { Text("Abort", color = TextMuted) }
            },
            containerColor = CardWhite,
            shape = RoundedCornerShape(20.dp)
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp).verticalScroll(scrollState)) {

        // --- RESET ALL BANNER ---
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Emergency Reset", color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    Text("Clear all restrictions with one tap", color = Color.White.copy(0.7f), fontSize = 11.sp)
                }
                Button(
                    onClick = { showUnlockAllDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("EXECUTE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        PremiumControlGroup("Security System") {
            PremiumSwitchItem("Auto-Lock (Offline Detection)", Icons.Default.GppMaybe, device?.controls?.autoLock ?: false)
            { viewModel.sendControl(context, imei, "autoLock", it) }

            PremiumSwitchItem("USB Terminal Block", Icons.Default.Usb, device?.controls?.usbLock ?: false)
            { viewModel.sendControl(context, imei, "usbLock", it) }

            PremiumSwitchItem("Hardware Camera Block", Icons.Default.CameraAlt, device?.controls?.cameraDisabled ?: false)
            { viewModel.sendControl(context, imei, "cameraDisabled", it) }

            PremiumSwitchItem("Application Install Lock", Icons.Default.AppRegistration, device?.controls?.installBlocked ?: false)
            { viewModel.sendControl(context, imei, "installBlocked", it) }

            PremiumSwitchItem("System Settings Lock", Icons.Default.Settings, device?.controls?.settingsBlocked ?: false)
            { viewModel.sendControl(context, imei, "settingsBlocked", it) }
        }

        PremiumControlGroup("App Restrictions") {
            PremiumSwitchItem("Instagram Restrictions", Icons.Default.Camera, device?.appRestrictions?.instagram ?: false) 
            { viewModel.sendControl(context, imei, "instagram", it) }
            
            PremiumSwitchItem("WhatsApp System Block", Icons.Default.Chat, device?.appRestrictions?.whatsapp ?: false) 
            { viewModel.sendControl(context, imei, "whatsapp", it) }
            
            PremiumSwitchItem("YouTube Content Block", Icons.Default.PlayCircle, device?.appRestrictions?.youtube ?: false) 
            { viewModel.sendControl(context, imei, "youtube", it) }
        }

        PremiumControlGroup("Terminal Utilities") {
            PremiumActionItem("Ping Last Known Location", Icons.Default.LocationOn) { viewModel.sendControl(context, imei, "request_location", true) }
            PremiumActionItem("Trigger Warning Audio", Icons.Default.VolumeUp) { viewModel.sendControl(context, imei, "warningAudio", true) }
            PremiumActionItem("Trigger Warning Wallpaper", Icons.Default.Wallpaper) { viewModel.sendControl(context, imei, "warningWallpaper", "SET_DEFAULT") }
        }

        // --- Danger Zone ---
        var showReleaseDialog by remember { mutableStateOf(false) }
        var releaseConfirmText by remember { mutableStateOf("") }

        if (showReleaseDialog) {
            AlertDialog(
                onDismissRequest = { showReleaseDialog = false; releaseConfirmText = "" },
                title = { Text("Permanently De-register?", fontWeight = FontWeight.Black, color = Color(0xFFDC2626)) },
                text = {
                    Column {
                        Text("This terminal will be removed from your secure network and all restrictions will be permanently cleared.")
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = releaseConfirmText,
                            onValueChange = { releaseConfirmText = it },
                            placeholder = { Text("TYPE CONFIRM", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showReleaseDialog = false
                            viewModel.deregisterDevice(context, imei) { onBack() }
                        },
                        enabled = releaseConfirmText.trim().uppercase() == "CONFIRM",
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("CONFIRM RELEASE")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showReleaseDialog = false; releaseConfirmText = "" }) { Text("Abort") }
                },
                containerColor = CardWhite,
                shape = RoundedCornerShape(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth().clickable { showReleaseDialog = true },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
            border = BorderStroke(1.dp, Color(0xFFFCA5A5))
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Dns, null, tint = Color(0xFFDC2626), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("De-register Terminal", color = Color(0xFF991B1B), fontWeight = FontWeight.Black, fontSize = 15.sp)
                    Text("Permanent release procedure", color = Color(0xFFB91C1C).copy(0.7f), fontSize = 11.sp)
                }
                Icon(Icons.Default.Logout, null, tint = Color(0xFFDC2626))
            }
        }
        
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun PremiumControlGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title.uppercase(), fontWeight = FontWeight.Black, fontSize = 11.sp, color = BrandBlue, letterSpacing = 1.sp, modifier = Modifier.padding(top = 28.dp, bottom = 10.dp, start = 4.dp))
    Card(
        modifier = Modifier.fillMaxWidth(), 
        colors = CardDefaults.cardColors(containerColor = CardWhite), 
        shape = RoundedCornerShape(24.dp), 
        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
    ) {
        Column { content() }
    }
}

@Composable
fun PremiumSwitchItem(label: String, icon: ImageVector, initialValue: Boolean, onToggle: (Boolean) -> Unit) {
    var isProcessing by remember { mutableStateOf(false) }
    LaunchedEffect(initialValue) { isProcessing = false }

    Row(
        modifier = Modifier.fillMaxWidth().clickable { if(!isProcessing) { isProcessing = true; onToggle(!initialValue) } }.padding(16.dp), 
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(color = Color(0xFFF8FAFC), shape = CircleShape, modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = if (isProcessing) TextMuted else TextTitle, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (isProcessing) TextMuted else TextTitle)
        
        if (isProcessing) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = BrandBlue)
        } else {
            Switch(
                checked = initialValue,
                onCheckedChange = { isProcessing = true; onToggle(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = BrandBlue,
                    uncheckedTrackColor = Color(0xFFE2E8F0),
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
fun PremiumActionItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp), 
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(color = Color(0xFFF8FAFC), shape = CircleShape, modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = TextTitle, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextTitle)
        Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(18.dp))
    }
}

@Composable
fun SmsTabContent(device: DeviceResponse?) {
    val context = LocalContext.current
    val customerPhone = device?.phoneNumber ?: ""
    val lockCode      = device?.smsCodes?.lockCode   ?: ""
    val unlockCode    = device?.smsCodes?.unlockCode ?: ""

    fun openSms(body: String) {
        if (customerPhone.isBlank()) { Toast.makeText(context, "Phone Missing", Toast.LENGTH_SHORT).show(); return }
        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$customerPhone")).apply { putExtra("sms_body", body) })
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4))) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Verified, null, tint = Color(0xFF16A34A))
                Spacer(Modifier.width(12.dp))
                Text("Direct Terminal Connection Active. SMS protocols bypassed cloud requirements.", fontSize = 12.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.Bold)
            }
        }

        PremiumControlGroup("Offline SMS Commands") {
            SmsActionButton("SEND LOCK SMS", Icons.Default.Lock, Color(0xFFDC2626)) { openSms("LOCK#$lockCode") }
            HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(horizontal = 20.dp))
            SmsActionButton("SEND UNLOCK SMS", Icons.Default.LockOpen, BrandBlue) { openSms("UNLOCK#$unlockCode") }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CardWhite), border = BorderStroke(1.dp, Color(0xFFF1F5F9))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("SECURITY KEYS (OFFLINE)", fontWeight = FontWeight.Black, fontSize = 11.sp, color = BrandBlue, letterSpacing = 1.sp)
                Spacer(Modifier.height(16.dp))
                SmsKeyRow("LOCK_KEY", lockCode)
                Spacer(Modifier.height(8.dp))
                SmsKeyRow("UNLOCK_KEY", unlockCode)
            }
        }
    }
}

@Composable
fun SmsActionButton(text: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(60.dp).padding(6.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(14.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = Color.White)
        Spacer(Modifier.width(10.dp))
        Text(text, fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color.White)
    }
}

@Composable
fun SmsKeyRow(label: String, code: String) {
    Surface(color = Color(0xFFF8FAFC), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Black, color = TextMuted, modifier = Modifier.weight(1f))
            Text(if(code.length > 20) code.take(18) + "..." else code, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextTitle, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        }
    }
}

@Composable
fun HardwareTechTab(device: DeviceResponse?) {
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PremiumInfoCard("Model Identifier", "${device?.brand} ${device?.model ?: "Generic"}", Icons.Default.Smartphone)
        PremiumInfoCard("System Version", "Android ${device?.androidVersion ?: "14.x"}", Icons.Default.Android)
        PremiumInfoCard("IMEI / Serial", device?.imei ?: "N/A", Icons.Default.QrCodeScanner)
    }
}

@Composable
fun CustomerProfileTab(device: DeviceResponse?) {
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PremiumInfoCard("Account Holder", device?.customerName ?: "N/A", Icons.Default.Person)
        PremiumInfoCard("Primary Contact", device?.phoneNumber ?: "N/A", Icons.Default.Call)
        PremiumInfoCard("Identity (CNIC)", device?.cnic ?: "N/A", Icons.Default.Badge)
    }
}

@Composable
fun EmiLedgerTab(device: DeviceResponse?) {
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PremiumInfoCard("Total Credit", "PKR ${device?.totalPrice ?: "0"}", Icons.Default.AccountBalance)
        PremiumInfoCard("Monthly Installment", "PKR ${device?.emiAmount ?: "0"}/mo", Icons.Default.Payments)
        PremiumInfoCard("Remaining Tenure", "${device?.emiTenure ?: "0"} Months", Icons.Default.Schedule)
    }
}

@Composable
fun PremiumInfoCard(label: String, value: String, icon: ImageVector) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Color(0xFFEFF6FF), shape = CircleShape, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = BrandBlue, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                Text(value, fontSize = 15.sp, fontWeight = FontWeight.Black, color = TextTitle)
            }
        }
    }
}
