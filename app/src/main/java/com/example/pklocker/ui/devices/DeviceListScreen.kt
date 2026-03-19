package com.example.pklocker.ui.devices

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
import com.example.pklocker.data.DeviceResponse
import com.example.pklocker.ui.theme.*

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

    // Fetch devices on startup
    LaunchedEffect(Unit) {
        viewModel.fetchDevices(context)
    }

    if (showDialog && deviceToToggle != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Confirm ${targetStatus}", fontWeight = FontWeight.Bold, color = TextTitle) },
            text = { Text("Are you sure you want to ${targetStatus.lowercase()} ${deviceToToggle?.customerName}'s device?") },
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
                        trailingIcon = { if(searchQuery.isNotEmpty()) IconButton(onClick = {searchQuery = ""}) { Icon(Icons.Default.Close, null, tint = TextMuted) } },
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
                            onViewDetail = { onDeviceClick(device.imei, device.customerName) }
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
}

@Composable
fun PremiumDeviceCard(
    device: DeviceResponse,
    onLockClick: () -> Unit,
    onUnlockClick: () -> Unit,
    onViewDetail: () -> Unit
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
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Detail Button (Primary Action)
                Button(
                    onClick = onViewDetail,
                    modifier = Modifier.weight(1.8f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E293B), // Deep Slate
                        contentColor = Color.White // Fix: Make text/icon white for better contrast
                    )
                ) {
                    Icon(Icons.Default.SettingsSuggest, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("CONTROL PANEL", fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                }
                
                // Quick Lock/Unlock
                QuickIconButton(
                    icon = if(isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                    color = if(isLocked) Color(0xFF16A34A) else Color(0xFFDC2626),
                    onClick = if(isLocked) onUnlockClick else onLockClick,
                    modifier = Modifier.weight(1f)
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
