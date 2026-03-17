package com.example.pklocker.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pklocker.data.AdvancedControlRequest
import com.example.pklocker.data.DeviceResponse
import com.example.pklocker.ui.theme.*

// Premium Theme Colors
val PrimaryColor = Color(0xFF1A237E)
val BgColor = Color(0xFFF4F6F9)

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
    val tabs = listOf("Action", "Device Detail", "Customer", "EMI Detail")
    var isOnlineMode by remember { mutableStateOf(true) }

    var showConfirmDialog by remember { mutableStateOf(false) }
    var pendingLockState by remember { mutableStateOf(false) }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(if (pendingLockState) "Confirm Lock" else "Confirm Unlock") },
            text = { Text("Are you sure you want to proceed? This will immediately affect the device.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.toggleLock(context, imei, pendingLockState)
                        showConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (pendingLockState) Color.Red else PrimaryColor)
                ) {
                    Text("YES, PROCEED")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(PrimaryColor)) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Controls", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Spacer(Modifier.weight(1f))
                            val statusText = device?.status ?: "Unknown"
                            val statusColor = if (statusText == "Locked") Color(0xFFFF5252) else Color(0xFF69F0AE)
                            Text("Status: $statusText", fontSize = 12.sp, color = statusColor, fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryColor)
                )

                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        modifier = Modifier.size(90.dp),
                        color = Color.White.copy(alpha = 0.2f),
                        border = BorderStroke(2.dp, Color.White.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Person, null, modifier = Modifier.padding(20.dp), tint = Color.White)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(customerName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth().background(Color.White).padding(vertical = 12.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    tabs.forEachIndexed { index, title ->
                        TabButton(title, selectedTab == index, { selectedTab = index }, PrimaryColor)
                    }
                }
            }
        },
        bottomBar = {
            Row(modifier = Modifier.fillMaxWidth().background(Color.White).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { pendingLockState = true; showConfirmDialog = true }, modifier = Modifier.weight(1f).height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)), shape = RoundedCornerShape(12.dp)) {
                    Text("LOCK DEVICE", fontWeight = FontWeight.Bold)
                }
                Button(onClick = { pendingLockState = false; showConfirmDialog = true }, modifier = Modifier.weight(1f).height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor), shape = RoundedCornerShape(12.dp)) {
                    Text("UNLOCK", fontWeight = FontWeight.Bold)
                }
            }
        },
        containerColor = BgColor
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column {
                when (selectedTab) {
                    0 -> {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ModeSelectorButton("Online Mode", isOnlineMode, { isOnlineMode = true }, PrimaryColor, Modifier.weight(1f))
                            ModeSelectorButton("SMS Mode", !isOnlineMode, { isOnlineMode = false }, PrimaryColor, Modifier.weight(1f))
                        }
                        if (isOnlineMode) ActionTabContent(viewModel, device, PrimaryColor)
                        else SmsTabContent(PrimaryColor)
                    }
                    1 -> DeviceDetailTab(imei)
                    2 -> CustomerDetailTab(customerName)
                    3 -> EmiDetailTab()
                }
            }
            if (viewModel.isLoading) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryColor)
                }
            }
        }
    }
}

@Composable
fun TabButton(title: String, isSelected: Boolean, onClick: () -> Unit, activeColor: Color) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) activeColor else Color.Transparent,
        border = if (!isSelected) BorderStroke(1.dp, Color.Gray.copy(0.3f)) else null
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(title, fontSize = 11.sp, color = if (isSelected) Color.White else Color.Gray, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ModeSelectorButton(text: String, isSelected: Boolean, onClick: () -> Unit, activeColor: Color, modifier: Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) activeColor else Color.White,
        shadowElevation = if (isSelected) 4.dp else 1.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = if (isSelected) Color.White else Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
fun ActionTabContent(viewModel: DeviceListViewModel, device: DeviceResponse?, themeColor: Color) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val imei = device?.imei ?: ""

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(scrollState)) {
        ControlGroup("Premium Security Controls") {
            LabeledSwitchItem("Auto-Lock (Offline)", Icons.Default.GppMaybe, device?.controls?.autoLock ?: false, themeColor)
            { viewModel.sendControl(context, imei, "autoLock", it) }

            LabeledSwitchItem("USB Block", Icons.Default.Usb, device?.controls?.usbLock ?: false, themeColor)
            { viewModel.sendControl(context, imei, "usbLock", it) }

            LabeledSwitchItem("Camera Block", Icons.Default.CameraAlt, device?.controls?.cameraDisabled ?: false, themeColor)
            { viewModel.sendControl(context, imei, "cameraDisabled", it) }

            LabeledSwitchItem("App Installation Block", Icons.Default.AppRegistration, device?.controls?.installBlocked ?: false, themeColor)
            { viewModel.sendControl(context, imei, "installBlocked", it) }

            LabeledSwitchItem("App Uninstallation Block", Icons.Default.DeleteSweep, device?.controls?.uninstallBlocked ?: false, themeColor)
            { viewModel.sendControl(context, imei, "uninstallBlocked", it) }

            LabeledSwitchItem("Soft Reset Block", Icons.Default.SettingsBackupRestore, device?.controls?.softResetBlocked ?: false, themeColor)
            { viewModel.sendControl(context, imei, "softResetBlocked", it) }

            LabeledSwitchItem("Soft Boot Block", Icons.Default.RestartAlt, device?.controls?.softBootBlocked ?: false, themeColor)
            { viewModel.sendControl(context, imei, "softBootBlocked", it) }

            LabeledSwitchItem("Outgoing Calls Block", Icons.Default.Call, device?.controls?.outgoingCallsBlocked ?: false, themeColor)
            { viewModel.sendControl(context, imei, "outgoingCallsBlocked", it) }

            LabeledSwitchItem("Settings Blocking", Icons.Default.Settings, device?.controls?.settingsBlocked ?: false, themeColor)
            { viewModel.sendControl(context, imei, "settingsBlocked", it) }
        }

        ControlGroup("Advanced Controls") {
            ControlActionButton("Warning Audio", Icons.Default.VolumeUp, device?.controls?.warningAudio ?: false, themeColor)
            { viewModel.sendControl(context, imei, "warningAudio", it) }

            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Wallpaper, null, tint = Color.Gray)
                Spacer(Modifier.width(12.dp))
                Text("Warning Wallpaper", modifier = Modifier.weight(1f), fontSize = 14.sp)
                Button(onClick = { viewModel.sendControl(context, imei, "warningWallpaper", "SET_DEFAULT") }, colors = ButtonDefaults.buttonColors(containerColor = themeColor)) {
                    Text("SET", fontSize = 12.sp)
                }
            }
        }

        ControlGroup("Remote Actions") {
            ActionButtonItem("Get Device Location", Icons.Default.LocationOn) { viewModel.sendControl(context, imei, "request_location", true) }
            ActionButtonItem("Get Phone Number", Icons.Default.Phone) { viewModel.sendControl(context, imei, "request_phone", true) }
            ActionButtonItem("Reset Device Password", Icons.Default.Password) { }
        }

        ControlGroup("Application Control") {
            LabeledSwitchItem("Instagram Block", Icons.Default.Camera, device?.appRestrictions?.instagram ?: false, themeColor) { viewModel.sendControl(context, imei, "instagram", it) }
            LabeledSwitchItem("WhatsApp Block", Icons.Default.Chat, device?.appRestrictions?.whatsapp ?: false, themeColor) { viewModel.sendControl(context, imei, "whatsapp", it) }
            LabeledSwitchItem("Facebook Block", Icons.Default.Public, device?.appRestrictions?.facebook ?: false, themeColor) { viewModel.sendControl(context, imei, "facebook", it) }
            LabeledSwitchItem("YouTube Block", Icons.Default.PlayCircle, device?.appRestrictions?.youtube ?: false, themeColor) { viewModel.sendControl(context, imei, "youtube", it) }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun ActionButtonItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp).clickable { onClick() }, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.Gray.copy(0.6f), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp)
        Icon(Icons.Default.ChevronRight, null, tint = Color.Gray.copy(0.1f))
    }
}

@Composable
fun ControlActionButton(label: String, icon: ImageVector, initialValue: Boolean, themeColor: Color, onToggle: (Boolean) -> Unit) {
    var active by remember(initialValue) { mutableStateOf(initialValue) }
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.Gray.copy(0.6f), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp)
        IconButton(onClick = { active = !active; onToggle(active) }) {
            Icon(if (active) Icons.Default.StopCircle else Icons.Default.PlayCircle, null, tint = if (active) Color.Red else themeColor, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
fun ControlGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(12.dp))
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(1.dp)) {
        Column { content() }
    }
}

@Composable
fun LabeledSwitchItem(label: String, icon: ImageVector, initialValue: Boolean, themeColor: Color, onToggle: (Boolean) -> Unit) {
    var isProcessing by remember { mutableStateOf(false) }

    LaunchedEffect(initialValue) {
        // Jab server se naya state mil jaye toh processing rok do
        isProcessing = false
    }

    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.Gray.copy(if (isProcessing) 0.3f else 0.6f), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium, fontSize = 14.sp, color = if (isProcessing) Color.Gray else Color.Black)
        
        if (isProcessing) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 4.dp), strokeWidth = 2.dp, color = themeColor)
        } else {
            Row(modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFFEEEEEE)).padding(2.dp)) {
                Surface(onClick = { 
                    if (!initialValue) return@Surface
                    isProcessing = true
                    onToggle(false) 
                }, color = if (!initialValue) Color(0xFF4CAF50) else Color.Transparent, shape = RoundedCornerShape(10.dp)) {
                    Icon(Icons.Default.LockOpen, null, modifier = Modifier.padding(10.dp).size(16.dp), tint = if(!initialValue) Color.White else Color.Gray)
                }
                Surface(onClick = { 
                    if (initialValue) return@Surface
                    isProcessing = true
                    onToggle(true) 
                }, color = if (initialValue) Color(0xFFD32F2F) else Color.Transparent, shape = RoundedCornerShape(10.dp)) {
                    Icon(Icons.Default.Lock, null, modifier = Modifier.padding(10.dp).size(16.dp), tint = if(initialValue) Color.White else Color.Gray)
                }
            }
        }
    }
}

@Composable
fun SmsTabContent(themeColor: Color) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("SMS commands work without internet.", fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(16.dp))
        SmsActionButton("LOCK BY SMS", Icons.Default.Lock, themeColor)
        SmsActionButton("UNLOCK BY SMS", Icons.Default.LockOpen, themeColor)
        SmsActionButton("GET LOCATION", Icons.Default.LocationOn, themeColor)
    }
}

@Composable
fun SmsActionButton(label: String, icon: ImageVector, themeColor: Color) {
    Button(onClick = { }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = themeColor), shape = RoundedCornerShape(12.dp)) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DeviceDetailTab(imei: String) {
    Column(modifier = Modifier.padding(16.dp)) {
        DetailRowItem("Model", "TECNO CL6")
        DetailRowItem("IMEI", imei)
        DetailRowItem("Android", "14")
        DetailRowItem("Status", "Connected")
    }
}

@Composable
fun CustomerDetailTab(name: String) {
    Column(modifier = Modifier.padding(16.dp)) {
        DetailRowItem("Customer", name)
        DetailRowItem("Phone", "0313-XXXXXXX")
    }
}

@Composable
fun EmiDetailTab() {
    Column(modifier = Modifier.padding(16.dp)) {
        DetailRowItem("Price", "45,000 PKR")
        DetailRowItem("Remaining", "15,000 PKR")
    }
}

@Composable
fun DetailRowItem(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray, fontSize = 14.sp)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
