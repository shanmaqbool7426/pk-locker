package com.example.pklocker.ui.dashboard

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

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
            .background(Color(0xFFF5F7FA))
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // --- Header Section (DYNAMIC) ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp),
                    color = Color.White,
                    shadowElevation = 2.dp
                ) {
                    Icon(Icons.Default.Widgets, contentDescription = null, modifier = Modifier.padding(10.dp), tint = Color(0xFF8D6E63))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Hi ${viewModel.shopName} ,", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    Text("PK Locker Admin", fontSize = 12.sp, color = Color.Gray)
                }
            }
            Row {
                Icon(Icons.Default.HeadsetMic, null, modifier = Modifier.padding(8.dp).size(20.dp), tint = Color.Gray)
                Icon(Icons.Default.Notifications, null, modifier = Modifier.padding(8.dp).size(20.dp), tint = Color.Gray)
                IconButton(onClick = { viewModel.initDashboard(context) }) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(20.dp), tint = Color.Gray)
                }
            }
        }

        // --- Banner Section ---
        Card(
            modifier = Modifier.fillMaxWidth().height(150.dp).padding(vertical = 8.dp),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Color(0xFFE65100), Color(0xFFFF9800)))
            )) {
                Column(modifier = Modifier.padding(20.dp).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    Surface(color = Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                        Text("U.S LOCKER", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    Text("SECURE YOUR EMI", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        BannerIconItem(Icons.Default.SimCard, "NO SIM")
                        BannerIconItem(Icons.Default.Public, "NO NET")
                        BannerIconItem(Icons.Default.PhonelinkLock, "LOCK")
                    }
                }
            }
        }

        // ─── VERSION BADGE — SIRF TEST K LIYE, BAAD MEIN HATANA HAI ────────────
        Surface(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF1B5E20)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "NEW CODE ACTIVE — v2.0 LOCK-FIX ✓",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Text("Dashboard", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(vertical = 12.dp))


        // --- Stats Row (DYNAMIC) ---
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PlatformStatCard(
                title = "Android", 
                icon = Icons.Default.Android, 
                av = stats?.android?.availableKeys ?: 0, 
                used = stats?.android?.usedKeys ?: 0, 
                total = stats?.android?.totalKeys ?: 0, 
                iconColor = Color(0xFF4CAF50), 
                modifier = Modifier.weight(1f)
            )
            PlatformStatCard(
                title = "iOS", 
                icon = Icons.Default.PhoneIphone, 
                av = stats?.ios?.availableKeys ?: 0, 
                used = stats?.ios?.usedKeys ?: 0, 
                total = stats?.ios?.totalKeys ?: 0, 
                iconColor = Color.Black, 
                modifier = Modifier.weight(1f)
            )
        }

        if (viewModel.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), color = Color(0xFF8D6E63))
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Contact Us", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        
        // --- Contact Card ---
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Contact US", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("+88 01901377582", color = Color.Gray, fontSize = 13.sp)
                }
                IconButton(onClick = {}, modifier = Modifier.background(Color(0xFFFFEBEE), CircleShape)) { 
                    Icon(Icons.Default.Phone, null, tint = Color.Red, modifier = Modifier.size(20.dp)) 
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = {}, modifier = Modifier.background(Color(0xFFE8F5E9), CircleShape)) { 
                    Icon(Icons.Default.Chat, null, tint = Color(0xFF25D366), modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Manage Android Customers", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(bottom = 12.dp))

        // --- Quick Management Grid (Linked to Device Stats) ---
        val actions = listOf(
            ActionData("Upcoming EMIs", stats?.devices?.total?.toString() ?: "0", Icons.Default.CalendarMonth, Color(0xFFFFF3E0)),
            ActionData("Active Customers", stats?.devices?.total?.toString() ?: "0", Icons.Default.People, Color(0xFFE8F5E9)),
            ActionData("Deregistered", stats?.devices?.deregistered?.toString() ?: "0", Icons.Default.PersonRemove, Color(0xFFFFEBEE)),
            ActionData("QR Code", "4", Icons.Default.QrCode, Color(0xFFE3F2FD)),
            ActionData("Phone QR", "5", Icons.Default.StayCurrentPortrait, Color(0xFFF3E5F5)),
            ActionData("Video Help", "6", Icons.Default.PlayCircle, Color(0xFFE0F2F1))
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (i in actions.indices step 2) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionGridItem(actions[i], Modifier.weight(1f)) { onMenuItemClick(actions[i].title) }
                    if (i + 1 < actions.size) ActionGridItem(actions[i+1], Modifier.weight(1f)) { onMenuItemClick(actions[i+1].title) }
                    else Spacer(Modifier.weight(1f))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun BannerIconItem(icon: ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Color.Yellow, modifier = Modifier.size(16.dp))
        Text(label, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PlatformStatCard(title: String, icon: ImageVector, av: Int, used: Int, total: Int, iconColor: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            StatTextRow("Available Keys:", av.toString())
            StatTextRow("Used Keys:", used.toString())
            StatTextRow("Total Keys:", total.toString())
        }
    }
}

@Composable
fun StatTextRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 11.sp, color = Color.Gray)
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ActionGridItem(data: ActionData, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.height(110.dp).clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(data.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                Spacer(modifier = Modifier.height(12.dp))
                Surface(shape = CircleShape, color = data.color, modifier = Modifier.size(40.dp)) {
                    Icon(data.icon, null, modifier = Modifier.padding(10.dp), tint = Color.Black.copy(alpha = 0.6f))
                }
            }
            // Number Badge
            Surface(
                modifier = Modifier.padding(12.dp).size(22.dp).align(Alignment.BottomStart),
                shape = CircleShape,
                color = Color(0xFF8D6E63)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(data.id, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

data class ActionData(val title: String, val id: String, val icon: ImageVector, val color: Color)
