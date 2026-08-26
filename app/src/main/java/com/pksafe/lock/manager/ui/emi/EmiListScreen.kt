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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pksafe.lock.manager.data.UpcomingEmi
import com.pksafe.lock.manager.ui.theme.*
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ═════════════════════════════════════════════════════════════════════════════
//  PK LOCKER — Upcoming EMIs Screen
//  Real EMI installments from GET /api/emis/upcoming (unpaid + partial),
//  grouped into Overdue / Upcoming, with working Mark-as-Paid.
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmiListScreen(
    onBack: () -> Unit,
    viewModel: EmiListViewModel = viewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Fetch real EMI data whenever the screen opens
    LaunchedEffect(Unit) {
        viewModel.fetchUpcomingEmis(context)
    }

    // Surface one-shot action feedback (mark paid success/failure)
    LaunchedEffect(viewModel.actionMessage) {
        viewModel.actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionMessage()
        }
    }

    val formatter = remember {
        NumberFormat.getCurrencyInstance(Locale("en", "PK")).apply { maximumFractionDigits = 0 }
    }
    val today = remember { LocalDate.now() }

    // ── Group installments by due date ──────────────────────────────────────
    val overdue = viewModel.upcomingEmis.filter { emi ->
        parseEmiDueDate(emi.emiDate)?.isBefore(today) == true
    }
    val upcoming = viewModel.upcomingEmis.filter { emi ->
        val due = parseEmiDueDate(emi.emiDate)
        due == null || !due.isBefore(today)
    }
    val dueIn30 = upcoming.count { emi ->
        val due = parseEmiDueDate(emi.emiDate)
        due != null && !due.isAfter(today.plusDays(30))
    }
    val unpaidTotal = viewModel.upcomingEmis.sumOf { it.remaining }

    // Mark-as-paid confirmation dialog state
    var emiToMarkPaid by remember { mutableStateOf<UpcomingEmi?>(null) }
    if (emiToMarkPaid != null) {
        val emi = emiToMarkPaid!!
        AlertDialog(
            onDismissRequest = { emiToMarkPaid = null },
            icon = {
                Icon(
                    Icons.Default.Payments, null,
                    tint = Success,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    "Mark EMI as Paid?",
                    fontWeight = FontWeight.Bold,
                    color = TextTitle
                )
            },
            text = {
                Text(
                    "${emi.customerName} — installment #${emi.installmentNumber}\n" +
                        "${formatter.format(emi.remaining)} will be recorded as received " +
                        "and the device balance will be updated.",
                    color = TextMuted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.markEmiPaid(context, emi)
                        emiToMarkPaid = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Success),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("CONFIRM", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { emiToMarkPaid = null }) {
                    Text("CANCEL", color = TextMuted, fontWeight = FontWeight.Medium)
                }
            },
            containerColor = CardWhite,
            shape = RoundedCornerShape(24.dp)
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    actions = {
                        IconButton(onClick = { viewModel.fetchUpcomingEmis(context) }) {
                            Icon(Icons.Default.Refresh, "Refresh", tint = TextTitle)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CardWhite)
                )

                // ── Summary strip (real numbers) ─────────────────────────────
                if (viewModel.upcomingEmis.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SummaryChip(
                            label = "Overdue",
                            value = "${overdue.size}",
                            color = if (overdue.isNotEmpty()) Danger else TextMuted,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryChip(
                            label = "Due 30d",
                            value = "$dueIn30",
                            color = Warning,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryChip(
                            label = "Unpaid",
                            value = formatter.format(unpaidTotal),
                            color = BrandBlue,
                            modifier = Modifier.weight(1.5f)
                        )
                    }
                }

                if (viewModel.isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = BrandBlue,
                        trackColor = BrandBlueLight
                    )
                }
            }
        },
        containerColor = SoftBg
    ) { padding ->
        when {
            // First load (no data yet)
            viewModel.isLoading && viewModel.upcomingEmis.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = BrandBlue, strokeWidth = 3.dp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Loading EMIs...", color = TextMuted, fontSize = 13.sp)
                    }
                }
            }

            // Error and nothing to show
            viewModel.errorMessage != null && viewModel.upcomingEmis.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.CloudOff, null,
                        tint = Danger.copy(alpha = 0.5f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        viewModel.errorMessage ?: "Something went wrong",
                        color = TextTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Check your internet connection and try again.",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { viewModel.fetchUpcomingEmis(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("RETRY", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // No unpaid EMIs at all
            viewModel.upcomingEmis.isEmpty() -> {
                EmptyEmiState(padding)
            }

            // Data
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (overdue.isNotEmpty()) {
                        item {
                            SectionHeader(
                                "Overdue (${overdue.size})",
                                Danger
                            )
                        }
                        items(overdue, key = { it.id }) { emi ->
                            EmiCard(
                                emi = emi,
                                formatter = formatter,
                                today = today,
                                isMarkingPaid = viewModel.markingPaidId == emi.id,
                                onMarkPaid = { emiToMarkPaid = emi }
                            )
                        }
                    }

                    if (upcoming.isNotEmpty()) {
                        item {
                            SectionHeader(
                                "Upcoming (${upcoming.size})",
                                BrandBlue
                            )
                        }
                        items(upcoming, key = { it.id }) { emi ->
                            EmiCard(
                                emi = emi,
                                formatter = formatter,
                                today = today,
                                isMarkingPaid = viewModel.markingPaidId == emi.id,
                                onMarkPaid = { emiToMarkPaid = emi }
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  EMI CARD — one unpaid/partial installment
// ═════════════════════════════════════════════════════════════════════════════
@Composable
private fun EmiCard(
    emi: UpcomingEmi,
    formatter: NumberFormat,
    today: LocalDate,
    isMarkingPaid: Boolean,
    onMarkPaid: () -> Unit
) {
    val due = parseEmiDueDate(emi.emiDate)
    val isOverdue = due?.isBefore(today) == true
    val isPartial = emi.status.equals("Partial", ignoreCase = true)
    val daysLate = if (isOverdue && due != null) Duration.between(due.atStartOfDay(), today.atStartOfDay()).toDays() else 0L
    val daysUntil = if (due != null && !isOverdue) Duration.between(today.atStartOfDay(), due.atStartOfDay()).toDays() else 0L

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isOverdue) Danger.copy(alpha = 0.4f) else BorderLight
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {

            // ── Header: Avatar + Name + Status badge ────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                if (isOverdue) listOf(DangerLight, Color(0xFFFECACA))
                                else listOf(BrandBlueLight, Color(0xFFBFDBFE))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        emi.customerName.take(1).uppercase(),
                        color = if (isOverdue) Danger else BrandBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        emi.customerName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Phone, null, tint = TextSubtle, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            emi.mobile ?: "N/A",
                            fontSize = 12.sp,
                            color = TextMuted,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Status badge: OVERDUE xNd / PARTIAL / DUE IN xNd / DUE TODAY
                StatusPill(isOverdue, isPartial, daysLate, daysUntil)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Info Grid ───────────────────────────────────────────────────
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
                        icon = Icons.Default.Event,
                        label = "Due Date",
                        value = formatEmiDueDate(emi.emiDate),
                        valueColor = if (isOverdue) Danger else TextTitle
                    )
                    EmiDataBlock(
                        icon = Icons.Default.Payment,
                        label = if (isPartial) "Remaining" else "EMI",
                        value = formatter.format(emi.remaining),
                        valueColor = if (isOverdue) Danger else TextTitle
                    )
                    EmiDataBlock(
                        icon = Icons.Default.AccountBalanceWallet,
                        label = "Installment",
                        value = "#${emi.installmentNumber}",
                        valueColor = TextTitle
                    )
                }
            }

            // Partial payment progress line
            if (isPartial && emi.paidAmount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle, null,
                        tint = Success, modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "${formatter.format(emi.paidAmount)} paid of ${formatter.format(emi.emiAmount)}",
                        fontSize = 11.sp,
                        color = Success,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Action Button ───────────────────────────────────────────────
            Button(
                onClick = onMarkPaid,
                enabled = !isMarkingPaid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isOverdue) Success else BrandBlue
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                if (isMarkingPaid) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(Icons.Default.CheckCircleOutline, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isPartial) "Pay Remaining" else "Mark as Paid",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  SHARED COMPONENTS
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun SectionHeader(title: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            title,
            color = TextTitle,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StatusPill(isOverdue: Boolean, isPartial: Boolean, daysLate: Long, daysUntil: Long) {
    val (bg, fg, text) = when {
        isOverdue && daysLate > 0 -> Triple(DangerLight, Danger, "LATE ${daysLate}d")
        isOverdue -> Triple(DangerLight, Danger, "OVERDUE")
        isPartial -> Triple(WarningLight, Warning, "PARTIAL")
        daysUntil == 0L -> Triple(WarningLight, Warning, "DUE TODAY")
        else -> Triple(InfoLight, Info, "IN ${daysUntil}d")
    }
    Surface(shape = RoundedCornerShape(20.dp), color = bg) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            letterSpacing = 0.3.sp
        )
    }
}

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
            Text(
                value,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
        Text(
            value,
            color = valueColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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

// ═════════════════════════════════════════════════════════════════════════════
//  DATE HELPERS — Mongo ISO strings → LocalDate / display text
// ═════════════════════════════════════════════════════════════════════════════

private fun parseEmiDueDate(iso: String?): LocalDate? {
    if (iso.isNullOrBlank()) return null
    return try {
        Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate()
    } catch (_: Exception) {
        try {
            LocalDate.parse(iso.substringBefore("T"))
        } catch (_: Exception) {
            null
        }
    }
}

private fun formatEmiDueDate(iso: String?): String {
    val due = parseEmiDueDate(iso) ?: return iso?.substringBefore("T") ?: "N/A"
    return try {
        due.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
    } catch (_: Exception) {
        iso?.substringBefore("T") ?: "N/A"
    }
}
