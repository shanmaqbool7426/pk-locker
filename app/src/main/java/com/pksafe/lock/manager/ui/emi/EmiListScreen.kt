package com.pksafe.lock.manager.ui.emi

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pksafe.lock.manager.data.DeviceResponse
import com.pksafe.lock.manager.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

// ═════════════════════════════════════════════════════════════════════════════
//  PK LOCKER — EMI List Screen (Professional Redesign)
//  All business logic preserved. UI completely refreshed.
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmiListScreen(
    onBack: () -> Unit,
    devices: List<DeviceResponse> = emptyList()
) {
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "PK"))
    formatter.maximumFractionDigits = 0

    val totalAmount = devices.sumOf { it.emiAmount }
    val lockedCount = devices.count { it.status == "Locked" }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(CardWhite)) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Upcoming EMIs",
                            color = TextTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextTitle)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CardWhite)
                )

                // Summary strip
                if (devices.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SummaryChip(
                            label = "Pending",
                            value = "${devices.size}",
                            color = Warning,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryChip(
                            label = "Collection",
                            value = formatter.format(totalAmount),
                            color = BrandBlue,
                            modifier = Modifier.weight(1.5f)
                        )
                        SummaryChip(
                            label = "Locked",
                            value = "$lockedCount",
                            color = if (lockedCount > 0) Danger else TextMuted,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        containerColor = SoftBg
    ) { padding ->
        if (devices.isEmpty()) {
            EmptyEmiState(padding)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Pending Collections",
                        color = TextMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                }

                items(devices) { device ->
                    EmiCard(device = device, formatter = formatter)
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  EMI CARD
// ═════════════════════════════════════════════════════════════════════════════
@Composable
private fun EmiCard(device: DeviceResponse, formatter: NumberFormat) {
    val isLocked = device.status == "Locked"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {

            // ── Header: Avatar + Name + Status ───────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                if (isLocked) listOf(DangerLight, Color(0xFFFECACA))
                                else listOf(BrandBlueLight, Color(0xFFBFDBFE))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        device.customerName.take(1).uppercase(),
                        color = if (isLocked) Danger else BrandBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        device.customerName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Phone, null, tint = TextSubtle, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(device.phoneNumber, fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                    }
                }

                // Status badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isLocked) DangerLight else SuccessLight
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isLocked) Danger else Success)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            device.status.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isLocked) Danger else Success,
                            letterSpacing = 0.3.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Info Grid ────────────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SurfaceGray.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    EmiDataBlock(
                        icon = Icons.Default.CalendarMonth,
                        label = "Due Date",
                        value = device.emiStartDate?.substringBefore("T") ?: "N/A",
                        valueColor = TextTitle
                    )
                    EmiDataBlock(
                        icon = Icons.Default.Payment,
                        label = "EMI",
                        value = formatter.format(device.emiAmount),
                        valueColor = Danger
                    )
                    EmiDataBlock(
                        icon = Icons.Default.AccountBalanceWallet,
                        label = "Total",
                        value = formatter.format(device.totalPrice),
                        valueColor = TextTitle
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Action Button ────────────────────────────────────────────
            Button(
                onClick = { /* TODO: Mark EMI as Paid logic */ },
                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Icon(Icons.Default.CheckCircleOutline, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mark as Paid", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  SHARED COMPONENTS
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun SummaryChip(label: String, value: String, color: Color, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(2.dp))
            Text(label, fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun EmiDataBlock(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    valueColor: Color
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = TextSubtle, modifier = Modifier.size(13.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, color = TextSubtle, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EmptyEmiState(padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = Success.copy(alpha = 0.08f),
            modifier = Modifier.size(100.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.CloudDone,
                    null,
                    tint = Success.copy(alpha = 0.5f),
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "All Caught Up!",
            color = TextTitle,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "No upcoming EMI payments at the moment.",
            color = TextMuted,
            fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
