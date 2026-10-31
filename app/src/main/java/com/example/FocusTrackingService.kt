package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

class FocusTrackingService : Service() {

    private lateinit var prefsMap: PreferencesHelper
    private var currentForegroundPackage: String? = null
    private var trackingTimer: Timer? = null
    private var lastLockEnforcementTime: Long = 0L
    private var lastAlarmBypassTime: Long = 0L
    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            val isInteractive = powerManager?.isInteractive ?: false

            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                // User switched off the screen: immediately stop tracking loop so screen stays OFF!
                trackingTimer?.cancel()
                trackingTimer = null
            } else if (intent?.action == Intent.ACTION_SCREEN_ON || intent?.action == Intent.ACTION_USER_PRESENT) {
                // User switched on the screen: restart tracking loop
                startTrackingLoop()
                
                // Only pull Lock Screen to front if lock is active AND screen is turned on!
                if (isInteractive && prefsMap.isLockTimeActive()) {
                    val uiIntent = Intent(applicationContext, LockScreenActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("REASON", "LOCKDOWN")
                    }
                    val bundle = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        android.app.ActivityOptions.makeBasic()
                            .setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                            .toBundle()
                    } else {
                        null
                    }
                    try {
                        if (bundle != null) {
                            startActivity(uiIntent, bundle)
                        } else {
                            startActivity(uiIntent)
                        }
                    } catch (e: Exception) {
                        try {
                            val pendingIntent = android.app.PendingIntent.getActivity(
                                applicationContext,
                                100,
                                uiIntent,
                                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                            )
                            if (bundle != null) {
                                pendingIntent.send(applicationContext, 0, null, null, null, null, bundle)
                            } else {
                                pendingIntent.send()
                            }
                            sendBroadcast(Intent("com.example.FORCE_FRONT"))
                        } catch (e2: Exception) {}
                    }
                }
            }
        }
    }

    private fun startTrackingLoop() {
        trackingTimer?.cancel()
        val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        if (powerManager?.isInteractive == false) {
            return
        }
        trackingTimer = Timer()
        trackingTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                try {
                    val pm = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                    if (pm?.isInteractive == true) {
                        checkForegroundApp()
                    }
                } catch (t: Throwable) {
                    // Ignore ALL errors (including OutOfMemoryError) to ensure the loop never dies
                }
            }
        }, 0, 500) // check every 500ms on a dedicated background thread!
    }

    override fun onCreate() {
        super.onCreate()
        prefsMap = PreferencesHelper(this)
        
        // Register dynamic receiver for screen off, screen on, and user present safely with exported flags
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
        
        // Create Notification Channel for Foreground Service
        val channelId = "focus_tracking_channel"
        val channelName = "Focus Tracking Service"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
            chan.lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(chan)
        }
        
        val lockUiIntent = Intent(this, LockScreenActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            putExtra("REASON", "LOCKDOWN")
        }
        val fullScreenPendingIntent = android.app.PendingIntent.getActivity(
            this,
            500,
            lockUiIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notifBuilder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Focus Lock Active")
            .setContentText("Monitoring apps to prevent distractions.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(fullScreenPendingIntent)

        val notification = notifBuilder.build()
            
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
        startTrackingLoop()
        schedulePingAlarm()

        // Immediate enforcement on boot or restart ONLY IF screen is active
        val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        if (powerManager?.isInteractive == true && prefsMap.isLockTimeActive()) {
            enforceLock("LOCKDOWN")
        }
    }

    private fun schedulePingAlarm() {
        val intent = Intent(applicationContext, RestartReceiver::class.java).apply {
            action = "com.example.PING_SERVICE"
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            applicationContext,
            3, // Use request code 3 for the ping alarm
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        
        try {
            // Non-wakeup alarm so device screen is never woken up while screen is off
            alarmManager.set(
                android.app.AlarmManager.ELAPSED_REALTIME,
                android.os.SystemClock.elapsedRealtime() + 15 * 60 * 1000L,
                pendingIntent
            )
        } catch (e: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        if (powerManager?.isInteractive == true && ::prefsMap.isInitialized && prefsMap.isLockTimeActive()) {
            enforceLock("LOCKDOWN")
        }
        return START_STICKY
    }
    override fun onDestroy() {
        super.onDestroy()
        trackingTimer?.cancel()
        
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {}
        
        val broadcastIntent = Intent(applicationContext, RestartReceiver::class.java).apply {
            action = "com.example.RESTART_SERVICE"
        }
        sendBroadcast(broadcastIntent)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val broadcastIntent = Intent(applicationContext, RestartReceiver::class.java).apply {
            action = "com.example.RESTART_SERVICE"
        }
        sendBroadcast(broadcastIntent)
        
        // Auto-reopen the app UI ONLY if lock is active AND screen is on!
        val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        if (powerManager?.isInteractive == true && prefsMap.isLockTimeActive()) {
            val reopenIntent = Intent(applicationContext, LockScreenActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra("REASON", "LOCKDOWN")
            }
            try {
                startActivity(reopenIntent)
            } catch (e: Exception) {}
        }
        
        // Also schedule a non-wakeup alarm just in case the broadcast fails to start it in time
        val serviceIntent = Intent(applicationContext, FocusTrackingService::class.java)
        val pendingIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.PendingIntent.getForegroundService(
                applicationContext,
                2,
                serviceIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            android.app.PendingIntent.getService(
                applicationContext,
                2,
                serviceIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
        }
        
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        alarmManager.set(
            android.app.AlarmManager.ELAPSED_REALTIME,
            android.os.SystemClock.elapsedRealtime() + 1000,
            pendingIntent
        )
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun getTodayString(): String {
        return SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
    }

    private fun goToHome() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            val pendingIntent = android.app.PendingIntent.getActivity(
                this,
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent.send()
            sendBroadcast(Intent("com.example.FORCE_FRONT"))
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun enforceLock(reason: String) {
        // If the user has switched off the screen, NEVER wake it up or force launch!
        val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        if (powerManager?.isInteractive != true) {
            return
        }

        // Never interrupt an active call or USSD session
        if (TelephonyHelper.isCallOrUssdActive(this)) {
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastLockEnforcementTime < 300) {
            return
        }
        lastLockEnforcementTime = now
        
        val intent = Intent(this, LockScreenActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            putExtra("REASON", reason)
        }

        val bundle = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            android.app.ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                .toBundle()
        } else {
            null
        }
        
        try {
            // Try standard start first
            if (bundle != null) {
                startActivity(intent, bundle)
            } else {
                startActivity(intent)
            }
            
            val pendingIntent = android.app.PendingIntent.getActivity(
                this,
                reason.hashCode(),
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            if (bundle != null) {
                pendingIntent.send(this, 0, null, null, null, null, bundle)
            } else {
                pendingIntent.send()
            }
            sendBroadcast(Intent("com.example.FORCE_FRONT"))
        } catch (e: Exception) {}
    }

    private fun checkForegroundApp() {
        // If the user has switched off the screen, don't inspect apps or enforce lock!
        val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        if (powerManager?.isInteractive != true) {
            return
        }

        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(time - 60000, time) // Look at the last minute to catch delayed events
        val event = UsageEvents.Event()
        
        var latestPackage: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                latestPackage = event.packageName
            }
        }
        
        if (latestPackage == null) {
            latestPackage = currentForegroundPackage
        }

        if (latestPackage == null) {
            val usageStats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 3600 * 24, time)
            if (usageStats != null && usageStats.isNotEmpty()) {
                latestPackage = usageStats.maxByOrNull { it.lastTimeUsed }?.packageName
            }
        }

        if (latestPackage == null) return
        val packageName = latestPackage
        
        // Detect App Switch / Intercept Multitasking
        if (currentForegroundPackage != null && currentForegroundPackage != packageName) {
            val isMyUi = packageName == this.packageName
            val isWhitelisted = isAppWhitelisted(packageName)
            val isHome = packageName.contains("launcher") || packageName.contains("home") || packageName.contains("systemui")
            
            // The user opened a NEW third-party app
            if (!isMyUi && !isHome) {
                // Do nothing here, rely on standard OS memory management
            }
        }
        
        currentForegroundPackage = packageName

        // Permanent YouTube Block
        if (packageName == "com.google.android.youtube" || packageName.contains("youtube")) {
            enforceLock("YOUTUBE")
            return
        }

        // General 30-minute block
        if (!isAppWhitelisted(packageName)) {
            val currentTime = System.currentTimeMillis()
            val lockExp = prefsMap.getAppLockExpiration(packageName)
            if (currentTime < lockExp) {
                // App is locked
                enforceLock("GENERAL_LIMIT")
            } else {
                var sessionUsage = prefsMap.getAppSessionUsage(packageName)
                val lastUsed = prefsMap.getAppLastUsed(packageName)
                val timeDelta = currentTime - lastUsed

                // 1. If you haven't used the app for over 30 minutes, your usage timer resets!
                if (lastUsed > 0L && timeDelta > 30 * 60 * 1000L) {
                    sessionUsage = 0L 
                } 
                // 2. If the tracker is running normally (checked within last 10 seconds), add exact elapsed time
                else if (lastUsed > 0L && timeDelta > 0L && timeDelta < 10000L) {
                    sessionUsage += timeDelta
                } 
                // 3. If first launch or recovering from a deep OS sleep, add the default 500ms tick
                else {
                    sessionUsage += 500L
                }

                prefsMap.setAppLastUsed(packageName, currentTime)
                prefsMap.setAppSessionUsage(packageName, sessionUsage)

                if (sessionUsage >= 30 * 60 * 1000L) { // Exactly 30 minutes
                    prefsMap.setAppLockExpiration(packageName, currentTime + 5 * 60 * 60 * 1000L) // lock for 5 hours
                    prefsMap.setAppSessionUsage(packageName, 0L) // reset usage
                    
                    enforceLock("GENERAL_LIMIT")
                }
            }
        }

        // Focus Block - block settings too to make it hard to remove
        val isSettings = packageName.contains("settings") || packageName.contains("packageinstaller") || packageName.contains("uninstaller")
        if (prefsMap.isLockTimeActive() || (prefsMap.isLockEnabled && isSettings)) {
            if (!isAppWhitelisted(packageName)) {
                if (isSettings && System.currentTimeMillis() < prefsMap.allowSettingsUntilMillis) {
                    // Allowed temporarily for setup
                } else {
                    enforceLock("LOCKDOWN")
                }
            }
        }
    }

    private fun isAppWhitelisted(packageName: String): Boolean {
        val whitelist = listOf(
            "com.example", // old namespace
            "com.aistudio.focuslock.fcklqk", // new application ID
            this.packageName
        )
        
        // Allow phone calls, dialer, USSD sessions (*182#, *131#, etc.), SIM toolkit, and carrier menus
        if (TelephonyHelper.isPhoneOrUssdPackage(packageName)) {
            return true
        }
        
        // Allow system document picker while user is actively picking a PDF to read
        if (packageName.contains("documentsui") || packageName.contains("filemanager") ||
            packageName.contains("documentpicker") || packageName.contains("providers.media") ||
            packageName.contains("com.google.android.apps.docs")) {
            if (System.currentTimeMillis() < prefsMap.allowDocumentPickerUntilMillis) {
                return true
            }
        }
        
        // When active lock is ON: systemui (recents/notification shade), launcher, home, and third party apps are ALL BLOCKED
        if (prefsMap.isLockTimeActive()) {
            return whitelist.contains(packageName)
        }
        
        // Outside of active lock, allow system navigation and home launcher
        if (packageName.contains("systemui") || packageName.contains("launcher") || 
            packageName.contains("home") || packageName.contains("recents")) {
            return true
        }
        
        return whitelist.contains(packageName)
    }
}
