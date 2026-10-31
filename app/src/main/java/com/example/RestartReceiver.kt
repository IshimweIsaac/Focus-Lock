package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, FocusTrackingService::class.java)
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            try {
                context.startService(serviceIntent)
            } catch (e2: Exception) {}
        }
        
        // Schedule next ping alarm if it's a ping broadcast
        if (intent.action == "com.example.PING_SERVICE") {
            try {
                val pingIntent = Intent(context, RestartReceiver::class.java).apply {
                    action = "com.example.PING_SERVICE"
                }
                val pendingPingIntent = android.app.PendingIntent.getBroadcast(
                    context, 3, pingIntent, 
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                // Use standard non-wakeup alarm so screen is never forced on while sleeping
                alarmManager.set(
                    android.app.AlarmManager.ELAPSED_REALTIME,
                    android.os.SystemClock.elapsedRealtime() + 15 * 60 * 1000L,
                    pendingPingIntent
                )
            } catch (e: Exception) {}
        }
        
        // When device boots, package replaced, or user unlocks, enforce lock if active
        val isBootOrUnlock = intent.action == Intent.ACTION_USER_PRESENT ||
                             intent.action == Intent.ACTION_BOOT_COMPLETED ||
                             intent.action == "android.intent.action.LOCKED_BOOT_COMPLETED" ||
                             intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
                             intent.action == Intent.ACTION_MY_PACKAGE_REPLACED

        if (isBootOrUnlock) {
            val prefs = PreferencesHelper(context)
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            val isInteractive = powerManager?.isInteractive ?: true
            if (prefs.isLockTimeActive() && isInteractive) {
                val uiIntent = Intent(context, LockScreenActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
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
                        context.startActivity(uiIntent, bundle)
                    } else {
                        context.startActivity(uiIntent)
                    }
                } catch (e: Exception) {}

                try {
                    val pendingIntent = android.app.PendingIntent.getActivity(
                        context,
                        100,
                        uiIntent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                    if (bundle != null) {
                        pendingIntent.send(context, 0, null, null, null, null, bundle)
                    } else {
                        pendingIntent.send()
                    }
                } catch (e2: Exception) {}
            }
        }
    }
}
