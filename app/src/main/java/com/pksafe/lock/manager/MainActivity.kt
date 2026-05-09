package com.pksafe.lock.manager

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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.pksafe.lock.manager.data.ApiService
import com.pksafe.lock.manager.ui.dashboard.DashboardScreen
import com.pksafe.lock.manager.ui.deregister.DeregisteredListScreen
import com.pksafe.lock.manager.ui.devices.ControlPanelScreen
import com.pksafe.lock.manager.ui.devices.DeviceListScreen
import com.pksafe.lock.manager.ui.devices.DeviceListViewModel
import com.pksafe.lock.manager.ui.emi.EmiListScreen
import com.pksafe.lock.manager.ui.login.LoginScreen
import com.pksafe.lock.manager.ui.provisioning.ProvisioningQrScreen
import com.pksafe.lock.manager.ui.provisioning.NfcSetupScreen
import com.pksafe.lock.manager.ui.registration.RegistrationScreen
import com.pksafe.lock.manager.ui.theme.PKLockerTheme
import com.pksafe.lock.manager.ui.theme.PrimaryDark
import com.pksafe.lock.manager.ui.theme.SuccessGreen
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

    val lockManager = remember { com.pksafe.lock.manager.util.LockManager(context) }
    val isAdmin = sharedPrefs.getBoolean("is_admin", false)
    if (isAdmin && isCustomer) {
        sharedPrefs.edit().putBoolean("is_customer", false).putBoolean("is_locked", false).apply()
        isCustomer = false
        isLocked = false
        // System level unlock bhi lazmi hy
        lockManager.unlockDevice()
    }

    // ─── STARTUP SILENT REFRESH ────────────────────────────────────────────────
    // Every time the customer app opens with a valid IMEI, silently fetch fresh
    // EMI/shop data from server so the lock screen always shows real values.
    // This runs in background — it doesn't block the UI at all.
    LaunchedEffect(isCustomer, deviceImei) {
        if (isCustomer && !deviceImei.isNullOrBlank()) {
            fetchAndSaveSmsCodesForCustomer(context, deviceImei!!)
            Log.d("STARTUP_REFRESH", "Silently refreshing EMI data for IMEI: $deviceImei")
        }
    }

    // ─── PERMANENT SECURITY ENFORCEMENT ─────────────────────────────────────
    // For production, we must block Reset as soon as the device is marked as Customer.
    // This happens even if the device is currently "Unlocked".
    LaunchedEffect(isCustomer) {
        if (isCustomer && lockManager.isDeviceOwner()) {
            // Block Factory Reset, USB, and Debugging PERMANENTLY for customers
            // We use a custom function that doesn't rely on the "isLocked" flag
            lockManager.enforcePermanentRestrictions(true)
            Log.d("SECURITY_ENFORCE", "Permanent Customer Restrictions Applied")
        }
    }

    // ─── Overlay & SMS Permission Guards ─────────────────────────────────────
    // Customer device pe har baar check karo — agar permission gayi toh dialog dikhao
    var overlayPermissionMissing by remember {
        mutableStateOf(
            isCustomer && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(context)
        )
    }

    // SMS Permissions
    var smsPermissionMissing by remember {
        mutableStateOf(
            isCustomer && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECEIVE_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED
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
        val lockManager = remember { com.pksafe.lock.manager.util.LockManager(context) }
        
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
                isLocked = isLocked,
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
        var isSigningUp by rememberSaveable { mutableStateOf(false) }
        
        if (isSigningUp) {
            com.pksafe.lock.manager.ui.login.SignupScreen(
                onBackToLogin = { isSigningUp = false }
            )
        } else {
            LoginScreen(
                onLoginSuccess = { 
                    isUserLoggedIn = true 
                    sharedPrefs.edit().putBoolean("is_logged_in", true).apply()
                },
                onNavigateToSignup = { isSigningUp = true }
            )
        }
    } else {
        PKLockerApp(
            isAdmin = true, 
            onLogout = {
                isUserLoggedIn = false
                sharedPrefs.edit()
                    .remove("is_logged_in")
                    .remove("is_admin")
                    .remove("auth_token")
                    .remove("shop_name")
                    .apply()
            }
        )
    }
}


private fun syncTokenToServer(imei: String, token: String) {
    val retrofit = Retrofit.Builder()
        .baseUrl(com.pksafe.lock.manager.util.Constants.BASE_URL)
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
    val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.pksafe.lock.manager.worker.LocationWorker>(
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
        .baseUrl(com.pksafe.lock.manager.util.Constants.BASE_URL)
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
        .baseUrl(com.pksafe.lock.manager.util.Constants.BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    val apiService = retrofit.create(ApiService::class.java)

    CoroutineScope(Dispatchers.IO).launch {
        try {
            // Fetch device detail (no auth needed if it's a public endpoint, or we use empty token)
            val response = apiService.getDeviceStatus("", imei)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    val device = body.data.device
                    val emiSummary = body.data.emiSummary
                    
                    val lockCode   = device.smsCodes?.lockCode
                    val unlockCode = device.smsCodes?.unlockCode
                    
                    val shopName = device.shopkeeper?.shopName ?: device.shopkeeper?.name ?: "Authorized Dealer"
                    val shopPhone = device.shopkeeper?.phone ?: "Contact Provider"
                    
                    val emiAmount = emiSummary.nextEmi?.amount ?: device.emiAmount
                    val emiDate = emiSummary.nextEmi?.dueDate ?: "Contact Provider"
                    
                    Log.d("LOCK_SYNC", "Syncing: Shop=$shopName, Phone=$shopPhone, EMI=$emiAmount")

                    // Format date if possible (assuming ISO string from server)
                    val formattedDate = if (emiDate != "Contact Provider") {
                        try {
                            val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                            inputFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                            val outputFormat = java.text.SimpleDateFormat("dd MMMM", java.util.Locale.US)
                            val date = inputFormat.parse(emiDate)
                            if (date != null) outputFormat.format(date) else emiDate
                        } catch (e: Exception) {
                            Log.e("LOCK_SYNC", "Date format error: ${e.message}")
                            emiDate
                        }
                    } else emiDate

                    context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
                        .edit()
                        .putString("sms_lock_code", lockCode)
                        .putString("sms_unlock_code", unlockCode)
                        .putString("shop_name", shopName)
                        .putString("shop_phone", shopPhone)
                        .putString("emi_amount", "Rs. ${emiAmount?.toInt() ?: 0}")
                        .putString("emi_due_date", formattedDate)
                        .apply()
                        
                    Log.d("LOCK_SYNC", "Device info successfully saved to preferences")
                } else {
                    Log.w("LOCK_SYNC", "Server response unsuccessful or body null")
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
    isLocked: Boolean,
    onImeiSubmit: (String) -> Unit, 
    onManualLock: () -> Unit,
    onReset: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE) }
    var showImeiDialog by remember(imei) { mutableStateOf(imei == "Not Set" || imei.isBlank()) }
    var tempImei by remember { mutableStateOf("") }

    // Helper to get IMEI robustly (for Device Owners)
    fun getRobustImei(ctx: Context): String? {
        return try {
            val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Try multiple slots/methods for reliability
                tm.imei ?: try { tm.getImei(0) } catch(e: Exception) { null } 
                       ?: try { tm.getImei(1) } catch(e: Exception) { null }
                       ?: tm.deviceId
            } else {
                tm.deviceId
            }
        } catch (e: Exception) {
            null
        }
    }

    // Auto-fetch IMEI if device owner — Polling while dialog is visible
    LaunchedEffect(showImeiDialog) {
        if (!showImeiDialog) return@LaunchedEffect
        
        while (showImeiDialog) {
            try {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
                if (dpm.isDeviceOwnerApp(context.packageName)) {
                    // 1. Grant permission automatically as Device Owner
                    val compName = android.content.ComponentName(context, com.pksafe.lock.manager.receiver.AdminReceiver::class.java)
                    try {
                        dpm.setPermissionGrantState(compName, context.packageName, android.Manifest.permission.READ_PHONE_STATE, android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
                    } catch(e: Exception) { }

                    // 2. Try fetching IMEI
                    val fetchedImei = getRobustImei(context)
                    if (!fetchedImei.isNullOrBlank()) {
                        Log.d("AUTO_IMEI", "Successfully auto-fetched IMEI: $fetchedImei")
                        tempImei = fetchedImei
                        onImeiSubmit(fetchedImei)
                        fetchAndSaveSmsCodesForCustomer(context, fetchedImei)
                        showImeiDialog = false
                        break // Success!
                    }
                }
            } catch (e: Exception) {
                Log.e("AUTO_IMEI", "Polling error: ${e.message}")
            }
            kotlinx.coroutines.delay(2000) // Check every 2 seconds
        }
    }

 

    val lockManager = remember { com.pksafe.lock.manager.util.LockManager(context) }

    // Permission States
    var isAdminActive by remember { mutableStateOf(lockManager.isAdminActive()) }
    var isOverlayActive by remember { mutableStateOf(lockManager.canDrawOverlays()) }
    var isDeviceOwner by remember { mutableStateOf(lockManager.isDeviceOwner()) }
    var isAccessibilityActive by remember { mutableStateOf(com.pksafe.lock.manager.service.AntiUninstallService.isServiceRunning(context)) }

    var showAccessibilityGuide by remember { mutableStateOf(false) }

    // Accessibility Guide Dialog
    if (showAccessibilityGuide) {
        AlertDialog(
            onDismissRequest = { showAccessibilityGuide = false },
            containerColor = Color(0xFF1A1A1A),
            title = {
                Text("Accessibility Guard On Karein", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Isko on karne se app delete nahi hogi:", color = Color.Gray, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("1. Settings khulne pe 'Downloaded Apps' ya 'Installed Apps' mein jayein", color = Color(0xFF3B82F6), fontWeight = FontWeight.Medium)
                    Text("2. Wahan 'PKLocker Guard' pe click karein", color = Color(0xFF3B82F6), fontWeight = FontWeight.Medium)
                    Text("3. Switch ON kar ke Allow karein", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAccessibilityGuide = false
                        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        // This extra helps highlight the service on modern Android
                        val componentName = android.content.ComponentName(context, com.pksafe.lock.manager.service.AntiUninstallService::class.java).flattenToString()
                        intent.putExtra(":settings:fragment_args_key", componentName)
                        intent.putExtra(":settings:show_fragment_args", android.os.Bundle().apply { putString(":settings:fragment_args_key", componentName) })
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                ) {
                    Text("Samajh Gaya, Settings Kholein", color = Color.White)
                }
            }
        )
    }

    // Refresh states when returning to app
    LaunchedEffect(Unit) {
        while(true) {
            isAdminActive = lockManager.isAdminActive()
            isOverlayActive = lockManager.canDrawOverlays()
            isDeviceOwner = lockManager.isDeviceOwner()
            isAccessibilityActive = com.pksafe.lock.manager.service.AntiUninstallService.isServiceRunning(context)
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
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(160.dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(Color(0xFF22C55E).copy(alpha = 0.15f), Color.Transparent)
                        ),
                        shape = CircleShape
                    )
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(100.dp)
                        .background(Color(0xFF22C55E).copy(alpha = 0.1f), CircleShape)
                        .border(1.dp, Color(0xFF22C55E).copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.Shield, 
                        null, 
                        modifier = Modifier.size(48.dp), 
                        tint = Color(0xFF22C55E)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "SYSTEM SECURED", 
                fontSize = 28.sp, 
                fontWeight = FontWeight.ExtraBold, 
                color = Color.White,
                letterSpacing = 1.sp
            )
            Text(
                "PKLocker Protection Active", 
                fontSize = 14.sp, 
                color = Color(0xFF22C55E),
                fontWeight = FontWeight.SemiBold
            )


            Spacer(modifier = Modifier.height(40.dp))

            // ─── MANUAL SECURITY SETUP (NO PC / NO QR) ──────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color(0xFF333333)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("MANUAL SETUP CHECKLIST", fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color.Gray, letterSpacing = 2.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    PermissionItem(
                        title = "Accessibility Guard",
                        subtitle = "Blocks Settings & Factory Reset",
                        isActive = isAccessibilityActive,
                        onClick = {
                            if (!isAccessibilityActive) {
                                showAccessibilityGuide = true
                            }
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFF333333))

                    PermissionItem(
                        title = "Device Owner Protocol",
                        subtitle = if (isDeviceOwner) "Enterprise Enrollment Active" else "Standard Mode (Limited)",
                        isActive = isDeviceOwner,
                        onClick = { 
                            if (!isDeviceOwner) {
                                Toast.makeText(context, "Must be enrolled via QR/ADB at Setup", Toast.LENGTH_LONG).show()
                            }
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFF333333))

                    PermissionItem(
                        title = "Display Over Other Apps",
                        subtitle = "Required to show Lock Screen",
                        isActive = isOverlayActive,
                        onClick = { lockManager.requestOverlayPermission() }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFF333333))

                    PermissionItem(
                        title = "Device Admin (Active)",
                        subtitle = "Prevents App Uninstallation",
                        isActive = isAdminActive,
                        onClick = { lockManager.requestAdminPermission() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))


            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "Ensures security enforcement is working correctly",
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )


            Spacer(modifier = Modifier.height(32.dp))
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
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if(isActive) Color.White else Color.Red)
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
    
    // ─── REACTIVE PREFS: auto-update when background fetch writes new values ───
    var shopName by remember { mutableStateOf(sharedPrefs.getString("shop_name", "Authorized Dealer") ?: "Authorized Dealer") }
    var shopPhone by remember { mutableStateOf(sharedPrefs.getString("shop_phone", "Contact Provider") ?: "Contact Provider") }
    var emiDue by remember { mutableStateOf(sharedPrefs.getString("emi_amount", "Rs. 2,500") ?: "Rs. 2,500") }
    var dueDate by remember { mutableStateOf(sharedPrefs.getString("emi_due_date", "20 March") ?: "20 March") }

    // Listen for pref changes (e.g. when fetchAndSaveSmsCodesForCustomer completes)
    DisposableEffect(Unit) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            when (key) {
                "shop_name" -> shopName = prefs.getString("shop_name", "Authorized Dealer") ?: "Authorized Dealer"
                "shop_phone" -> shopPhone = prefs.getString("shop_phone", "Contact Provider") ?: "Contact Provider"
                "emi_amount" -> emiDue = prefs.getString("emi_amount", "Rs. 2,500") ?: "Rs. 2,500"
                "emi_due_date" -> dueDate = prefs.getString("emi_due_date", "20 March") ?: "20 March"
            }
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            shopName.uppercase(), 
                            fontWeight = FontWeight.Black, 
                            fontSize = 20.sp, 
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = "Verified Dealer",
                            tint = Color(0xFF22C55E),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Icon(
                            Icons.Default.SupportAgent, 
                            null, 
                            tint = Color.Gray, 
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "SUPPORT: $shopPhone", 
                            color = Color.Gray, 
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
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

            Spacer(modifier = Modifier.height(32.dp))

            // Contact button removed as per request

            Spacer(modifier = Modifier.height(20.dp))
            
            // Subtle Emergency Release (Debug)
            TextButton(onClick = { 
                com.pksafe.lock.manager.util.LockManager(context).selfDeactivate()
                onReset()
            }) {
                Text("Emergency Local Release", color = Color.White.copy(0.1f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun PKLockerApp(isAdmin: Boolean, viewModel: DeviceListViewModel = viewModel(), onLogout: () -> Unit) {
    val context = LocalContext.current
    val dashboardViewModel: com.pksafe.lock.manager.ui.dashboard.DashboardViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    
    // Initialize dashboard data to fetch available keys/stats for the whole app
    LaunchedEffect(Unit) {
        dashboardViewModel.initDashboard(context)
    }

    val stats = dashboardViewModel.dashboardData

    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var selectedDeviceImei by remember { mutableStateOf<String?>(null) }
    var selectedDeviceName by remember { mutableStateOf<String?>(null) }
    var showBuyKeysPopup by remember { mutableStateOf(false) }

    fun navigateSafe(dest: AppDestinations) {
        if (dest == AppDestinations.REGISTRATION) {
            val available = stats?.android?.availableKeys ?: 0
            if (available <= 0) {
                showBuyKeysPopup = true
                return
            }
        }
        currentDestination = dest
    }

    if (selectedDeviceImei != null && isAdmin) {
        ControlPanelScreen(
            imei = selectedDeviceImei!!,
            customerName = selectedDeviceName ?: "Unknown",
            onBack = { selectedDeviceImei = null }
        )
    } else {
        if (showBuyKeysPopup) {
            AlertDialog(
                onDismissRequest = { showBuyKeysPopup = false },
                containerColor = Color.White,
                icon = { Icon(Icons.Default.Key, contentDescription = null, tint = Color(0xFFEA580C), modifier = Modifier.size(32.dp)) },
                title = { Text("Zero Available Keys", fontWeight = FontWeight.Bold, color = Color(0xFF1E293B)) },
                text = { Text("You do not have any available keys to register a new device. Please buy keys to continue.", color = Color(0xFF64748B)) },
                confirmButton = {
                    Button(
                        onClick = { 
                            showBuyKeysPopup = false
                            navigateSafe(AppDestinations.BUY_KEYS) 
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                    ) {
                        Text("Buy Keys Now", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBuyKeysPopup = false }) {
                        Text("Cancel", color = Color(0xFF64748B))
                    }
                }
            )
        }

        NavigationSuiteScaffold(
            navigationSuiteItems = {
                AppDestinations.entries.filter { 
                    it != AppDestinations.EMI_LIST && 
                    it != AppDestinations.DEREGISTER_LIST &&
                    it != AppDestinations.PROVISIONING_QR &&
                    it != AppDestinations.CABLE_SYNC &&
                    it != AppDestinations.PHONE_QR &&
                    it != AppDestinations.EASY_SETUP &&
                    it != AppDestinations.VIDEO_HELP &&
                    it != AppDestinations.NFC_SETUP &&
                    it != AppDestinations.ADMIN_KEYS
                }.forEach {
                    item(
                        icon = { Icon(imageVector = it.icon, contentDescription = it.label) },
                        label = { Text(it.label) },
                        selected = it == currentDestination,
                        onClick = { navigateSafe(it) }
                    )
                }
            }
        ) {
            Scaffold { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    when (currentDestination) {
                        AppDestinations.HOME -> DashboardScreen(onMenuItemClick = { title ->
                            when(title) {
                                "Upcoming EMIs" -> navigateSafe(AppDestinations.EMI_LIST)
                                "Active Customers" -> navigateSafe(AppDestinations.LIST)
                                "Deregistered" -> navigateSafe(AppDestinations.DEREGISTER_LIST)
                                "QR Code" -> navigateSafe(AppDestinations.PROVISIONING_QR)
                                "Cable Sync" -> navigateSafe(AppDestinations.CABLE_SYNC)
                                "Phone QR" -> navigateSafe(AppDestinations.PHONE_QR)
                                "Easy Setup" -> navigateSafe(AppDestinations.EASY_SETUP)
                                "Video Help" -> navigateSafe(AppDestinations.VIDEO_HELP)
                                "Register Device" -> navigateSafe(AppDestinations.REGISTRATION)
                                "Buy Keys" -> navigateSafe(AppDestinations.BUY_KEYS)
                                "NFC Setup" -> navigateSafe(AppDestinations.NFC_SETUP)
                                "Key Requests" -> navigateSafe(AppDestinations.ADMIN_KEYS)
                            }
                        })
                        AppDestinations.REGISTRATION -> if (isAdmin) RegistrationScreen(
                            onRegistrationSuccess = {
                                viewModel.fetchDevices(context) // Refresh the list
                                currentDestination = AppDestinations.LIST // Navigate to Devices tab
                            }
                        )
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
                        AppDestinations.PROVISIONING_QR -> if (isAdmin) com.pksafe.lock.manager.ui.dashboard.QrSetupScreen(
                            onBack = { currentDestination = AppDestinations.HOME },
                            onProvisioningQr = { currentDestination = AppDestinations.PHONE_QR }
                        )
                        AppDestinations.CABLE_SYNC -> if (isAdmin) com.pksafe.lock.manager.ui.provisioning.ProvisioningCableScreen(
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
                        AppDestinations.EASY_SETUP -> if (isAdmin) com.pksafe.lock.manager.ui.provisioning.EasySetupScreen(
                            onBack = { currentDestination = AppDestinations.HOME }
                        )
                        AppDestinations.BUY_KEYS -> if (isAdmin) com.pksafe.lock.manager.ui.keys.BuyKeysScreen(
                            onBack = { currentDestination = AppDestinations.HOME }
                        )
                        AppDestinations.NFC_SETUP -> if (isAdmin) NfcSetupScreen(onBack = { currentDestination = AppDestinations.HOME })
                        AppDestinations.ADMIN_KEYS -> com.pksafe.lock.manager.ui.keys.AdminKeyOrdersScreen(onBack = { currentDestination = AppDestinations.HOME })
                        AppDestinations.PROFILE -> com.pksafe.lock.manager.ui.profile.ProfileScreen(
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
    CABLE_SYNC("Cable Sync", Icons.Default.Usb),
    EASY_SETUP("Easy Setup", Icons.Default.PhoneAndroid),
    NFC_SETUP("NFC Setup", Icons.Default.TapAndPlay),
    ADMIN_KEYS("Key Requests", Icons.Default.AdminPanelSettings)
}
