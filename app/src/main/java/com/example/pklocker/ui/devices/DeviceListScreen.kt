package com.example.pklocker.ui.devices

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pklocker.data.DeviceResponse
import com.example.pklocker.ui.theme.*

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
            title = { Text("Confirm ${targetStatus}", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to ${targetStatus.lowercase()} ${deviceToToggle?.customerName}'s device?") },
            confirmButton = {
                Button(
                    onClick = {
                        // FIX: Pass true for "Lock" and false for "Unlock"
                        viewModel.toggleLock(context, deviceToToggle!!.imei, targetStatus == "Lock")
                        showDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (targetStatus == "Lock") ErrorRed else SuccessGreen)
                ) {
                    Text("YES, PROCEED", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("CANCEL", color = Color.Gray)
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White
        )
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(PrimaryDark).padding(bottom = 8.dp)) {
                CenterAlignedTopAppBar(
                    title = { Text("Customers List", color = Color.White, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { /* Handle Back */ }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.fetchDevices(context) }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = PrimaryDark)
                )
                
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search by Name or IMEI...", color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = AccentOrange
                    ),
                    singleLine = true
                )
            }
        },
        containerColor = BackgroundGray
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (viewModel.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = AccentOrange)
            } else if (viewModel.devices.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Devices, contentDescription = null, modifier = Modifier.size(60.dp), tint = Color.LightGray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No devices found on server", color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.fetchDevices(context) }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryDark)) {
                        Text("Refresh")
                    }
                }
            } else {
                val filteredDevices = viewModel.devices.filter { 
                    it.customerName.contains(searchQuery, ignoreCase = true) || it.imei.contains(searchQuery)
                }
                
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(filteredDevices) { device ->
                        DeviceItemCompetitorStyle(
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
            }
        }
    }
}

@Composable
fun DeviceItemCompetitorStyle(
    device: DeviceResponse,
    onLockClick: () -> Unit,
    onUnlockClick: () -> Unit,
    onViewDetail: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = Color.LightGray.copy(alpha = 0.2f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.padding(8.dp), tint = PrimaryDark)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = device.customerName, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = PrimaryDark)
                    Text(text = "QR Provisioning", fontSize = 12.sp, color = ErrorRed, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp, color = Color.LightGray)

            InfoRow("Mobile:", device.phoneNumber)
            InfoRow("Reg. Date:", device.registeredAt?.substringBefore("T") ?: "N/A")
            InfoRow("IMEI Number:", device.imei)
            InfoRow("Status:", "Install", valueColor = ErrorRed)
            InfoRow("Lock Status:", device.status, valueColor = if (device.status.equals("Locked", ignoreCase = true)) ErrorRed else SuccessGreen)

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionButton(Icons.Default.Lock, "1", onClick = onLockClick)
                Spacer(modifier = Modifier.width(20.dp))
                ActionButton(Icons.Default.LockOpen, "2", onClick = onUnlockClick)
                Spacer(modifier = Modifier.width(20.dp))
                ActionButton(Icons.Default.ManageSearch, "3", onClick = onViewDetail)
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, valueColor: Color = PrimaryDark) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(text = label, modifier = Modifier.width(100.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = valueColor)
    }
}

@Composable
fun ActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, number: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            IconButton(
                onClick = onClick,
                modifier = Modifier
                    .size(45.dp)
                    .background(Color(0xFF8D6E63), CircleShape)
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Surface(
                modifier = Modifier.align(Alignment.TopStart).offset(x = (-4).dp, y = (-4).dp),
                shape = CircleShape,
                color = PrimaryDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White)
            ) {
                Text(
                    text = number,
                    fontSize = 10.sp,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}
