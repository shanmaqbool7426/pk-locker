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
import android.util.Log
import android.content.IntentFilter
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.Toast
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        // Register Auto-Lock listener if enabled
        registerAutoLockReceiver()
        
        showLockOverlay()
    }

    private val connectivityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val prefs = getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
            val isAutoLockEnabled = prefs.getBoolean("auto_lock_enabled", false)
            
            if (isAutoLockEnabled && !isOnline()) {
                Log.w("AUTO_LOCK", "Internet disconnected! Triggering Lock.")
                prefs.edit().putBoolean("is_locked", true).apply()
                // Update UI or restart session if needed
            }
        }
    }

    private fun registerAutoLockReceiver() {
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiver(connectivityReceiver, filter)
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
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
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
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
            text = "SUPPORT: $shopPhoneStr"
            visibility = if (shopPhoneStr.isNotEmpty()) View.VISIBLE else View.GONE
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
            // 64-char codes typing karna impractical hai — pehle 8 chars bhi accept
            val unlockSet = validUnlock.flatMap { c -> listOf(c.uppercase(), c.take(8).uppercase()) }.toSet()
            val releaseSet = validRelease.flatMap { c -> listOf(c.uppercase(), c.take(8).uppercase()) }.toSet()

            when (entered) {
                in unlockSet -> {
                    // Temporary unlock — same as SMS "UNLOCK#<code>"
                    prefs.edit().putBoolean("is_locked", false).commit()
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

        // --- WhatsApp Help & Support ---
        lockView?.findViewById<View>(R.id.btnWhatsAppSupport)?.setOnClickListener {
            try {
                val shopNameStr = prefs.getString("shop_name", "Authorized Dealer") ?: "Authorized Dealer"
                val emiAmountStr = prefs.getString("emi_amount", "") ?: ""
                val emiDueDateStr = prefs.getString("emi_due_date", "") ?: ""
                val deviceInfo = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                val message = "Assalam o Alaikum,\n" +
                        "Mera device lock ho gaya hai.\n" +
                        "Device: $deviceInfo\n" +
                        "Shop: $shopNameStr\n" +
                        "EMI: $emiAmountStr (Due: $emiDueDateStr)\n" +
                        "Please mujh se rabta karein."
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(
                        "https://wa.me/923069829158?text=${Uri.encode(message)}"
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
                        val shopPhone = device.shopkeeper?.phone ?: "Contact Provider"

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
                                    text = "SUPPORT: $shopPhone"
                                    visibility = View.VISIBLE
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

    override fun onDestroy() {
        super.onDestroy()
        lockView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) { }
        }
        try {
            unregisterReceiver(connectivityReceiver)
        } catch (e: Exception) {}
        
        lockView = null
    }
}
