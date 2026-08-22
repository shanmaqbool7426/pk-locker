package com.pksafe.lock.manager.ui.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import com.pksafe.lock.manager.ui.theme.*

// ═════════════════════════════════════════════════════════════════════════════
//  PK LOCKER — Profile / Settings Screen (Professional Redesign)
//  All business logic preserved. UI completely refreshed.
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onLogout: () -> Unit) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE) }
    val shopName = sharedPrefs.getString("shop_name", "Shopkeeper") ?: "Shopkeeper"
    val shopPhone = sharedPrefs.getString("shop_phone", "") ?: ""

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    // ─── Logout Dialog ──────────────────────────────────────────────────────
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = { Icon(Icons.Default.Logout, null, tint = Danger, modifier = Modifier.size(28.dp)) },
            title = { Text("Confirm Logout", fontWeight = FontWeight.Bold, color = TextTitle) },
            text = { Text("Are you sure you want to end your session? You will need to sign in again.", color = TextMuted, fontSize = 14.sp, lineHeight = 20.sp) },
            confirmButton = {
                Button(
                    onClick = { showLogoutDialog = false; onLogout() },
                    colors = ButtonDefaults.buttonColors(containerColor = Danger),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("YES, LOGOUT", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = TextMuted, fontWeight = FontWeight.Medium)
                }
            },
            containerColor = CardWhite,
            shape = RoundedCornerShape(24.dp)
        )
    }

    // ─── Privacy Dialog ─────────────────────────────────────────────────────
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            icon = { Icon(Icons.Default.Policy, null, tint = BrandBlue, modifier = Modifier.size(28.dp)) },
            title = { Text("Legal & Privacy", fontWeight = FontWeight.Bold, color = TextTitle) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "1. Data Collection\nWe collect basic device info to provide locking services. We do not sell your personal data.\n\n" +
                        "2. Permissions\nThis app requires Device Admin and Accessibility permissions to function properly. Disabling these may lock the device.\n\n" +
                        "3. Liability\nThe shopkeeper is solely responsible for verifying customer identities. PKLocker is a tool, not a financial entity.\n\n" +
                        "4. Service Usage\nMisuse of the platform for illegal activities will result in immediate termination of the account.",
                        fontSize = 13.sp,
                        color = TextMuted,
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showPrivacyDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("I UNDERSTAND", fontWeight = FontWeight.Bold) }
            },
            containerColor = CardWhite,
            shape = RoundedCornerShape(24.dp)
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold, color = TextTitle, fontSize = 20.sp) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CardWhite)
            )
        },
        containerColor = SoftBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ═══ PROFILE HEADER ════════════════════════════════════════════════
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardWhite)
                    .padding(bottom = 28.dp, top = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Gradient avatar
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(BrandBlue, BrandBlueDark))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            shopName.take(1).uppercase(),
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(shopName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextTitle)
                    if (shopPhone.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(shopPhone, fontSize = 13.sp, color = TextMuted)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = SuccessLight,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Verified, null, tint = Success, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Verified Admin", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Success)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ═══ SETTINGS SECTIONS ═════════════════════════════════════════════
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {

                // ── Account ─────────────────────────────────────────────────
                SettingsSectionLabel("ACCOUNT")
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Default.AccountCircle,
                        iconBg = BrandBlueLight,
                        iconTint = BrandBlue,
                        title = "Merchant Details",
                        subtitle = shopName
                    ) {
                        Toast.makeText(context, "Merchant: $shopName", Toast.LENGTH_SHORT).show()
                    }
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.Security,
                        iconBg = WarningLight,
                        iconTint = Warning,
                        title = "Security Credentials",
                        subtitle = "Update password & keys"
                    ) {
                        Toast.makeText(context, "Credential management coming soon", Toast.LENGTH_SHORT).show()
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Preferences ─────────────────────────────────────────────
                SettingsSectionLabel("PREFERENCES")
                SettingsCard {
                    SettingsSwitchRow(
                        icon = Icons.Default.NotificationsActive,
                        iconBg = DangerLight,
                        iconTint = Danger,
                        title = "Critical Alerts",
                        subtitle = "Lock & security notifications",
                        sharedPrefs = sharedPrefs,
                        prefKey = "pref_critical_alerts",
                        defaultValue = true
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        icon = Icons.Default.PhonelinkLock,
                        iconBg = BrandBlueLight,
                        iconTint = BrandBlue,
                        title = "Auto-Lock Protocol",
                        subtitle = "Lock on SIM change automatically",
                        sharedPrefs = sharedPrefs,
                        prefKey = "pref_auto_lock",
                        defaultValue = false
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Information ─────────────────────────────────────────────
                SettingsSectionLabel("INFORMATION")
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Default.HeadsetMic,
                        iconBg = SuccessLight,
                        iconTint = Success,
                        title = "Technical Support",
                        subtitle = "Direct line to admin"
                    ) {
                        val intent = Intent(Intent.ACTION_DIAL).apply {
                            data = Uri.parse("tel:+923001234567")
                        }
                        context.startActivity(intent)
                    }
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.Layers,
                        iconBg = InfoLight,
                        iconTint = Info,
                        title = "Legal & Privacy",
                        subtitle = "Terms of service"
                    ) {
                        showPrivacyDialog = true
                    }
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.Info,
                        iconBg = SurfaceGray,
                        iconTint = TextMuted,
                        title = "App Version",
                        subtitle = "v1.2.0-stable (104)"
                    ) {
                        Toast.makeText(context, "You are on the latest version", Toast.LENGTH_SHORT).show()
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))

                // ── Logout ──────────────────────────────────────────────────
                Surface(
                    onClick = { showLogoutDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = DangerSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Danger.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Logout, null, tint = Danger, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("LOGOUT", color = Danger, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.5.sp)
                    }
                }

                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  SETTINGS COMPONENTS
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun SettingsSectionLabel(title: String) {
    Text(
        text = title,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        color = TextSubtle,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp, top = 4.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column { content() }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = iconBg,
            shape = CircleShape,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextTitle)
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(Icons.Default.ChevronRight, null, tint = TextSubtle, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String? = null,
    sharedPrefs: android.content.SharedPreferences,
    prefKey: String,
    defaultValue: Boolean
) {
    var checked by remember { mutableStateOf(sharedPrefs.getBoolean(prefKey, defaultValue)) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                checked = !checked
                sharedPrefs.edit().putBoolean(prefKey, checked).apply()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = iconBg,
            shape = CircleShape,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextTitle)
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = {
                checked = it
                sharedPrefs.edit().putBoolean(prefKey, it).apply()
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = BrandBlue,
                uncheckedTrackColor = BorderLight,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = BorderSoft, modifier = Modifier.padding(start = 54.dp))
}
