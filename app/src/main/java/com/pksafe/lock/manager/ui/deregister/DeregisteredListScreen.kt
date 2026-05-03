package com.pksafe.lock.manager.ui.deregister

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pksafe.lock.manager.data.DeviceResponse
import com.pksafe.lock.manager.ui.theme.*

// Consitant Premium Theme Colors
private val SoftBg = Color(0xFFF8FAFC)
private val CardWhite = Color.White
private val BrandBlue = Color(0xFF2563EB)
private val TextTitle = Color(0xFF0F172A)
private val TextMuted = Color(0xFF64748B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeregisteredListScreen(
    onBack: () -> Unit,
    viewModel: DeregisteredListViewModel = viewModel()
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    // Fetch on startup
    LaunchedEffect(Unit) {
        viewModel.fetchDeregisteredDevices(context)
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(CardWhite)) {
                CenterAlignedTopAppBar(
                    title = { Text("Deregistered Users", fontWeight = FontWeight.Black, color = TextTitle) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = TextTitle)
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.fetchDeregisteredDevices(context) }) {
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
                        placeholder = { Text("Search Released IMEIs...", color = TextMuted, fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted, modifier = Modifier.size(20.dp)) },
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
                EmptyDeregisteredPlaceholder { viewModel.fetchDeregisteredDevices(context) }
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
                        DeregisteredItemCard(device)
                    }
                }
            }
        }
    }
}

@Composable
fun DeregisteredItemCard(device: DeviceResponse) {
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
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF1F5F9)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PersonOff, contentDescription = null, tint = TextMuted, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = device.customerName, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = TextTitle)
                    Text("IMEI: ${device.imei}", fontSize = 12.sp, color = TextMuted)
                }
                
                // Status Badge (Released)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFF1F5F9)
                ) {
                    Text(
                        text = "RELEASED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = TextMuted,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), thickness = 1.dp, color = Color(0xFFF1F5F9))

            Row(modifier = Modifier.fillMaxWidth()) {
                DeregInfoColumn("Mobile", device.phoneNumber, Modifier.weight(1f))
                DeregInfoColumn("Device", "${device.brand} ${device.model ?: ""}", Modifier.weight(1.5f))
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(modifier = Modifier.fillMaxWidth()) {
                DeregInfoColumn("Registered", device.registeredAt?.substringBefore("T") ?: "N/A", Modifier.weight(1f))
                DeregInfoColumn("Deregistered", "Permanently Removed", Modifier.weight(1.5f), Color(0xFFDC2626))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // State Message instead of Buttons
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFF0FDF4),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Verified, null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Device has been successfully deregistered and keys released.",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF16A34A)
                    )
                }
            }
        }
    }
}

@Composable
fun DeregInfoColumn(label: String, value: String, modifier: Modifier, valColor: Color = TextTitle) {
    Column(modifier = modifier) {
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = valColor)
    }
}

@Composable
fun EmptyDeregisteredPlaceholder(onRefresh: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.History, null, modifier = Modifier.size(80.dp), tint = Color(0xFFE2E8F0))
        Spacer(modifier = Modifier.height(16.dp))
        Text("No History Found", fontWeight = FontWeight.Black, fontSize = 20.sp, color = TextTitle)
        Text("Deregistered devices will appear here after release.", color = TextMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRefresh,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
        ) {
            Text("REFRESH LIST")
        }
    }
}
