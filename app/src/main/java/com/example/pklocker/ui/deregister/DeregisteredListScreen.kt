package com.example.pklocker.ui.deregister

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pklocker.data.DeviceResponse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeregisteredListScreen(
    onBack: () -> Unit,
    devices: List<DeviceResponse> = emptyList()
) {
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Deregistered Users", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF8D6E63))
            )
        },
        containerColor = Color(0xFFF5F7FA)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Bar (Image 4 Style)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search by Name or IMEI...") },
                trailingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White
                )
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val mockDeregistered = listOf(
                    DeviceResponse(
                        imei = "868401070525374",
                        customerName = "Ridy molla",
                        cnic = "33100-0000000-0",
                        phoneNumber = "017XXXXXXXX",
                        brand = "Samsung",
                        model = "A14",
                        status = "Uninstall",
                        registeredAt = "2025-06-26 14:23:21"
                    ),
                    DeviceResponse(
                        imei = "867911078507126",
                        customerName = "mohabat/89",
                        cnic = "33100-1111111-1",
                        phoneNumber = "018XXXXXXXX",
                        brand = "Tecno",
                        model = "Spark 20",
                        status = "Uninstall",
                        registeredAt = "2025-06-24 19:23:27"
                    )
                )

                items(mockDeregistered) { device ->
                    DeregisterItemCard(device)
                }
            }
        }
    }
}

@Composable
fun DeregisterItemCard(device: DeviceResponse) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE0E0E0),
                    modifier = Modifier.size(50.dp)
                ) {
                    Icon(Icons.Default.Person, null, modifier = Modifier.padding(10.dp), tint = Color.Gray)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = device.customerName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(text = "QR Provisioning", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp, color = Color.LightGray)

            DeregRow("Mobile:", device.phoneNumber)
            DeregRow("Reg. Date:", device.registeredAt ?: "N/A")
            DeregRow("IMEI Number:", device.imei)
            DeregRow("Status:", device.status, Color.Red)
            DeregRow("Lock Status:", "Lock Status", Color.Red)

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8D6E63)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Deregister", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DeregRow(label: String, value: String, valueColor: Color = Color.Black) {
    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, modifier = Modifier.width(100.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.DarkGray)
        Text(text = value, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = valueColor)
    }
}
