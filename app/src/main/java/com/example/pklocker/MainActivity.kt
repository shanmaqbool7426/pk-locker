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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
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
        handleIntent(intent)

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.data?.let { uri ->
            if (uri.scheme == "pklocker" && uri.host == "payment-result") {
                val status = uri.getQueryParameter("status")
                val orderId = uri.getQueryParameter("orderId")
                
                val prefs = getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("last_payment_status", status)
                    .putString("last_payment_order_id", orderId)
                    .apply()
                
                Log.d("PAYMENT_LINK", "Deep link received: status=$status, orderId=$orderId")
            }
        }
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

    // isCustomer is now set EXPLICITLY via the LoginScreen "Setup" flow.
    // Removed LaunchedEffect(deviceImei) that used to auto-set isCustomer = true.

    // ─── Overlay & SMS Permission Guards ─────────────────────────────────────
    // Customer device pe har baar check karo — agar permission gayi toh dialog dikhao
    var overlayPermissionMissing by remember {
        mutableStateOf(
            isCustomer && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(context)
        )
    }

    var smsPermissionMissing by remember {
        mutableStateOf(
            isCustomer && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECEIVE_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        smsPermissionMissing = !granted
    }

    var locationPermissionMissing by remember {
        mutableStateOf(
            isCustomer && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { map ->
        val fineGranted = map[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        locationPermissionMissing = !fineGranted
    }

    // Jab bhi app foreground mein aaye, dobara check karo
    LaunchedEffect(isCustomer) {
        if (isCustomer && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            overlayPermissionMissing = !Settings.canDrawOverlays(context)
            smsPermissionMissing = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECEIVE_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            locationPermissionMissing = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    // ─── Dialogs for Missing Permissions ─────────────────────────────────────
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

    if (smsPermissionMissing && isCustomer) {
        AlertDialog(
            onDismissRequest = { /* mandatory */ },
            icon = { Icon(Icons.Default.Email, null, tint = Color(0xFF1976D2), modifier = Modifier.size(40.dp)) },
            title = { Text("Offline SMS Security", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "PKLocker requires SMS permission to securely manage this device offline when there is no internet connection.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { smsPermissionLauncher.launch(android.Manifest.permission.RECEIVE_SMS) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                ) {
                    Text("GRANT PERMISSION", fontWeight = FontWeight.Bold)
                }
            }
        )
        return
    }

    if (locationPermissionMissing && isCustomer) {
        AlertDialog(
            onDismissRequest = { },
            icon = { Icon(Icons.Default.LocationOn, null, tint = Color(0xFF388E3C), modifier = Modifier.size(40.dp)) },
            title = { Text("Location Sync Required", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Background location is required for periodic security status updates and EMI compliance verification.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { 
                        locationPermissionLauncher.launch(arrayOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        )) 
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C))
                ) {
                    Text("ALLOW LOCATION", fontWeight = FontWeight.Bold)
                }
            }
        )
        return
    }

    // ─── BACKGROUND SYNC TRIGGER ──────────────────────────────────────────
    LaunchedEffect(isCustomer, deviceImei) {
        if (isCustomer && !deviceImei.isNullOrBlank() && !locationPermissionMissing) {
            scheduleLocationSync(context)
        }
    }

    // Professional FCM Token Sync with Server
    LaunchedEffect(isCustomer, deviceImei, isUserLoggedIn) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                fcmToken = token
                sharedPrefs.edit().putString("fcm_token", token).apply()
                
                if (isCustomer && !deviceImei.isNullOrBlank()) {
                    syncTokenToServer(deviceImei!!, token)
                } else if (!isCustomer && isUserLoggedIn) {
                    // Update Shopkeeper's token
                    val authToken = sharedPrefs.getString("auth_token", null)
                    if (authToken != null) {
                        syncShopkeeperTokenToServer(authToken, token)
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
        val lockManager = remember { com.example.pklocker.util.LockManager(context) }
        
        // ── ACTUAL LOCK/UNLOCK TRIGGER ─────────────────────────────────────
        LaunchedEffect(isLocked) {
            if (isLocked) {
                lockManager.lockDevice()
            } else {
                lockManager.unlockDevice()
                // Move app to background so user sees Home Screen on Unlock
                try {
                    val intent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (e: Exception) { }
            }
        }

        val doFullReset: () -> Unit = {
            // Remove Device Admin/Owner FIRST so app can be uninstalled
            lockManager.selfDeactivate()
            sharedPrefs.edit().clear().apply()
            isCustomer = false
            isUserLoggedIn = false
            isLocked = false
        }
        if (isLocked) {
            CustomerLockScreen(onReset = doFullReset)
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
                onReset = doFullReset
            )
        }
    } else if (!isUserLoggedIn) {
        LoginScreen(onLoginSuccess = { 
            isUserLoggedIn = true 
            sharedPrefs.edit().putBoolean("is_logged_in", true).apply()
        })
    } else {
        PKLockerApp(
            isAdmin = true, 
            onLogout = {
                isUserLoggedIn = false
                sharedPrefs.edit().remove("is_logged_in").remove("auth_token").remove("shop_name").apply()
            }
        )
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

private fun scheduleLocationSync(context: Context) {
    val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.pklocker.worker.LocationWorker>(
        30, java.util.concurrent.TimeUnit.MINUTES
    ).build()
    
    androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "LocationSync",
        androidx.work.ExistingPeriodicWorkPolicy.KEEP,
        workRequest
    )
    Log.d("LOCATION_SYNC", "Location sync scheduled for every 30 mins")
}

private fun syncShopkeeperTokenToServer(authToken: String, token: String) {
    val retrofit = Retrofit.Builder()
        .baseUrl(com.example.pklocker.util.Constants.BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    val apiService = retrofit.create(ApiService::class.java)

    CoroutineScope(Dispatchers.IO).launch {
        try {
            apiService.updateShopkeeperFcmToken("Bearer $authToken", mapOf("fcmToken" to token))
            Log.d("SYNC_TOKEN", "Shopkeeper token synced")
        } catch (e: Exception) {
            Log.e("SYNC_TOKEN", "Failed to sync shopkeeper token: ${e.message}")
        }
    }
}

/**
 * Fetch SMS lock/unlock codes from server and save to SharedPrefs.
 * Called after customer enters their IMEI.
 * SmsReceiver uses these codes to lock/unlock offline.
 */
private fun fetchAndSaveSmsCodesForCustomer(context: Context, imei: String) {
    val retrofit = Retrofit.Builder()
        .baseUrl(com.example.pklocker.util.Constants.BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    val apiService = retrofit.create(ApiService::class.java)

    CoroutineScope(Dispatchers.IO).launch {
        try {
            // Fetch device detail (no auth needed if it's a public endpoint, or we use empty token)
            val response = apiService.getDeviceStatus("", imei)
            if (response.isSuccessful) {
                val body = response.body()
                val lockCode   = body?.data?.device?.smsCodes?.lockCode
                val unlockCode = body?.data?.device?.smsCodes?.unlockCode
                if (!lockCode.isNullOrBlank() && !unlockCode.isNullOrBlank()) {
                    context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
                        .edit()
                        .putString("sms_lock_code", lockCode)
                        .putString("sms_unlock_code", unlockCode)
                        .apply()
                    Log.d("SMS_CODES", "SMS codes saved for IMEI: $imei")
                } else {
                    // Fallback: codes are deterministic from IMEI, SmsReceiver will generate them
                    Log.w("SMS_CODES", "Server returned empty codes — SmsReceiver will generate from IMEI")
                }
            } else {
                Log.w("SMS_CODES", "Could not fetch device info: ${response.code()} — SmsReceiver will generate codes from IMEI")
            }
        } catch (e: Exception) {
            // Network error — SmsReceiver will fall back to generating codes from IMEI
            Log.w("SMS_CODES", "Network error fetching SMS codes: ${e.message} — SmsReceiver will use IMEI-based generation")
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
    val context = LocalContext.current
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
                        // Save IMEI and fetch SMS codes from server (for offline SMS locking)
                        fetchAndSaveSmsCodesForCustomer(context, tempImei)
                        showImeiDialog = false
                    }
                }) { Text("ACTIVATE") }
            }
        )
    }

    val lockManager = remember { com.example.pklocker.util.LockManager(context) }

    // Permission States
    var isAdminActive by remember { mutableStateOf(lockManager.isAdminActive()) }
    var isOverlayActive by remember { mutableStateOf(lockManager.canDrawOverlays()) }
    var isDeviceOwner by remember { mutableStateOf(lockManager.isDeviceOwner()) }

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
            isDeviceOwner = lockManager.isDeviceOwner()
            kotlinx.coroutines.delay(2000)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0A0A0A)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            
            // --- Premium Header ---
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                Surface(
                    shape = CircleShape, 
                    color = Color(0xFF22C55E).copy(alpha = 0.05f), 
                    border = BorderStroke(1.dp, Color(0xFF22C55E).copy(0.2f)),
                    modifier = Modifier.fillMaxSize()
                ) {}
                Icon(
                    Icons.Default.Shield, 
                    null, 
                    modifier = Modifier.size(50.dp), 
                    tint = Color(0xFF22C55E)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Text("DEVICE PROTECTED", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text("Security protocol actively monitoring", fontSize = 13.sp, color = Color.Gray)

            Spacer(modifier = Modifier.height(40.dp))

            // ─── PERMISSION CHECKLIST ───────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color(0xFF333333)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("CRITICAL PERMISSIONS", fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color.Gray, letterSpacing = 2.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    PermissionItem(
                        title = "Device Owner Status",
                        subtitle = "Required for Hardware Controls",
                        isActive = isDeviceOwner,
                        onClick = { 
                            android.widget.Toast.makeText(context, "Command: adb shell dpm set-device-owner...", android.widget.Toast.LENGTH_LONG).show()
                        }
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFF333333))

                    PermissionItem(
                        title = "Display Over Other Apps",
                        isActive = isOverlayActive,
                        onClick = { lockManager.requestOverlayPermission() }
                    )
                    PermissionItem(
                        title = "Device Admin (Active)",
                        isActive = isAdminActive,
                        onClick = { lockManager.requestAdminPermission() }
                    )
                    PermissionItem(
                        title = "Anti-Uninstall Guard",
                        isActive = isAccessibilityActive,
                        onClick = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // --- Device Meta Card ---
            Surface(
                color = Color(0xFF1A1A1A).copy(0.5f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF333333).copy(0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("TERMINAL IMEI", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(imei, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    Box(
                        modifier = Modifier
                            .background(if(token.length > 10) Color(0xFF22C55E).copy(0.1f) else Color.Red.copy(0.1f), CircleShape)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            if (token.length > 10) "FCM SYNCED" else "OFFLINE",
                            color = if(token.length > 10) Color(0xFF22C55E) else Color.Red,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- Test Button ---
            Button(
                onClick = onManualLock,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().height(60.dp)
            ) {
                Icon(Icons.Default.Lock, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("TEST LOCK PROTOCOL", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color.Black)
            }

            Spacer(modifier = Modifier.height(48.dp))

            TextButton(onClick = onReset) {
                Text("RESET SYSTEM", color = Color.White.copy(alpha = 0.2f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PermissionItem(title: String, subtitle: String? = null, isActive: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (isActive) SuccessGreen else Color.Red,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if(isActive) Color.Black else Color.Red)
                if (subtitle != null) {
                    Text(subtitle, fontSize = 11.sp, color = Color.Gray)
                }
            }
        }
        if (!isActive) {
            TextButton(
                onClick = onClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("ACTIVATE", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Icon(Icons.Default.Check, null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
        }
    }
}
@Composable
fun CustomerLockScreen(onReset: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = true) {}

    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE) }
    val shopName = sharedPrefs.getString("shop_name", "Authorized Dealer") ?: "Authorized Dealer"
    val shopPhone = sharedPrefs.getString("shop_phone", "Contact Provider") ?: "Contact Provider"
    
    // Fallback data for reference UI (should be dynamic in production)
    val emiDue = "Rs. 2,500"
    val dueDate = "20 March"

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0A0A0A)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // --- Glowing Warning Header ---
            Surface(
                color = Color(0xFFDC2626).copy(0.1f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFDC2626)),
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                Text(
                    text = "! WARNING !",
                    color = Color(0xFFDC2626),
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    letterSpacing = 4.sp,
                    modifier = Modifier.padding(horizontal = 40.dp, vertical = 8.dp)
                )
            }

            Text(
                text = "DEVICE LOCKED",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 1.sp
            )
            Text(
                text = "For security reasons, this terminal has been restricted.",
                fontSize = 13.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 40.dp)
            )

            // --- Security Icon ---
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                Surface(
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFDC2626).copy(0.3f)),
                    modifier = Modifier.fillMaxSize()
                ) {}
                Icon(
                    Icons.Default.GppBad, 
                    null, 
                    modifier = Modifier.size(60.dp), 
                    tint = Color(0xFFDC2626)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // --- Payment & Shop Info Card ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333))
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(shopName.uppercase(), fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color.White)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(Icons.Default.Call, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(shopPhone, color = Color.Gray, fontSize = 14.sp)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp), color = Color(0xFF333333))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("EMI PENDING", color = Color(0xFFDC2626), fontWeight = FontWeight.Black, fontSize = 10.sp)
                            Text(emiDue, color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("DUE DATE", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            Text(dueDate, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Surface(color = Color.White.copy(0.05f), shape = RoundedCornerShape(12.dp)) {
                        Text(
                            "\"EMI pay karein aur phone unlock karwain\"", 
                            color = Color.White.copy(0.7f), 
                            fontSize = 13.sp, 
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- Contact Button ---
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$shopPhone"))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.SupportAgent, null, tint = Color.White)
                Spacer(Modifier.width(12.dp))
                Text("CONTACT SUPPORT", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color.White)
            }

            Spacer(modifier = Modifier.height(20.dp))
            
            // Subtle Emergency Release (Debug)
            TextButton(onClick = { 
                com.example.pklocker.util.LockManager(context).selfDeactivate()
                onReset()
            }) {
                Text("Emergency Local Release", color = Color.White.copy(0.1f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun PKLockerApp(isAdmin: Boolean, viewModel: DeviceListViewModel = viewModel(), onLogout: () -> Unit) {
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
                                "Buy Keys" -> currentDestination = AppDestinations.BUY_KEYS
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
                            isForInstallation = true,
                            onBack = { currentDestination = AppDestinations.HOME }
                        )
                        AppDestinations.VIDEO_HELP -> if (isAdmin) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Video Tutorials Coming Soon")
                        }
                        AppDestinations.BUY_KEYS -> if (isAdmin) com.example.pklocker.ui.keys.BuyKeysScreen(
                            onBack = { currentDestination = AppDestinations.HOME }
                        )
                        AppDestinations.PROFILE -> com.example.pklocker.ui.profile.ProfileScreen(
                            onLogout = onLogout
                        )
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
    BUY_KEYS("Buy Keys", Icons.Default.Key),
    EMI_LIST("EMIs", Icons.Default.CalendarMonth),
    DEREGISTER_LIST("Deregistered", Icons.Default.PersonRemove),
    PROVISIONING_QR("QR Code", Icons.Default.QrCode),
    PHONE_QR("Phone QR", Icons.Default.StayCurrentPortrait),
    VIDEO_HELP("Help", Icons.Default.PlayCircle),
    PROFILE("Profile", Icons.Default.Person),
}
