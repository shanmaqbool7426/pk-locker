package com.pksafe.lock.manager.ui.profile

import android.content.Context
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
import androidx.compose.runtime.*
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

// Consistent Premium Theme Colors
private val SoftBg = Color(0xFFF8FAFC)
private val CardWhite = Color.White
private val BrandBlue = Color(0xFF2563EB)
private val TextTitle = Color(0xFF0F172A)
private val TextMuted = Color(0xFF64748B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onLogout: () -> Unit) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE) }
    
    val shopName = sharedPrefs.getString("shop_name", "Shopkeeper") ?: "Shopkeeper"

    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = { Icon(Icons.Default.ExitToApp, null, tint = Color(0xFFDC2626), modifier = Modifier.size(40.dp)) },
            title = { Text("Confirm Logout", fontWeight = FontWeight.Black, color = TextTitle) },
            text = { Text("Are you sure you want to end your session?") },
            confirmButton = {
                Button(
                    onClick = { 
                        showLogoutDialog = false 
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("YES, LOGOUT", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = CardWhite,
            shape = RoundedCornerShape(20.dp)
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Security Settings", fontWeight = FontWeight.ExtraBold, color = TextTitle) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CardWhite)
            )
        },
        containerColor = SoftBg
    ) { padding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Premium Header Section with Gradient Avatar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardWhite)
                    .padding(bottom = 32.dp, top = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFF3B82F6), BrandBlue))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            shopName.take(1).uppercase(),
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(shopName, fontSize = 22.sp, fontWeight = FontWeight.Black, color = TextTitle)
                    Surface(
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            "Verified Administrator", 
                            fontSize = 11.sp, 
                            fontWeight = FontWeight.Bold,
                            color = BrandBlue,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Settings Sections
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                
                SectionTitle("CONTROL ACCOUNT")
                SettingsCard {
                    SettingsItem(icon = Icons.Default.AccountCircle, title = "Merchant Details", subtitle = "View shop information") {}
                    HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(start = 56.dp))
                    SettingsItem(icon = Icons.Default.Security, title = "Security Credentials", subtitle = "Update password & keys") {}
                }

                Spacer(Modifier.height(24.dp))

                SectionTitle("PREFERENCES")
                SettingsCard {
                    SettingsSwitchItem(icon = Icons.Default.NotificationsActive, title = "Critical Alerts", initialValue = true)
                    HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(start = 56.dp))
                    SettingsSwitchItem(icon = Icons.Default.PhonelinkLock, title = "Auto-Lock Protocol", initialValue = false)
                }

                Spacer(Modifier.height(24.dp))

                SectionTitle("INFORMATION")
                SettingsCard {
                    SettingsItem(icon = Icons.Default.HeadsetMic, title = "Technical Support", subtitle = "Direct line to admin") {}
                    HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(start = 56.dp))
                    SettingsItem(icon = Icons.Default.Layers, title = "Legal & Privacy") {}
                    HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(start = 56.dp))
                    SettingsItem(icon = Icons.Default.Info, title = "Architecture Build", subtitle = "v1.2.0-stable (104)") {}
                }

                Spacer(Modifier.height(40.dp))

                // Logout Button
                Button(
                    onClick = { showLogoutDialog = true },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("TERMINATE SESSION", color = Color(0xFFDC2626), fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 1.sp)
                }

                Spacer(Modifier.height(60.dp))
            }
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        fontWeight = FontWeight.Black,
        fontSize = 11.sp,
        color = BrandBlue,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 8.dp, bottom = 10.dp)
    )
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            content()
        }
    }
}

@Composable
fun SettingsItem(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = Color(0xFFF1F5F9),
            shape = CircleShape,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = TextTitle, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextTitle)
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(18.dp))
    }
}

@Composable
fun SettingsSwitchItem(icon: ImageVector, title: String, initialValue: Boolean) {
    var checked by remember { mutableStateOf(initialValue) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { checked = !checked }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = Color(0xFFF1F5F9),
            shape = CircleShape,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = TextTitle, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextTitle, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = { checked = it },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = BrandBlue,
                uncheckedTrackColor = Color(0xFFE2E8F0),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}
