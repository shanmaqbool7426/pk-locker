package com.pksafe.lock.manager.ui.emi

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pksafe.lock.manager.data.DeviceResponse
import java.text.NumberFormat
import java.util.Locale

// Professional App Theme Colors
val AppBg = Color(0xFFF4F7FA)
val TextTitle = Color(0xFF111827)
val TextSubtitle = Color(0xFF6B7280)
val PrimaryBlue = Color(0xFF2563EB)
val BorderColor = Color(0xFFE5E7EB)
val CardSurface = Color.White
val SuccessGreen = Color(0xFF10B981)
val DangerRed = Color(0xFFEF4444)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmiListScreen(
    onBack: () -> Unit,
    devices: List<DeviceResponse> = emptyList()
) {
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { 
                        Text(
                            "Upcoming EMIs", 
                            color = TextTitle, 
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        ) 
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextTitle)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                    modifier = Modifier.shadow(elevation = 2.dp)
                )
            }
        },
        containerColor = AppBg
    ) { padding ->
        if (devices.isEmpty()) {
            EmptyStateView(padding)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Stats Row (Optionally can add total count here)
                item {
                    Text(
                        text = "Pending Approvals & Collections",
                        color = TextSubtitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                items(devices) { device ->
                    EmiItemCard(device)
                }
                
                item {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
    }
}

@Composable
fun EmiItemCard(device: DeviceResponse) {
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "PK"))
    formatter.maximumFractionDigits = 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, BorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header Row: Avatar & Name & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Initial Avatar
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(PrimaryBlue.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = device.customerName.take(1).uppercase(),
                        color = PrimaryBlue,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.customerName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextTitle
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PhoneIphone, null, tint = TextSubtitle, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = device.phoneNumber,
                            fontSize = 12.sp,
                            color = TextSubtitle,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Device Status Pill
                val isLocked = device.status == "Locked"
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isLocked) DangerRed.copy(0.1f) else SuccessGreen.copy(0.1f)
                ) {
                    Text(
                        text = device.status.uppercase(),
                        color = if (isLocked) DangerRed else SuccessGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = BorderColor.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            // Details Grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                EmiDataBlock(
                    icon = Icons.Default.CalendarMonth,
                    label = "Due Date",
                    value = device.emiStartDate?.substringBefore("T") ?: "N/A",
                    valueColor = TextTitle
                )
                EmiDataBlock(
                    icon = Icons.Default.Payment,
                    label = "EMI Amount",
                    value = formatter.format(device.emiAmount),
                    valueColor = DangerRed // Highlighted as due
                )
                EmiDataBlock(
                    icon = Icons.Default.AccountBalanceWallet,
                    label = "Total Loan",
                    value = formatter.format(device.totalPrice),
                    valueColor = TextTitle
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Button
            Button(
                onClick = { /* TODO: Mark EMI as Paid logic */ },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.CheckCircleOutline, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mark as Paid", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun EmiDataBlock(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, valueColor: Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = TextSubtitle, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, color = TextSubtitle, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun EmptyStateView(padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = PrimaryBlue.copy(alpha = 0.1f),
            modifier = Modifier.size(80.dp)
        ) {
            Icon(
                Icons.Default.CloudDone,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "All Caught Up!",
            color = TextTitle,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "You do not have any upcoming EMIs at the moment.",
            color = TextSubtitle,
            fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 40.dp)
        )
    }
}
