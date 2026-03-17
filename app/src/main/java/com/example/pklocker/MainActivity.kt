package com.example.pklocker

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pklocker.data.ApiService
import com.example.pklocker.ui.dashboard.DashboardScreen
import com.example.pklocker.ui.deregister.DeregisteredListScreen
import com.example.pklocker.ui.devices.ControlPanelScreen
import com.example.pklocker.ui.devices.DeviceListScreen
import com.example.pklocker.ui.devices.DeviceListViewModel
import com.example.pklocker.ui.emi.EmiListScreen
import com.example.pklocker.ui.login.LoginScreen
import com.example.pklocker.ui.provisioning.ProvisioningQrScreen
import com.example.pklocker.ui.registration.RegistrationScreen
import com.example.pklocker.ui.theme.PKLockerTheme
import com.example.pklocker.ui.theme.PrimaryDark
import com.example.pklocker.ui.theme.SuccessGreen
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        enableEdgeToEdge()
        setContent {
            PKLockerTheme {
                MainAppEntryPoint()
            }
        }
    }

    // ─── Back button completely block karo jab device locked ho ──────────────
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        val prefs = getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val isLocked = prefs.getBoolean("is_locked", false)
        val isCustomer = prefs.getBoolean("is_customer", false)
        // Agar customer locked hai toh back mat karne do
        if (isCustomer && isLocked) return
        super.onBackPressed()
    }
}

@Composable
fun MainAppEntryPoint() {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE) }

    var isCustomer by remember { mutableStateOf(sharedPrefs.getBoolean("is_customer", false)) }
    var isLocked by remember { mutableStateOf(sharedPrefs.getBoolean("is_locked", false)) }
    var isUserLoggedIn by rememberSaveable { mutableStateOf(sharedPrefs.getBoolean("is_logged_in", false)) }
    var fcmToken by remember { mutableStateOf(sharedPrefs.getString("fcm_token", "Fetching...")) }
    var deviceImei by remember { mutableStateOf(sharedPrefs.getString("device_imei", "")) }

    // ─── Overlay Permission Guard ─────────────────────────────────────────────
    // Customer device pe har baar check karo — agar permission gayi toh dialog dikhao
    var overlayPermissionMissing by remember {
        mutableStateOf(
            isCustomer && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(context)
        )
    }

    // Jab bhi app foreground mein aaye, dobara check karo
    LaunchedEffect(isCustomer) {
        if (isCustomer && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            overlayPermissionMissing = !Settings.canDrawOverlays(context)
        }
    }

    // ─── Blocking Dialog — Permission Gayi Toh Ruko ──────────────────────────
    if (overlayPermissionMissing && isCustomer) {
        AlertDialog(
            onDismissRequest = { /* dismiss nahi hoga — mandatory hai */ },
            icon = { Icon(Icons.Default.SecurityUpdate, null, tint = Color.Red, modifier = Modifier.size(40.dp)) },
            title = { Text("Security Permission Required", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "This device requires overlay permission to enforce security.",
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Steps:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text("1. Tap GRANT below", fontSize = 13.sp)
                    Text("2. Find 'PK Locker' in the list", fontSize = 13.sp)
                    Text("3. Turn ON 'Allow display over other apps'", fontSize = 13.sp)
                    Text("4. Come back to this app", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Also go to: Settings > Apps > PKLocker > Turn OFF 'Manage app if unused'",
                        fontSize = 12.sp,
                        color = Color.Red,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Seedha Overlay Permission settings pe bhejo
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("GRANT PERMISSION", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                // Re-check button — user wapis aaye toh check karo
                OutlinedButton(onClick = {
                    overlayPermissionMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                        !Settings.canDrawOverlays(context)
                }) {
                    Text("I've done it — Check Again")
                }
            }
        )
        return  // Dialog dismiss nahi hua toh aage mat jao
    }

    // Professional FCM Token Sync with Server
    LaunchedEffect(isCustomer, deviceImei) {
        if (isCustomer) {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    fcmToken = token
                    sharedPrefs.edit().putString("fcm_token", token).apply()
                    
                    if (!deviceImei.isNullOrBlank()) {
                        syncTokenToServer(deviceImei!!, token)
                    }
                }
            }
        }
    }

    val listener = remember {
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            when (key) {
                "is_locked" -> isLocked = prefs.getBoolean("is_locked", false)
                "is_customer" -> isCustomer = prefs.getBoolean("is_customer", false)
                "device_imei" -> deviceImei = prefs.getString("device_imei", "")
            }
        }
    }

    DisposableEffect(Unit) {
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    if (isCustomer) {
        if (isLocked) {
            CustomerLockScreen(onReset = {
                sharedPrefs.edit().clear().apply()
                isCustomer = false
                isUserLoggedIn = false
                isLocked = false
            })
        } else {
            CustomerStatusScreen(
                token = fcmToken ?: "No Token",
                imei = deviceImei ?: "Not Set",
                onImeiSubmit = { newImei ->
                    sharedPrefs.edit().putString("device_imei", newImei).apply()
                    deviceImei = newImei
                },
                onManualLock = {
                    sharedPrefs.edit().putBoolean("is_locked", true).apply()
                },
                onReset = {
                    sharedPrefs.edit().clear().apply()
                    isCustomer = false
                    isUserLoggedIn = false
                    isLocked = false
                }
            )
        }
    } else if (!isUserLoggedIn) {
        LoginScreen(onLoginSuccess = { 
            isUserLoggedIn = true 
            sharedPrefs.edit().putBoolean("is_logged_in", true).apply()
        })
    } else {
        PKLockerApp(isAdmin = true)
    }
}

private fun syncTokenToServer(imei: String, token: String) {
    val retrofit = Retrofit.Builder()
        .baseUrl(com.example.pklocker.util.Constants.BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    val apiService = retrofit.create(ApiService::class.java)
    
    CoroutineScope(Dispatchers.IO).launch {
        try {
            apiService.updateFcmToken("", mapOf("imei" to imei, "fcmToken" to token))
            Log.d("SYNC_TOKEN", "Token synced for IMEI: $imei")
        } catch (e: Exception) {
            Log.e("SYNC_TOKEN", "Failed to sync: ${e.message}")
        }
    }
}

@Composable
fun CustomerStatusScreen(
    token: String, 
    imei: String, 
    onImeiSubmit: (String) -> Unit, 
    onManualLock: () -> Unit,
    onReset: () -> Unit
) {
    var showImeiDialog by remember { mutableStateOf(imei == "Not Set" || imei.isBlank()) }
    var tempImei by remember { mutableStateOf("") }

    if (showImeiDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Device Activation") },
            text = {
                Column {
                    Text("Enter Registered IMEI to sync with server:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tempImei,
                        onValueChange = { tempImei = it },
                        label = { Text("IMEI Number") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = { 
                    if (tempImei.isNotBlank()) {
                        onImeiSubmit(tempImei)
                        showImeiDialog = false
                    }
                }) { Text("ACTIVATE") }
            }
        )
    }

    val context = LocalContext.current
    val lockManager = remember { com.example.pklocker.util.LockManager(context) }

    // Permission States
    var isAdminActive by remember { mutableStateOf(lockManager.isAdminActive()) }
    var isOverlayActive by remember { mutableStateOf(lockManager.canDrawOverlays()) }

    // Accessibility check
    val isAccessibilityActive = remember {
        val service = context.packageName + "/" + com.example.pklocker.service.AntiUninstallService::class.java.canonicalName
        val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        enabledServices?.contains(service) == true
    }

    // Refresh states when returning to app
    LaunchedEffect(Unit) {
        while(true) {
            isAdminActive = lockManager.isAdminActive()
            isOverlayActive = lockManager.canDrawOverlays()
            kotlinx.coroutines.delay(2000)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF5F5F5)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            Surface(shape = CircleShape, color = SuccessGreen.copy(alpha = 0.1f), modifier = Modifier.size(100.dp)) {
                Icon(Icons.Default.Shield, null, modifier = Modifier.padding(24.dp).size(50.dp), tint = SuccessGreen)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("DEVICE PROTECTED", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = PrimaryDark)
            Text("Security protocol active", fontSize = 14.sp, color = Color.Gray)

            Spacer(modifier = Modifier.height(32.dp))

            // ─── PERMISSION CHECKLIST ───────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("REQUIRED PERMISSIONS", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PrimaryDark)
                    Spacer(modifier = Modifier.height(12.dp))

                    PermissionItem(
                        title = "Display over other apps",
                        isActive = isOverlayActive,
                        onClick = { lockManager.requestOverlayPermission() }
                    )
                    PermissionItem(
                        title = "Device Admin",
                        isActive = isAdminActive,
                        onClick = { lockManager.requestAdminPermission() }
                    )
                    PermissionItem(
                        title = "Anti-Uninstall (Accessibility)",
                        isActive = isAccessibilityActive,
                        onClick = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("DEVICE INFO", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PrimaryDark)
                    Text("IMEI: $imei", fontSize = 11.sp, color = Color.Gray)
                    Text("FCM ACTIVE: ${if (token.length > 10) "YES" else "CONNECTING..."}", fontSize = 11.sp, color = if(token.length > 10) SuccessGreen else Color.Red)
                    Text("DEVICE OWNER: ${if (lockManager.isDeviceOwner()) "ACTIVE (UNSTOPPABLE)" else "NOT SET"}", 
                        fontSize = 11.sp, 
                        fontWeight = FontWeight.Bold,
                        color = if(lockManager.isDeviceOwner()) SuccessGreen else Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onManualLock,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("TEST LOCK NOW", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(40.dp))

            TextButton(onClick = onReset) {
                Text("RESET CUSTOMER MODE", color = Color.Red.copy(alpha = 0.5f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun PermissionItem(title: String, isActive: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (isActive) SuccessGreen else Color.Red,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, fontSize = 14.sp, color = if(isActive) Color.Black else Color.Red)
        }
        if (!isActive) {
            TextButton(onClick = onClick) {
                Text("GRANT", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun CustomerLockScreen(onReset: () -> Unit) {
    // ─── Back button completely disable karo lock screen par ─────────────────
    androidx.activity.compose.BackHandler(enabled = true) {
        // Kuch mat karo — back button ka koi asar nahi
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFB71C1C)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.GppBad, null, modifier = Modifier.size(100.dp), tint = Color.White)
            Spacer(modifier = Modifier.height(24.dp))
            Text("DEVICE LOCKED", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Security Protocol Active",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "This device is locked due to payment overdue or security violation. Please contact your provider.",
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp
            )

            // Debug reset — production mein hataana hai
            Spacer(modifier = Modifier.height(60.dp))
            TextButton(onClick = onReset) {
                Text("DEBUG RESET", color = Color.White.copy(alpha = 0.15f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun PKLockerApp(isAdmin: Boolean, viewModel: DeviceListViewModel = viewModel()) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var selectedDeviceImei by remember { mutableStateOf<String?>(null) }
    var selectedDeviceName by remember { mutableStateOf<String?>(null) }

    if (selectedDeviceImei != null && isAdmin) {
        ControlPanelScreen(
            imei = selectedDeviceImei!!,
            customerName = selectedDeviceName ?: "Unknown",
            onBack = { selectedDeviceImei = null }
        )
    } else {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                AppDestinations.entries.filter { 
                    it != AppDestinations.EMI_LIST && 
                    it != AppDestinations.DEREGISTER_LIST &&
                    it != AppDestinations.PROVISIONING_QR &&
                    it != AppDestinations.PHONE_QR &&
                    it != AppDestinations.VIDEO_HELP
                }.forEach {
                    item(
                        icon = { Icon(imageVector = it.icon, contentDescription = it.label) },
                        label = { Text(it.label) },
                        selected = it == currentDestination,
                        onClick = { currentDestination = it }
                    )
                }
            }
        ) {
            Scaffold { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    when (currentDestination) {
                        AppDestinations.HOME -> DashboardScreen(onMenuItemClick = { title ->
                            when(title) {
                                "Upcoming EMIs" -> currentDestination = AppDestinations.EMI_LIST
                                "Active Customers" -> currentDestination = AppDestinations.LIST
                                "Deregistered" -> currentDestination = AppDestinations.DEREGISTER_LIST
                                "QR Code" -> currentDestination = AppDestinations.PROVISIONING_QR
                                "Phone QR" -> currentDestination = AppDestinations.PHONE_QR
                                "Video Help" -> currentDestination = AppDestinations.VIDEO_HELP
                                "Register Device" -> currentDestination = AppDestinations.REGISTRATION
                            }
                        })
                        AppDestinations.REGISTRATION -> if (isAdmin) RegistrationScreen()
                        AppDestinations.LIST -> if (isAdmin) DeviceListScreen(
                            onDeviceClick = { imei, name ->
                                selectedDeviceImei = imei
                                selectedDeviceName = name
                            }
                        )
                        AppDestinations.EMI_LIST -> if (isAdmin) EmiListScreen(
                            onBack = { currentDestination = AppDestinations.HOME },
                            devices = viewModel.devices
                        )
                        AppDestinations.DEREGISTER_LIST -> if (isAdmin) DeregisteredListScreen(
                            onBack = { currentDestination = AppDestinations.HOME }
                        )
                        AppDestinations.PROVISIONING_QR -> if (isAdmin) ProvisioningQrScreen(
                            title = "Provisioning QR",
                            onBack = { currentDestination = AppDestinations.HOME }
                        )
                        AppDestinations.PHONE_QR -> if (isAdmin) ProvisioningQrScreen(
                            title = "Running Phone QR",
                            onBack = { currentDestination = AppDestinations.HOME }
                        )
                        AppDestinations.VIDEO_HELP -> if (isAdmin) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Video Tutorials Coming Soon")
                        }
                        AppDestinations.PROFILE -> ProfilePlaceholder()
                    }
                }
            }
        }
    }
}

enum class AppDestinations(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    REGISTRATION("Register", Icons.Default.AppRegistration),
    LIST("Devices", Icons.Default.List),
    EMI_LIST("EMIs", Icons.Default.CalendarMonth),
    DEREGISTER_LIST("Deregistered", Icons.Default.PersonRemove),
    PROVISIONING_QR("QR Code", Icons.Default.QrCode),
    PHONE_QR("Phone QR", Icons.Default.StayCurrentPortrait),
    VIDEO_HELP("Help", Icons.Default.PlayCircle),
    PROFILE("Profile", Icons.Default.Person),
}

@Composable
fun ProfilePlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Profile & Settings")
    }
}
