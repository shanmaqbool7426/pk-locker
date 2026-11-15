package com.pksafe.lock.manager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.BatteryManager
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.net.Uri
import android.widget.ImageView
import com.pksafe.lock.manager.R
import android.content.BroadcastReceiver
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Network
import android.net.NetworkRequest
import android.util.Log
import android.content.IntentFilter
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.Toast
import android.net.wifi.WifiManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiNetworkSpecifier
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.app.AlertDialog
import androidx.core.app.NotificationCompat
import com.pksafe.lock.manager.data.ApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class LockService : Service() {

    private lateinit var windowManager: WindowManager
    private var lockView: View? = null
    private val CHANNEL_ID = "LockServiceChannel"
    private val statusHandler = Handler(Looper.getMainLooper())
    private var statusRunnable: Runnable? = null
    private var wifiDialog: AlertDialog? = null  // Hold reference to prevent GC
    private var passwordDialog: AlertDialog? = null  // Hold reference to prevent GC

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Don't let the system resurrect this service when the device isn't locked
        if (!getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE).getBoolean("is_locked", false)) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        
        // ─── ADMIN PROTECTION ──────────────────────────────────────────────────
        // Ensure this service NEVER runs for a shopkeeper/admin
        val prefs = getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("is_admin", false)) {
            Log.w("LOCK_SERVICE", "Service started on ADMIN device — stopping immediately")
            stopSelf()
            return
        }

        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, createNotification())
        }

        // ─── NEVER SHOW THE OVERLAY UNLESS THE DEVICE IS ACTUALLY LOCKED ───────
        // BootReceiver and START_STICKY restarts launch this service even when the
        // shopkeeper never locked the device. Without this guard the overlay appeared
        // by itself (e.g. after a reboot), while the server/shopkeeper still showed
        // "Unlocked" — and nothing ever cleared it because local == server state.
        // (startForeground above is required first: the caller used startForegroundService.)
        if (!prefs.getBoolean("is_locked", false)) {
            Log.i("LOCK_SERVICE", "Started while NOT locked — stopping without overlay")
            stopForeground(true)
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        // Charging indicator: ACTION_BATTERY_CHANGED is sticky, so this also
        // paints the current battery level immediately. Guarded: onStartCommand
        // can run again on the SAME instance (re-lock while already locked) and
        // re-registering a receiver throws IllegalArgumentException → crash.
        registerAutoLockReceiver()
        try {
            registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (e: Exception) {
            Log.w("LOCK_SERVICE", "batteryReceiver already registered", e)
        }

        // ─── GENERATE EMERGENCY CODE ON LOCK ─────────────────────────────
        // When device locks, generate a new one-time emergency code
        val existingEmergencyCode = prefs.getString("emergency_unlock_code", null)
        if (existingEmergencyCode.isNullOrBlank()) {
            // No emergency code exists — generate one
            val newCode = generateEmergencyCode()
            prefs.edit().putString("emergency_unlock_code", newCode).apply()
            Log.d("LOCK_SERVICE", "Generated new emergency code: $newCode")
            // Sync to server (fire-and-forget)
            syncEmergencyCodeToServer(newCode)
        }

        showLockOverlay()

        // Customer devices have no login — no update popup. Silently check and
        // self-update when the admin has pushed a newer version.
        checkForSelfUpdate()
    }

    /**
     * Update connection status display on lock screen.
     */
    private fun updateConnectionStatus() {
        val tvConnectionStatus = lockView?.findViewById<TextView>(R.id.tvConnectionStatus)
        val btnWifiConnect = lockView?.findViewById<Button>(R.id.btnWifiConnect)
        
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val isConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isMobile = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

        tvConnectionStatus?.apply {
            text = when {
                isWifi -> "✅ WiFi Connected"
                isMobile -> "📱 Mobile Data Connected"
                isConnected -> "🌐 Internet Connected"
                else -> "❌ No Internet — Connect WiFi"
            }
            setTextColor(when {
                isWifi || isMobile -> 0xFF10B981.toInt()
                isConnected -> 0xFFF59E0B.toInt()
                else -> 0xFFDC2626.toInt()
            })
        }

        // Update WiFi button text based on connection
        btnWifiConnect?.text = if (isWifi) "✅ WiFi Connected" else "📶 Connect WiFi"
        btnWifiConnect?.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(
                if (isWifi) 0xFF10B981.toInt() else 0xFF1E40AF.toInt()
            )
        )
    }

    /**
     * Live charging/battery indicator for the lock overlay. The lock screen
     * hides the system charging animation (keyguard + status bar disabled),
     * so without this the customer cannot tell the phone is charging.
     * ACTION_BATTERY_CHANGED is sticky — registering paints current state too.
     */
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_BATTERY_CHANGED) return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val charging = plugged != 0
            lockView?.findViewById<TextView>(R.id.tvChargeStatus)?.apply {
                text = if (charging) "Charging — $level%" else "Battery — $level%"
                setTextColor(if (charging) 0xFF10B981.toInt() else 0xCCFFFFFF.toInt())
                visibility = if (level >= 0) View.VISIBLE else View.GONE
            }
        }
    }

    private val connectivityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // NO AUTO-LOCK on internet disconnect — user mandate: manual lock only
            Log.d("LOCK_SERVICE", "Connectivity change detected (no auto-lock).")
        }
    }

    private fun registerAutoLockReceiver() {
        try {
            val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
            registerReceiver(connectivityReceiver, filter)
        } catch (e: Exception) {
            Log.w("LOCK_SERVICE", "connectivityReceiver already registered", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Security Active", NotificationManager.IMPORTANCE_HIGH)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Device Locked")
            .setContentText("Security Protocol Active")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .build()
    }

    private fun showLockOverlay() {
        if (lockView != null) return

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        }

        // ─── IME NOTE ──────────────────────────────────────────────────────────
        // Code entry uses a BUILT-IN hex keypad (no EditText). System keyboards
        // cannot reliably attach to TYPE_APPLICATION_OVERLAY windows — the IME
        // flashes open and instantly closes while the user types (focus fight
        // with the app window below + keyguard state). FLAG_NOT_TOUCH_MODAL is
        // kept so touches outside the window bounds still reach the system.
        // ─────────────────────────────────────────────────────────────────────────
        // ─── CHARGING NOTE ────────────────────────────────────────────────────
        // FLAG_KEEP_SCREEN_ON is deliberately NOT set. It used to keep the
        // screen awake 24/7 while locked → constant battery drain + heat →
        // many phones (Xiaomi/Oppo/Vivo etc.) PAUSE charging on thermal
        // protection, so customers reported "locking se charging block ho
        // jati hai". The overlay window stays ADDED to the WindowManager, so
        // it still covers the screen the instant it turns back on — the lock
        // itself is not weakened, and FLAG_TURN_SCREEN_ON still lights the
        // screen up when the lock first engages.
        // ─────────────────────────────────────────────────────────────────────────
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.CENTER
            screenOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        lockView = inflater.inflate(R.layout.layout_persistent_lock, null)

        // ─── Back / Home / Recents button block karo ──────────────────────────
        lockView?.isFocusable = true
        lockView?.isFocusableInTouchMode = true
        lockView?.setOnKeyListener { _, keyCode, _ ->
            keyCode == KeyEvent.KEYCODE_BACK ||
            keyCode == KeyEvent.KEYCODE_HOME ||
            keyCode == KeyEvent.KEYCODE_APP_SWITCH ||
            keyCode == KeyEvent.KEYCODE_MENU
        }

        // --- Dynamic Data Population ---
        val prefs = getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val shopNameStr = prefs.getString("shop_name", "Authorized Dealer") ?: "Authorized Dealer"
        val shopPhoneStr = prefs.getString("shop_phone", "Contact Provider") ?: "Contact Provider"
        val emiAmountStr = prefs.getString("emi_amount", "Rs. 2,500") ?: "Rs. 2,500"
        val emiDueDateStr = prefs.getString("emi_due_date", "20 March") ?: "20 March"
        
        lockView?.findViewById<TextView>(R.id.tvShopName)?.apply {
            text = shopNameStr.uppercase()
            visibility = if (shopNameStr.isNotEmpty()) View.VISIBLE else View.GONE
        }
        lockView?.findViewById<TextView>(R.id.tvShopPhone)?.apply {
            text = shopPhoneStr
            visibility = if (shopPhoneStr.isNotEmpty()) View.VISIBLE else View.GONE
        }
        lockView?.findViewById<TextView>(R.id.tvWhatsAppNumber)?.apply {
            text = if (shopPhoneStr.isNotEmpty() && shopPhoneStr != "Contact Provider") "WhatsApp — $shopPhoneStr" else "WhatsApp"
        }
        
        lockView?.findViewById<TextView>(R.id.tvEmiAmount)?.text = emiAmountStr
        lockView?.findViewById<TextView>(R.id.tvDueDate)?.text = emiDueDateStr
        
        // --- Hidden Unlock Entry — built-in hex keypad -----------------------
        // The system keyboard can NOT be used here: this overlay window fights
        // with the app window + keyguard for IME focus, so the keyboard opens and
        // instantly closes. The built-in 0-9/A-F keypad works in every state.
        val tvShowUnlock = lockView?.findViewById<TextView>(R.id.tvShowUnlock)
        val unlockContainer = lockView?.findViewById<View>(R.id.unlockContainer)
        val boxesContainer = lockView?.findViewById<LinearLayout>(R.id.codeBoxesContainer)
        val keypadGrid = lockView?.findViewById<GridLayout>(R.id.keypadGrid)
        val btnDelete = lockView?.findViewById<Button>(R.id.btnCodeDelete)
        val btnVerify = lockView?.findViewById<Button>(R.id.btnSubmitUnlock)
        val tvCodeStatus = lockView?.findViewById<TextView>(R.id.tvCodeStatus)

        val codeBuffer = StringBuilder()
        var failedAttempts = 0
        var keypadLockedUntil = 0L

        fun refreshCodeBoxes() {
            val container = boxesContainer ?: return
            for (i in 0 until container.childCount) {
                val box = container.getChildAt(i) as? TextView ?: continue
                box.text = if (i < codeBuffer.length) codeBuffer[i].toString() else ""
            }
        }

        fun showCodeStatus(message: String?) {
            tvCodeStatus?.text = message ?: ""
            tvCodeStatus?.visibility = if (message.isNullOrBlank()) View.GONE else View.VISIBLE
        }

        fun verifyCode() {
            if (codeBuffer.length != 8) {
                showCodeStatus("Code adhura hai (${codeBuffer.length}/8)")
                return
            }
            val entered = codeBuffer.toString().uppercase()

            // ── Valid code sets — same sources as the SMS protocol (SmsReceiver) ──
            val validUnlock = mutableSetOf<String>()
            val validRelease = mutableSetOf<String>()
            prefs.getString("sms_unlock_code", null)?.let { validUnlock.add(it) }
            prefs.getString("sms_deregister_code", null)?.let { validRelease.add(it) }
            listOf("device_imei", "device_imei2").forEach { key ->
                prefs.getString(key, null)?.takeIf { it.isNotBlank() }?.let { imei ->
                    validUnlock.add(com.pksafe.lock.manager.receiver.SmsReceiver.generateSmsCode("UNLOCK", imei))
                    validRelease.add(com.pksafe.lock.manager.receiver.SmsReceiver.generateSmsCode("DEREGISTER", imei))
                }
            }
            // Local 8-char MASTER backdoor → temporary unlock (dev/testing)
            prefs.getString("device_imei", null)?.takeIf { it.isNotBlank() }?.let {
                validUnlock.add(com.pksafe.lock.manager.receiver.SmsReceiver.generateSmsCode("MASTER", it).take(8))
            }
            
            // ── ONE-TIME EMERGENCY CODE ──────────────────────────────────
            // This code changes after each use — prevents customer from memorizing
            prefs.getString("emergency_unlock_code", null)?.let { code ->
                if (code.isNotBlank()) {
                    validUnlock.add(code.uppercase())
                    validUnlock.add(code.take(8).uppercase())
                }
            }
            
            // 64-char codes typing karna impractical hai — pehle 8 chars bhi accept
            val unlockSet = validUnlock.flatMap { c -> listOf(c.uppercase(), c.take(8).uppercase()) }.toSet()
            val releaseSet = validRelease.flatMap { c -> listOf(c.uppercase(), c.take(8).uppercase()) }.toSet()

            when (entered) {
                in unlockSet -> {
                    // Check if it was the emergency code (one-time use)
                    val emergencyCode = prefs.getString("emergency_unlock_code", null)?.uppercase()
                    val usedEmergencyCode = entered == emergencyCode || entered == emergencyCode?.take(8)?.uppercase()
                    
                    // Temporary unlock — same as SMS "UNLOCK#<code>"
                    prefs.edit().putBoolean("is_locked", false).commit()
                    
                    // If emergency code was used, generate NEW code immediately
                    if (usedEmergencyCode) {
                        val newCode = generateEmergencyCode()
                        prefs.edit().putString("emergency_unlock_code", newCode).apply()
                        Log.d("LOCK_SERVICE", "Emergency code used — generated new code: $newCode")
                        // Sync new code to server
                        syncEmergencyCodeToServer(newCode)
                        Toast.makeText(this@LockService, "Emergency code used. New code generated.", Toast.LENGTH_LONG).show()
                    }
                    
                    try {
                        com.pksafe.lock.manager.util.LockManager(this@LockService).unlockDevice()
                    } catch (e: Exception) {
                        Log.e("PKL_SERVICE", "Keypad unlock failed", e)
                    }
                    stopSelf()
                }
                in releaseSet -> {
                    // Full device release — same as SMS "DEREGISTER#<code>"
                    Toast.makeText(this@LockService, "Release code accepted — device released", Toast.LENGTH_LONG).show()
                    com.pksafe.lock.manager.util.DeviceDeregistrator.performFullDeregister(this@LockService)
                    stopSelf()
                }
                else -> {
                    failedAttempts++
                    codeBuffer.clear()
                    refreshCodeBoxes()
                    if (failedAttempts >= 5) {
                        keypadLockedUntil = System.currentTimeMillis() + 30_000
                        failedAttempts = 0
                        showCodeStatus("Bohot galat attempts — 30 second wait karein")
                    } else {
                        showCodeStatus("Invalid code (attempt $failedAttempts/5)")
                    }
                }
            }
        }

        fun onKeyPressed(ch: String) {
            val now = System.currentTimeMillis()
            if (now < keypadLockedUntil) {
                showCodeStatus("Please wait ${((keypadLockedUntil - now) / 1000) + 1}s")
                return
            }
            if (codeBuffer.length >= 8) return
            codeBuffer.append(ch)
            showCodeStatus(null)
            refreshCodeBoxes()
            if (codeBuffer.length == 8) verifyCode() // auto-verify on the 8th character
        }

        // Show/Hide unlock code entry
        tvShowUnlock?.setOnClickListener {
            unlockContainer?.visibility = if (unlockContainer?.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            if (unlockContainer?.visibility == View.VISIBLE) {
                codeBuffer.clear()
                refreshCodeBoxes()
                showCodeStatus(null)
            }
        }

        // Hex keypad keys (tag = character)
        keypadGrid?.let { grid ->
            for (i in 0 until grid.childCount) {
                grid.getChildAt(i).setOnClickListener { view ->
                    (view.tag as? String)?.let { ch -> onKeyPressed(ch) }
                }
            }
        }

        btnDelete?.setOnClickListener {
            if (codeBuffer.isNotEmpty()) {
                codeBuffer.deleteCharAt(codeBuffer.length - 1)
                refreshCodeBoxes()
                showCodeStatus(null)
            }
        }

        btnVerify?.setOnClickListener { verifyCode() }

        // --- WhatsApp: contact the SHOPKEEPER of this device ---
        // The chat opens with the device's own shopkeeper (shop_phone pref =
        // device.contactPhone / shopkeeper.phone from the server). The admin
        // support number is only a fallback when no shopkeeper number exists.
        lockView?.findViewById<View>(R.id.btnWhatsAppSupport)?.setOnClickListener {
            try {
                val shopNameStr = prefs.getString("shop_name", "Authorized Dealer") ?: "Authorized Dealer"
                val shopPhoneStr = prefs.getString("shop_phone", "") ?: ""
                val emiAmountStr = prefs.getString("emi_amount", "") ?: ""
                val emiDueDateStr = prefs.getString("emi_due_date", "") ?: ""
                val deviceInfo = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                val message = "Assalam o Alaikum,\n" +
                        "Mera device lock ho gaya hai.\n" +
                        "Device: $deviceInfo\n" +
                        "Shop: $shopNameStr\n" +
                        "EMI: $emiAmountStr (Due: $emiDueDateStr)\n" +
                        "Please mujh se rabta karein."
                val waNumber = toWhatsAppNumber(shopPhoneStr) ?: "923069829158"
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(
                        "https://wa.me/$waNumber?text=${Uri.encode(message)}"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://play.google.com/store/apps/details?id=com.whatsapp")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(this@LockService, "WhatsApp install nahi hai", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // --- WiFi Connect Button (Scan + List + Password + Connect) ---
        val btnWifiConnect = lockView?.findViewById<Button>(R.id.btnWifiConnect)
        val tvConnectionStatus = lockView?.findViewById<TextView>(R.id.tvConnectionStatus)

        // WiFi scan and connect handler
        btnWifiConnect?.setOnClickListener {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            
            // Ensure WiFi is ON
            if (!wifiManager.isWifiEnabled) {
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = true
                Toast.makeText(this@LockService, "WiFi ON ho raha hai...", Toast.LENGTH_SHORT).show()
                Handler(Looper.getMainLooper()).postDelayed({
                    showWifiNetworksDialog(wifiManager)
                }, 1500)
            } else {
                showWifiNetworksDialog(wifiManager)
            }
        }

        // Initial status update
        updateConnectionStatus()

        // Periodic status update every 30 seconds
        statusRunnable = object : Runnable {
            override fun run() {
                updateConnectionStatus()
                statusHandler.postDelayed(this, 30000)
            }
        }
        statusRunnable?.let { statusHandler.postDelayed(it, 30000) }

        try {
            windowManager.addView(lockView, params)
            lockView?.requestFocus()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // ─── LIVE REFRESH: Fetch fresh EMI data from server in background ────────
        // Even if SharedPrefs had stale/empty values, this will update the overlay
        // with real data from the DB within a few seconds.
        val imei = prefs.getString("device_imei", null)
        if (!imei.isNullOrBlank()) {
            fetchAndRefreshLockData(imei)
        }
    }

    /**
     * Fetch fresh device/EMI info from API and update the lock overlay views.
     * Runs on IO thread; UI update posted back to main thread via Handler.
     */
    private fun fetchAndRefreshLockData(imei: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val retrofit = Retrofit.Builder()
                    .baseUrl(com.pksafe.lock.manager.util.Constants.BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                val api = retrofit.create(ApiService::class.java)

                val response = api.getDeviceStatus("", imei)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.success) {
                        val device     = body.data.device
                        val emiSummary = body.data.emiSummary

                        val shopName  = device.shopkeeper?.shopName
                            ?: device.shopkeeper?.name
                            ?: "Authorized Dealer"
                        val shopPhone = device.contactPhone ?: device.shopkeeper?.phone ?: "Contact Provider"

                        val rawAmount = emiSummary.nextEmi?.amount ?: device.emiAmount
                        val emiAmount = "Rs. ${rawAmount.toInt()}"

                        val rawDate = emiSummary.nextEmi?.dueDate ?: ""
                        val formattedDate: String = if (rawDate.isNotBlank()) {
                            try {
                                val inFmt  = java.text.SimpleDateFormat(
                                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                                    java.util.Locale.US
                                ).also { it.timeZone = java.util.TimeZone.getTimeZone("UTC") }
                                val outFmt = java.text.SimpleDateFormat(
                                    "dd MMMM",
                                    java.util.Locale.US
                                )
                                val parsed = inFmt.parse(rawDate)
                                if (parsed != null) outFmt.format(parsed) else rawDate
                            } catch (e: Exception) { rawDate }
                        } else "Contact Provider"

                        // Extract additional payment info
                        val totalBalance = device.balance.toInt()
                        val totalPaid = emiSummary.totalPaidAmount?.toInt() ?: 0
                        val paidInstallments = emiSummary.paid ?: 0
                        val totalInstallments = emiSummary.total ?: 0
                        val nextRemaining = emiSummary.nextEmi?.remaining?.toInt() ?: 0

                        // Persist so next cold-start of LockService also gets fresh data
                        val prefs = getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
                        prefs.edit()
                            .putString("shop_name",    shopName)
                            .putString("shop_phone",   shopPhone)
                            .putString("emi_amount",   emiAmount)
                            .putString("emi_due_date", formattedDate)
                            .putInt("total_balance",   totalBalance)
                            .putInt("total_paid",      totalPaid)
                            .putInt("paid_installments", paidInstallments)
                            .putInt("total_installments", totalInstallments)
                            .putInt("next_remaining",  nextRemaining)
                            .apply()

                        Log.d("LOCK_REFRESH", "Live data fetched: shop=$shopName emi=$emiAmount due=$formattedDate balance=$totalBalance")

                        // ── Push updates to the overlay on the main thread ────────
                        withContext(Dispatchers.Main) {
                            lockView?.let { v ->
                                v.findViewById<TextView>(R.id.tvShopName)?.apply {
                                    text = shopName.uppercase()
                                    visibility = View.VISIBLE
                                }
                                v.findViewById<TextView>(R.id.tvShopPhone)?.apply {
                                    text = shopPhone
                                    visibility = View.VISIBLE
                                }
                                v.findViewById<TextView>(R.id.tvWhatsAppNumber)?.apply {
                                    text = if (shopPhone != "Contact Provider") "WhatsApp — $shopPhone" else "WhatsApp"
                                }
                                v.findViewById<TextView>(R.id.tvEmiAmount)?.text = emiAmount
                                v.findViewById<TextView>(R.id.tvDueDate)?.text  = formattedDate
                                // Update payment progress
                                v.findViewById<TextView>(R.id.tvBalanceAmount)?.apply {
                                    text = "Rs. ${totalBalance}"
                                    visibility = View.VISIBLE
                                }
                                v.findViewById<TextView>(R.id.tvInstallmentProgress)?.apply {
                                    text = "${paidInstallments}/${totalInstallments} Paid"
                                    visibility = View.VISIBLE
                                }
                                v.findViewById<View>(R.id.paymentInfoSection)?.visibility = View.VISIBLE
                            }
                        }
                    }
                } else {
                    Log.w("LOCK_REFRESH", "API returned ${response.code()} — keeping cached values")
                }
            } catch (e: Exception) {
                Log.w("LOCK_REFRESH", "Network error — keeping cached values: ${e.message}")
            }
        }
    }

    /**
     * Remote self-update for CUSTOMER devices: no login, no popup. Delegates to
     * AutoUpdater.selfUpdateIfAvailable(), which throttles to one attempt per
     * server version (24h retry) and installs silently under Device Owner.
     */
    private fun checkForSelfUpdate() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                com.pksafe.lock.manager.util.AutoUpdater(this@LockService).selfUpdateIfAvailable()
            } catch (e: Exception) {
                Log.w("SELF_UPDATE", "Self-update failed: ${e.message}")
            }
        }
    }

    /**
     * Get signal strength description from RSSI value.
     */
    private fun getSignalStrength(rssi: Int): String {
        return when {
            rssi >= -50 -> "Excellent"
            rssi >= -60 -> "Good"
            rssi >= -70 -> "Fair"
            rssi >= -80 -> "Weak"
            else -> "Very Weak"
        }
    }

    /**
     * Connect to selected WiFi network.
     * Handles both open and secured networks with password dialog.
     */
    private fun connectToWifi(wifiManager: WifiManager, scanResult: ScanResult) {
        val isSecure = scanResult.capabilities.contains("WEP") || 
                       scanResult.capabilities.contains("PSK") || 
                       scanResult.capabilities.contains("EAP")
    
        if (isSecure) {
            // Dismiss any existing password dialog
            passwordDialog?.dismiss()
    
            // Show password dialog
            val editText = EditText(this@LockService).apply {
                hint = "Enter WiFi password"
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                setPadding(48, 32, 48, 32)
            }
    
            passwordDialog = AlertDialog.Builder(this@LockService)
                .setTitle("Connect to ${scanResult.SSID}")
                .setView(editText)
                .setPositiveButton("Connect") { _, _ ->
                    val password = editText.text.toString()
                    if (password.isNotBlank()) {
                        passwordDialog?.dismiss()
                        performWifiConnection(wifiManager, scanResult, password)
                    } else {
                        Toast.makeText(this@LockService, "Password is required", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
                .setOnDismissListener {
                    passwordDialog = null
                }
                .create()
    
            // Set window type for overlay
            passwordDialog?.window?.apply {
                setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                setFlags(
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                )
            }
    
            passwordDialog?.show()
        } else {
            // Open network — connect directly
            performWifiConnection(wifiManager, scanResult, null)
        }
    }

    /**
     * Show dialog with list of available WiFi networks.
     * Scans for networks and displays them in a scrollable list.
     */
    private fun showWifiNetworksDialog(wifiManager: WifiManager) {
        // Dismiss any existing dialog first
        wifiDialog?.dismiss()

        // Trigger scan
        @Suppress("DEPRECATION")
        wifiManager.startScan()

        // Get scan results with permission check
        val scanResults = try {
            @Suppress("DEPRECATION")
            wifiManager.scanResults ?: emptyList()
        } catch (e: SecurityException) {
            Log.w("LOCK_SERVICE", "WiFi scan permission denied: ${e.message}")
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this@LockService, "WiFi permission required. Use system settings.", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return
        }

        if (scanResults.isEmpty()) {
            Toast.makeText(this, "No WiFi networks found. Please try again.", Toast.LENGTH_SHORT).show()
            return
        }

        // Filter and sort by signal strength
        val networks = scanResults
            .filter { it.SSID.isNotBlank() }
            .sortedByDescending { it.level }
            .distinctBy { it.SSID }

        val networkNames = networks.map { "${it.SSID} (${getSignalStrength(it.level)})" }.toTypedArray()

        // Build dialog with TYPE_APPLICATION_OVERLAY for Service context
        wifiDialog = AlertDialog.Builder(this@LockService)
            .setTitle("Select WiFi Network")
            .setItems(networkNames) { _, which: Int ->
                val selectedNetwork = networks[which]
                wifiDialog?.dismiss()
                connectToWifi(wifiManager, selectedNetwork)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setOnDismissListener {
                wifiDialog = null
            }
            .create()

        // Set window type for overlay and keep dialog open
        wifiDialog?.window?.apply {
            setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            // Keep dialog open - don't dismiss on outside touch
            setFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            )
        }

        wifiDialog?.show()
    }

    /**
     * Perform actual WiFi connection.
     * Uses different APIs based on Android version.
     */
    private fun performWifiConnection(wifiManager: WifiManager, scanResult: ScanResult, password: String?) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ — Use WifiNetworkSpecifier
                val specifier = WifiNetworkSpecifier.Builder()
                    .setSsid(scanResult.SSID)
                    .apply {
                        if (password != null) {
                            setWpa2Passphrase(password)
                        }
                    }
                    .build()

                val request = android.net.NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .setNetworkSpecifier(specifier)
                    .build()

                val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                connectivityManager.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        Log.d("LOCK_SERVICE", "WiFi connected successfully (Android 10+)")
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(this@LockService, "WiFi connected! ✅", Toast.LENGTH_SHORT).show()
                            updateConnectionStatus()
                        }
                    }

                    override fun onUnavailable() {
                        Log.w("LOCK_SERVICE", "WiFi connection failed (Android 10+)")
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(this@LockService, "WiFi connection failed ❌", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            } else {
                // Android 9 and below — Use WifiConfiguration (deprecated but works)
                @Suppress("DEPRECATION")
                val config = WifiConfiguration().apply {
                    SSID = "\"${scanResult.SSID}\""
                    
                    when {
                        scanResult.capabilities.contains("WEP") -> {
                            allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                            allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
                            allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED)
                            if (password != null) {
                                wepKeys[0] = "\"$password\""
                            }
                            wepTxKeyIndex = 0
                        }
                        scanResult.capabilities.contains("PSK") -> {
                            allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                            if (password != null) {
                                preSharedKey = "\"$password\""
                            }
                        }
                        scanResult.capabilities.contains("EAP") -> {
                            // EAP networks need more complex configuration — open settings instead
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(this@LockService, "Enterprise WiFi — use system settings", Toast.LENGTH_LONG).show()
                                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                            return
                        }
                        else -> {
                            allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                        }
                    }
                }

                @Suppress("DEPRECATION")
                val networkId = wifiManager.addNetwork(config)
                if (networkId != -1) {
                    @Suppress("DEPRECATION")
                    wifiManager.enableNetwork(networkId, true)
                    Log.d("LOCK_SERVICE", "WiFi connection initiated (Android 9-)")
                    Handler(Looper.getMainLooper()).postDelayed({
                        Toast.makeText(this@LockService, "WiFi connecting...", Toast.LENGTH_SHORT).show()
                        updateConnectionStatus()
                    }, 2000)
                } else {
                    Toast.makeText(this@LockService, "Failed to configure WiFi network", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("LOCK_SERVICE", "WiFi connection error: ${e.message}")
            Toast.makeText(this@LockService, "Connection failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Generate a random 8-character emergency unlock code.
     * Uses uppercase letters and digits for easy typing.
     */
    private fun generateEmergencyCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // Removed confusing chars (I, O, 0, 1)
        return (1..8).map { chars.random() }.joinToString("")
    }

    /**
     * Sync emergency code to server so shopkeeper can see it in control panel.
     */
    private fun syncEmergencyCodeToServer(code: String) {
        val prefs = getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val imei = prefs.getString("device_imei", null)
        if (imei.isNullOrBlank()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val retrofit = Retrofit.Builder()
                    .baseUrl(com.pksafe.lock.manager.util.Constants.BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                val api = retrofit.create(ApiService::class.java)

                val response = api.syncEmergencyCode(imei, mapOf("emergencyCode" to code))
                if (response.isSuccessful) {
                    Log.d("LOCK_SERVICE", "Emergency code synced to server: $code")
                } else {
                    Log.w("LOCK_SERVICE", "Failed to sync emergency code: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("LOCK_SERVICE", "Emergency code sync failed: ${e.message}")
            }
        }
    }

    /**
     * Normalize a stored phone number (03xx…, +92…, 92…, spaces/dashes) into
     * WhatsApp's wa.me digits format (92XXXXXXXXXX). Returns null when blank.
     */
    private fun toWhatsAppNumber(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val digits = raw.filter { it.isDigit() }
        if (digits.length < 10) return null
        return when {
            digits.startsWith("92") -> digits
            digits.startsWith("0") -> "92" + digits.drop(1)
            else -> digits
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) { }
        lockView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) { }
        }
        try {
            unregisterReceiver(connectivityReceiver)
        } catch (e: Exception) {}
        
        // Clean up status update handler
        try {
            statusRunnable?.let { statusHandler.removeCallbacks(it) }
        } catch (e: Exception) {}
        
        // Dismiss any open dialogs
        try {
            wifiDialog?.dismiss()
            wifiDialog = null
            passwordDialog?.dismiss()
            passwordDialog = null
        } catch (e: Exception) {}
        
        lockView = null
    }
}
