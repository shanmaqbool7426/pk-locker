package com.example.pklocker.ui.devices

import androidx.compose.foundation.background
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
import com.example.pklocker.ui.theme.*

// Premium Theme Colors
val PrimaryColor = Color(0xFF1A237E) // Deep Navy
val SecondaryColor = Color(0xFF3F51B5)
val AccentColor = Color(0xFFC5CAE9)
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
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Action", "Device Detail", "Customer", "EMI Detail")
    var isOnlineMode by remember { mutableStateOf(true) }

    // Confirmation Dialog States
    var showConfirmDialog by remember { mutableStateOf(false) }
    var pendingLockState by remember { mutableStateOf(false) }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(if (pendingLockState) "Confirm Lock" else "Confirm Unlock") },
            text = { Text("Are you sure you want to ${if (pendingLockState) "LOCK" else "UNLOCK"} this device? This action is immediate.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.toggleLock(context, imei, pendingLockState)
                        showConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (pendingLockState) Color.Red else PrimaryColor)
                ) {
                    if (viewModel.isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    else Text("YES, PROCEED")
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
                            Text("Check Status: Unlocked", fontSize = 12.sp, color = Color.White.copy(0.7f))
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryColor)
                )
                
                // Profile Header
                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        modifier = Modifier.size(90.dp),
                        color = Color.White.copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(2.dp, Color.White.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Person, null, modifier = Modifier.padding(20.dp), tint = Color.White)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(customerName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                // Custom Tab Row
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color.White).padding(vertical = 12.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    tabs.forEachIndexed { index, title ->
                        TabButton(
                            title = title,
                            isSelected = selectedTab == index,
                            onClick = { selectedTab = index },
                            activeColor = PrimaryColor
                        )
                    }
                }
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().background(Color.White).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { 
                        pendingLockState = true
                        showConfirmDialog = true 
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("LOCK DEVICE", fontWeight = FontWeight.Bold)
                }
                
                Button(
                    onClick = { 
                        pendingLockState = false
                        showConfirmDialog = true 
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
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
                        
                        if (isOnlineMode) ActionTabContent(viewModel, imei, PrimaryColor)
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
        border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(0.3f)) else null
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
fun ActionTabContent(viewModel: DeviceListViewModel, imei: String, themeColor: Color) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(scrollState)) {
        ControlGroup("Device Safety") {
            LabeledSwitchItem("USB Debugging Lock", Icons.Default.Usb, false, themeColor) { viewModel.sendControl(context, imei, "usb_lock", it) }
            LabeledSwitchItem("Camera Access", Icons.Default.CameraAlt, true, themeColor) { viewModel.sendControl(context, imei, "camera_disable", !it) }
            LabeledSwitchItem("Settings Blocking", Icons.Default.Settings, true, themeColor) { viewModel.sendControl(context, imei, "settings_lock", it) }
        }
        
        ControlGroup("Application Control") {
            LabeledSwitchItem("Instagram", Icons.Default.Camera, true, themeColor) { viewModel.sendControl(context, imei, "instagram_block", !it) }
            LabeledSwitchItem("WhatsApp", Icons.Default.Chat, true, themeColor) { viewModel.sendControl(context, imei, "whatsapp_block", !it) }
            LabeledSwitchItem("Facebook", Icons.Default.Public, true, themeColor) { viewModel.sendControl(context, imei, "facebook_block", !it) }
            LabeledSwitchItem("YouTube", Icons.Default.PlayCircle, true, themeColor) { viewModel.sendControl(context, imei, "youtube_block", !it) }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun ControlGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 12.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            content()
        }
    }
}

@Composable
fun LabeledSwitchItem(label: String, icon: ImageVector, initialValue: Boolean, themeColor: Color, onToggle: (Boolean) -> Unit) {
    var checked by remember { mutableStateOf(initialValue) }
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.Gray.copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium, fontSize = 14.sp)
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFFEEEEEE)).padding(2.dp)
        ) {
            Surface(
                onClick = { checked = false; onToggle(false) },
                color = if (!checked) Color.White else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
                shadowElevation = if (!checked) 2.dp else 0.dp
            ) {
                Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("OFF", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = if(!checked) Color.Black else Color.Gray)
                }
            }
            Surface(
                onClick = { checked = true; onToggle(true) },
                color = if (checked) themeColor else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
                shadowElevation = if (checked) 2.dp else 0.dp
            ) {
                Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("ON", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = if(checked) Color.White else Color.Gray)
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
    Button(
        onClick = { },
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(50.dp),
        colors = ButtonDefaults.buttonColors(containerColor = themeColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
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
