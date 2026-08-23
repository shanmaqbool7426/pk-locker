package com.pksafe.lock.manager.ui.dashboard

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pksafe.lock.manager.data.DashboardAnalytics

// ═════════════════════════════════════════════════════════════════════════════
//  PK LOCKER — Professional Dashboard UI
//  Signature: fun DashboardScreen(onMenuItemClick: (String) -> Unit, ...)
//  Existing navigation/logic is preserved; only visuals are redesigned.
// ═════════════════════════════════════════════════════════════════════════════

private val BgGray       = Color(0xFFF8FAFC)
private val CardWhite    = Color.White
private val Primary      = Color(0xFF2563EB)
private val PrimaryDark  = Color(0xFF1D4ED8)
private val TextPrimary  = Color(0xFF0F172A)
private val TextSecondary = Color(0xFF64748B)
private val Success      = Color(0xFF10B981)
private val Danger       = Color(0xFFEF4444)
private val Warning      = Color(0xFFF59E0B)
private val Info         = Color(0xFF06B6D4)
private val Border       = Color(0xFFE2E8F0)

@Composable
fun DashboardScreen(
    onMenuItemClick: (String) -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        viewModel.initDashboard(context)
    }

    val stats = viewModel.dashboardData
    val analytics = viewModel.analytics

    Box(modifier = Modifier.fillMaxSize().background(BgGray)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 24.dp)
        ) {
            // ── HEADER ─────────────────────────────────────────────────────
            DashboardHeader(
                shopName = viewModel.shopName,
                shopPhone = viewModel.shopPhone,
                isAdmin = viewModel.isAdmin,
                onRefresh = { viewModel.initDashboard(context) },
                onShareApk = { shareApk(context) }
            )

            // ── WELCOME BANNER ─────────────────────────────────────────────
            WelcomeBanner(
                totalDevices = stats?.devices?.total ?: 0,
                lockedDevices = stats?.devices?.locked ?: 0,
                availableKeys = stats?.android?.availableKeys ?: 0,
                onBuyKeysClick = { onMenuItemClick("Buy Keys") }
            )

            // ── KEY STATS GRID ─────────────────────────────────────────────
            SectionTitle("Business Overview")
            StatsGrid(
                totalDevices = stats?.devices?.total ?: 0,
                lockedDevices = stats?.devices?.locked ?: 0,
                unlockedDevices = (stats?.devices?.total ?: 0) - (stats?.devices?.locked ?: 0),
                availableKeys = stats?.android?.availableKeys ?: 0,
                monthlyCollection = analytics?.monthlyCollection ?: 0.0,
                collectionRate = analytics?.collectionRate ?: "0"
            )

            // ── ANALYTICS CARDS ────────────────────────────────────────────
            if (analytics != null) {
                SectionTitle("Analytics")
                AnalyticsRow(analytics = analytics)
            }

            // ── QUICK SETUP ────────────────────────────────────────────────
            SectionTitle("Customer Device Setup")
            SetupCard(
                title = "Wireless ADB Setup",
                subtitle = "No cable needed. Pair with 6-digit code.",
                icon = Icons.Default.WifiTethering,
                gradient = Brush.horizontalGradient(listOf(Color(0xFFF59E0B), Color(0xFFD97706))),
                onClick = { onMenuItemClick("Wireless ADB") }
            )
            SetupCard(
                title = "Cable Activation",
                subtitle = "Fast USB connection for instant setup.",
                icon = Icons.Default.Usb,
                gradient = Brush.horizontalGradient(listOf(Color(0xFF6366F1), Color(0xFF4F46E5))),
                onClick = { onMenuItemClick("Cable Sync") }
            )

            // ── QUICK ACTIONS ──────────────────────────────────────────────
            SectionTitle("Quick Actions")
            QuickActionsGrid(onMenuItemClick = onMenuItemClick, isAdmin = viewModel.isAdmin)

            // ── HELP & SUPPORT ─────────────────────────────────────────────
            SectionTitle("Help & Support")
            SupportCard()

            Spacer(modifier = Modifier.height(16.dp))
        }

        if (viewModel.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                color = Primary
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  HEADER
// ═════════════════════════════════════════════════════════════════════════════
@Composable
private fun DashboardHeader(
    shopName: String,
    shopPhone: String,
    isAdmin: Boolean,
    onRefresh: () -> Unit,
    onShareApk: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardWhite)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Primary, PrimaryDark))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = shopName.take(1).uppercase().ifEmpty { "P" },
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = shopName.ifEmpty { "Shopkeeper" },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        if (isAdmin) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = shopPhone.ifEmpty { "PK Locker Partner" },
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderIconButton(Icons.Default.Refresh, onRefresh)
                HeaderIconButton(Icons.Default.Share, onShareApk)
            }
        }
    }
}

@Composable
private fun HeaderIconButton(icon: ImageVector, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .background(Color(0xFFF1F5F9), CircleShape)
    ) {
        Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(20.dp))
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  WELCOME BANNER
// ═════════════════════════════════════════════════════════════════════════════
@Composable
private fun WelcomeBanner(
    totalDevices: Int,
    lockedDevices: Int,
    availableKeys: Int,
    onBuyKeysClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A)))
                )
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = "Welcome Back!",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Manage your EMI devices and payments in one place.",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MiniStat("Total", totalDevices.toString(), Color.White)
                        MiniStat("Locked", lockedDevices.toString(), if (lockedDevices > 0) Danger else Color.White)
                        MiniStat("Keys", availableKeys.toString(), if (availableKeys <= 0) Danger else Success)
                    }
                    Button(
                        onClick = onBuyKeysClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Warning),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Buy Keys", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, valueColor: Color) {
    Column {
        Text(text = value, color = valueColor, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Text(text = label, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  STATS GRID
// ═════════════════════════════════════════════════════════════════════════════
@Composable
private fun StatsGrid(
    totalDevices: Int,
    lockedDevices: Int,
    unlockedDevices: Int,
    availableKeys: Int,
    monthlyCollection: Double,
    collectionRate: String
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                title = "Total Customers",
                value = totalDevices.toString(),
                icon = Icons.Default.PeopleAlt,
                iconBg = Color(0xFFDBEAFE),
                iconColor = Primary,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Locked Devices",
                value = lockedDevices.toString(),
                icon = Icons.Default.Lock,
                iconBg = Color(0xFFFEE2E2),
                iconColor = Danger,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                title = "Available Keys",
                value = availableKeys.toString(),
                icon = Icons.Default.Key,
                iconBg = Color(0xFFD1FAE5),
                iconColor = Success,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Unlocked Devices",
                value = unlockedDevices.toString(),
                icon = Icons.Default.LockOpen,
                iconBg = Color(0xFFFEF3C7),
                iconColor = Warning,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                title = "Monthly Collection",
                value = "Rs. ${formatNumber(monthlyCollection)}",
                icon = Icons.Default.AccountBalanceWallet,
                iconBg = Color(0xFFECFDF5),
                iconColor = Success,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Collection Rate",
                value = "${collectionRate.toDoubleOrNull() ?: 0.0}%",
                icon = Icons.Default.TrendingUp,
                iconBg = Color(0xFFE0F2FE),
                iconColor = Info,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    iconBg: Color,
    iconColor: Color,
    modifier: Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )
            Text(
                text = title,
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  ANALYTICS ROW
// ═════════════════════════════════════════════════════════════════════════════
@Composable
private fun AnalyticsRow(analytics: DashboardAnalytics) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AnalyticsCard(
            title = "High Risk",
            value = "${analytics.highRiskCount ?: 0}",
            subtitle = "Overdue 2+ EMIs",
            icon = Icons.Default.WarningAmber,
            color = if ((analytics.highRiskCount ?: 0) > 0) Danger else Success,
            modifier = Modifier.weight(1f)
        )
        AnalyticsCard(
            title = "Locked",
            value = "${analytics.deviceStats?.locked ?: 0}",
            subtitle = "Devices secured",
            icon = Icons.Default.PhonelinkLock,
            color = Primary,
            modifier = Modifier.weight(1f)
        )
        AnalyticsCard(
            title = "Unlocked",
            value = "${analytics.deviceStats?.unlocked ?: 0}",
            subtitle = "Active devices",
            icon = Icons.Default.PhonelinkRing,
            color = Success,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AnalyticsCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
            Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(text = subtitle, fontSize = 10.sp, color = TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  SETUP CARDS
// ═════════════════════════════════════════════════════════════════════════════
@Composable
private fun SetupCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradient: Brush,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(text = title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(text = subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                    }
                }
                Icon(
                    Icons.Default.ArrowForwardIos,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  QUICK ACTIONS GRID
// ═════════════════════════════════════════════════════════════════════════════
@Composable
private fun QuickActionsGrid(onMenuItemClick: (String) -> Unit, isAdmin: Boolean) {
    val actions = buildList {
        add(ActionItem("Register Device", Icons.Default.PersonAdd, Color(0xFFDBEAFE), Primary, "Register Device"))
        add(ActionItem("All Customers", Icons.Default.Groups, Color(0xFFD1FAE5), Success, "Active Customers"))
        add(ActionItem("Buy Keys", Icons.Default.Key, Color(0xFFFEF3C7), Warning, "Buy Keys"))
        add(ActionItem("EMI Payments", Icons.Default.CalendarMonth, Color(0xFFE0F2FE), Info, "Upcoming EMIs"))
        if (isAdmin) {
            add(ActionItem("Key Requests", Icons.Default.AdminPanelSettings, Color(0xFFFEE2E2), Danger, "Key Requests"))
        }
        add(ActionItem("Deregistered", Icons.Default.PersonRemove, Color(0xFFF3E8FF), Color(0xFF7C3AED), "Deregistered"))
    }

    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        for (i in actions.indices step 2) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton(actions[i], Modifier.weight(1f)) { onMenuItemClick(actions[i].route) }
                if (i + 1 < actions.size) {
                    ActionButton(actions[i + 1], Modifier.weight(1f)) { onMenuItemClick(actions[i + 1].route) }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ActionButton(item: ActionItem, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier
            .height(90.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(item.bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(item.icon, contentDescription = null, tint = item.iconColor, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = item.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  SUPPORT CARD
// ═════════════════════════════════════════════════════════════════════════════
@Composable
private fun SupportCard() {
    val context = LocalContext.current
    val WhatsAppGreen = Color(0xFF25D366)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clickable {
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = android.net.Uri.parse(
                            "https://wa.me/923069829158?text=${android.net.Uri.encode("Assalam o Alaikum, I need help with PK Locker app.")}"
                        )
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    try {
                        // Fallback: open WhatsApp Play Store page
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.whatsapp")
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) { }
                }
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = null,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(listOf(Color(0xFF25D366), Color(0xFF128C7E)))
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Help & Support", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(text = "Chat on WhatsApp — +92 306 9829158", fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(text = "Chat Now", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  HELPERS
// ═════════════════════════════════════════════════════════════════════════════
@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp)
    )
}

private fun formatNumber(value: Double): String {
    return if (value == value.toLong().toDouble()) {
        String.format("%,d", value.toLong())
    } else {
        String.format("%,.2f", value)
    }
}

private fun shareApk(context: Context) {
    try {
        val applicationInfo = context.applicationInfo
        val originalApk = java.io.File(applicationInfo.sourceDir)
        val sharedApk = java.io.File(context.cacheDir, "PK_Locker_Secure.apk")

        if (!sharedApk.exists() || sharedApk.length() != originalApk.length()) {
            originalApk.copyTo(sharedApk, overwrite = true)
        }

        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            val fileUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                sharedApk
            )
            putExtra(android.content.Intent.EXTRA_STREAM, fileUri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = android.content.Intent.createChooser(shareIntent, "Send App via Bluetooth / WhatsApp")
        chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Cannot share App directly: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
    }
}

private data class ActionItem(
    val title: String,
    val icon: ImageVector,
    val bgColor: Color,
    val iconColor: Color,
    val route: String
)
