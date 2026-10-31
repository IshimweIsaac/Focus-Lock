package com.example

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telecom.TelecomManager
import android.telephony.TelephonyManager

object TelephonyHelper {

    /**
     * Identifies if a given package name belongs to a telephony, dialer,
     * in-call UI, carrier service, SIM Toolkit (STK), or USSD provider.
     */
    fun isPhoneOrUssdPackage(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val lower = packageName.lowercase()
        return lower == "com.android.phone" ||
               lower.endsWith(".phone") ||
               lower.contains(".phone.") ||
               lower.contains("dialer") ||
               lower.contains("incallui") ||
               lower.contains("telecom") ||
               lower.contains("telephony") ||
               lower.contains("stk") || // SIM Toolkit for USSD banking and menus
               lower.contains("sim") ||
               lower.contains("carrier")
    }

    /**
     * Checks if a phone call or USSD session is currently active or in the foreground.
     */
    fun isCallOrUssdActive(context: Context): Boolean {
        // 1. Check if an active call exists via TelecomManager
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                if (telecomManager?.isInCall == true) {
                    return true
                }
            }
        } catch (e: Exception) {}

        // 2. Check TelephonyManager call state (ringing or off-hook)
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (telephonyManager != null) {
                @Suppress("DEPRECATION")
                val state = telephonyManager.callState
                if (state != TelephonyManager.CALL_STATE_IDLE) {
                    return true
                }
            }
        } catch (e: Exception) {}

        // 3. Check if the foreground application is a phone, dialer, or USSD dialog
        try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usageStatsManager != null) {
                val time = System.currentTimeMillis()
                val events = usageStatsManager.queryEvents(time - 15000, time)
                val event = UsageEvents.Event()
                var latestPackage: String? = null
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                        latestPackage = event.packageName
                    }
                }
                if (latestPackage != null && isPhoneOrUssdPackage(latestPackage)) {
                    return true
                }
            }
        } catch (e: Exception) {}

        return false
    }

    /**
     * Safely opens the system phone dialer so the user can make calls,
     * emergency calls, or run USSD codes (*182#, *131#, *123#, etc.).
     */
    fun openDialer(context: Context) {
        try {
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(dialIntent)
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("tel:")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            } catch (e2: Exception) {}
        }
    }
}
