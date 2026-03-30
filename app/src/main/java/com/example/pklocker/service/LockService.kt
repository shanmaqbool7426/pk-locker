package com.example.pklocker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.net.Uri
import android.widget.ImageView
import com.example.pklocker.R
import android.content.BroadcastReceiver
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import android.content.IntentFilter
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.NotificationCompat

class LockService : Service() {

    private lateinit var windowManager: WindowManager
    private var lockView: View? = null
    private val CHANNEL_ID = "LockServiceChannel"
    private val MASTER_UNLOCK_CODE = "123456"

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

        // ─── CRITICAL FIX ────────────────────────────────────────────────────────
        // FLAG_NOT_TOUCH_MODAL HATAYA gaya hai — ye asli bug tha!
        // Jab ye flag lagta tha toh overlay ke bahar ki touches neeche jati theen
        // Ab overlay SABHI touches intercept karega — user escape nahi kar sakta
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
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            PixelFormat.OPAQUE  // OPAQUE: neeche kuch bhi nazar nahi aayega
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
        val shopNameStr = prefs.getString("shop_name", "Ali Mobile Shop") ?: "Ali Mobile Shop"
        val shopPhoneStr = prefs.getString("shop_phone", "0300-1234567") ?: "0300-1234567"
        
        lockView?.findViewById<TextView>(R.id.tvShopName)?.text = shopNameStr.uppercase()
        lockView?.findViewById<TextView>(R.id.tvShopPhone)?.text = shopPhoneStr
        
        // --- Dial Support Listener ---
        lockView?.findViewById<Button>(R.id.btnContactSupport)?.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$shopPhoneStr")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }

        // --- Hidden Unlock Entry Logic ---
        val tvShowUnlock = lockView?.findViewById<TextView>(R.id.tvShowUnlock)
        val unlockContainer = lockView?.findViewById<View>(R.id.unlockContainer)
        val btnUnlock = lockView?.findViewById<Button>(R.id.btnSubmitUnlock)
        val codeInput = lockView?.findViewById<EditText>(R.id.unlockCodeInput)

        // Show/Hide unlock code entry
        tvShowUnlock?.setOnClickListener {
            unlockContainer?.visibility = if (unlockContainer?.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        btnUnlock?.setOnClickListener {
            if (codeInput?.text.toString() == MASTER_UNLOCK_CODE) {
                prefs.edit().putBoolean("is_locked", false).apply()
                stopSelf()
            } else {
                Toast.makeText(this, "Invalid Security Code!", Toast.LENGTH_SHORT).show()
                codeInput?.text?.clear()
            }
        }

        try {
            windowManager.addView(lockView, params)
            lockView?.requestFocus()
        } catch (e: Exception) {
            e.printStackTrace()
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
