package com.example.pklocker.ui.emi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pklocker.data.DeviceResponse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmiListScreen(
    onBack: () -> Unit,
    devices: List<DeviceResponse> = emptyList()
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upcoming EMIs", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF8D6E63))
            )
        },
        containerColor = Color(0xFFF5F7FA)
    ) { padding ->
        if (devices.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No EMIs found", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(devices) { device ->
                    EmiItemCard(device)
                }
            }
        }
    }
}

@Composable
fun EmiItemCard(device: DeviceResponse) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black,
                modifier = Modifier.size(50.dp)
            ) {
                Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.padding(10.dp))
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                EmiDetailRow("Customer Name:", device.customerName)
                EmiDetailRow("Mobile:", device.phoneNumber)
                EmiDetailRow("Total Loan Amount:", "${device.totalPrice}")
                EmiDetailRow("EMI Date:", device.emiStartDate?.substringBefore("T") ?: "N/A")
                EmiDetailRow("EMI Amount:", "${device.emiAmount}")
            }

            Button(
                onClick = { /* Mark as Paid Logic */ },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8D6E63)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("Mark as Paid", fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun EmiDetailRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 1.dp)) {
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.width(110.dp))
        Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
    }
}
