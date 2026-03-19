package com.example.pklocker.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

// Fixed Color Palette to prevent Dark Theme washout
val AppBg = Color(0xFFF4F7FA)
val TextTitle = Color(0xFF111827)     // Deep Dark Gray for primary text
val TextSubtitle = Color(0xFF6B7280)  // Medium Gray for secondary text
val PrimaryBlue = Color(0xFF2563EB)   // Brand Blue
val CardSurface = Color.White

@Composable
fun DashboardScreen(
    onMenuItemClick: (String) -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    // Initialize data from server
    LaunchedEffect(Unit) {
        viewModel.initDashboard(context)
    }

    val stats = viewModel.dashboardData

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .verticalScroll(scrollState)
    ) {
        // --- Top Header Area (Modern Clean Look) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Profile Avatar
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            viewModel.shopName.take(1).uppercase(),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            "Welcome back,",
                            fontSize = 12.sp,
                            color = TextSubtitle
                        )
                        Text(
                            viewModel.shopName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextTitle
                        )
                    }
                }
                IconButton(
                    onClick = { viewModel.initDashboard(context) },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFF3F4F6), CircleShape)
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(20.dp), tint = TextTitle)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Premium PK LOCKER Banner ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(Color(0xFF111827), Color(0xFF1F2937)))) // Super sleek dark slate
            ) {
                // Background Decorative Icon
                Icon(
                    Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(140.dp)
                        .offset(x = 20.dp, y = 20.dp),
                    tint = Color.White.copy(alpha = 0.03f)
                )

                Column(modifier = Modifier.padding(24.dp)) {
                    Surface(
                        color = Color(0xFF3B82F6).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "PK LOCKER SECURE",
                            color = Color(0xFF93C5FD),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "EMI Protection Active",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        BannerFeatureItem(Icons.Default.SimCardAlert, "NO SIM", Color(0xFFF87171))
                        BannerFeatureItem(Icons.Default.WifiOff, "NO NET", Color(0xFFFBBF24))
                        BannerFeatureItem(Icons.Default.LockClock, "AUTO LOCK", Color(0xFF34D399))
                    }
                }
            }
        }

        if (viewModel.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), color = PrimaryBlue)
        }

        Spacer(modifier = Modifier.height(28.dp))
        Text(
            "System Stats",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = TextTitle,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
        )

        // --- Stats Row ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PlatformStatCard(
                title = "Android",
                icon = Icons.Default.Android,
                av = stats?.android?.availableKeys ?: 0,
                used = stats?.android?.usedKeys ?: 0,
                total = stats?.android?.totalKeys ?: 0,
                iconColor = Color(0xFF10B981),
                modifier = Modifier.weight(1f)
            )
            PlatformStatCard(
                title = "iOS",
                icon = Icons.Default.PhoneIphone,
                av = stats?.ios?.availableKeys ?: 0,
                used = stats?.ios?.usedKeys ?: 0,
                total = stats?.ios?.totalKeys ?: 0,
                iconColor = TextTitle,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))
        Text(
            "Manage Customers",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = TextTitle,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
        )

        // --- Beautiful Grid ---
        val actions = listOf(
            ActionData("Upcoming EMIs", stats?.devices?.total?.toString() ?: "0", Icons.Default.CalendarToday, Color(0xFFFFF7ED), Color(0xFFEA580C)),
            ActionData("Active Customers", stats?.devices?.total?.toString() ?: "0", Icons.Default.PeopleAlt, Color(0xFFECFDF5), Color(0xFF059669)),
            ActionData("Deregistered", stats?.devices?.deregistered?.toString() ?: "0", Icons.Default.PersonOff, Color(0xFFFEF2F2), Color(0xFFDC2626)),
            ActionData("QR Code", "4", Icons.Default.QrCodeScanner, Color(0xFFEFF6FF), Color(0xFF2563EB)),
            ActionData("Phone QR", "5", Icons.Default.PhoneAndroid, Color(0xFFF5F3FF), Color(0xFF7C3AED)),
            ActionData("Video Help", "6", Icons.Default.OndemandVideo, Color(0xFFF0FDF4), Color(0xFF16A34A))
        )

        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            for (i in actions.indices step 2) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ActionGridItem(actions[i], Modifier.weight(1f)) { onMenuItemClick(actions[i].title) }
                    if (i + 1 < actions.size) ActionGridItem(actions[i+1], Modifier.weight(1f)) { onMenuItemClick(actions[i+1].title) }
                    else Spacer(Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
        Text(
            "Support",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = TextTitle,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
        )

        // --- Support Card ---
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = borderStroke(),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFEFF6FF),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.SupportAgent, null, tint = PrimaryBlue, modifier = Modifier.padding(12.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Contact Helpdesk", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextTitle)
                    Text("+88 01901377582", color = TextSubtitle, fontSize = 13.sp)
                }
                Icon(Icons.Default.ArrowForwardIos, null, modifier = Modifier.size(16.dp), tint = Color.LightGray)
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}

// ─── HELPER COMPONENTS ──────────────────────────────────────────────────────

fun borderStroke() = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB))

@Composable
fun BannerFeatureItem(icon: ImageVector, label: String, tintColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = tintColor.copy(alpha = 0.2f),
            shape = CircleShape,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(icon, null, tint = tintColor, modifier = Modifier.padding(6.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun PlatformStatCard(title: String, icon: ImageVector, av: Int, used: Int, total: Int, iconColor: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = borderStroke(),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = TextTitle)
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.height(20.dp))
            StatTextRow("Available", av.toString(), Color(0xFF10B981))
            StatTextRow("Used", used.toString(), TextSubtitle)
            StatTextRow("Total", total.toString(), TextTitle, isBold = true)
        }
    }
}

@Composable
fun StatTextRow(label: String, value: String, valueColor: Color, isBold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = TextSubtitle)
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
            color = valueColor
        )
    }
}

@Composable
fun ActionGridItem(data: ActionData, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.height(120.dp).clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = borderStroke(),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = data.bgColor,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(data.icon, null, modifier = Modifier.padding(8.dp), tint = data.iconColor)
                }
                
                // Value pill
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFF3F4F6)
                ) {
                    Text(
                        data.id, 
                        color = TextTitle, 
                        fontSize = 12.sp, 
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Text(data.title, color = TextTitle, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

data class ActionData(val title: String, val id: String, val icon: ImageVector, val bgColor: Color, val iconColor: Color)
