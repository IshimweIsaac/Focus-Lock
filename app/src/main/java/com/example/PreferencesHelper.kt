package com.example

import android.content.Context
import android.content.SharedPreferences

class PreferencesHelper(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("FocusLockPrefs", Context.MODE_PRIVATE)


    fun unlockAll() {
        val editor = prefs.edit()
        editor.putBoolean("manualLockActive", false)
        editor.putLong("manualLockEndTimeMillis", 0L)
        editor.putBoolean("isLockEnabled", false)
        editor.putBoolean("midDayLockEnabled", false)
        editor.putLong("quickLockEndTimeMillis", 0L)
        editor.putLong("allowSettingsUntilMillis", System.currentTimeMillis() + 600_000L)
        val allKeys = prefs.all.keys
        for (key in allKeys) {
            if (key.startsWith("lock_exp_") || key.startsWith("session_usage_")) {
                editor.remove(key)
            }
        }
        editor.apply()
    }

    // Manual immediate lock (direct user switch)
    var manualLockActive: Boolean
        get() = prefs.getBoolean("manualLockActive", false)
        set(value) = prefs.edit().putBoolean("manualLockActive", value).apply()

    var manualLockEndTimeMillis: Long
        get() = prefs.getLong("manualLockEndTimeMillis", 0L)
        set(value) = prefs.edit().putLong("manualLockEndTimeMillis", value).apply()

    var isLockEnabled: Boolean
        get() = prefs.getBoolean("isLockEnabled", false)
        set(value) = prefs.edit().putBoolean("isLockEnabled", value).apply()

    var startHour: Int
        get() = prefs.getInt("startHour", 21)
        set(value) = prefs.edit().putInt("startHour", value).apply()

    var startMinute: Int
        get() = prefs.getInt("startMinute", 0)
        set(value) = prefs.edit().putInt("startMinute", value).apply()

    var endHour: Int
        get() = prefs.getInt("endHour", 7)
        set(value) = prefs.edit().putInt("endHour", value).apply()

    var endMinute: Int
        get() = prefs.getInt("endMinute", 0)
        set(value) = prefs.edit().putInt("endMinute", value).apply()

    var midDayLockEnabled: Boolean
        get() = prefs.getBoolean("midDayLockEnabled", false)
        set(value) = prefs.edit().putBoolean("midDayLockEnabled", value).apply()

    var midDayStartHour: Int
        get() = prefs.getInt("midDayStartHour", 9)
        set(value) = prefs.edit().putInt("midDayStartHour", value).apply()

    var midDayStartMinute: Int
        get() = prefs.getInt("midDayStartMinute", 0)
        set(value) = prefs.edit().putInt("midDayStartMinute", value).apply()

    var midDayEndHour: Int
        get() = prefs.getInt("midDayEndHour", 17)
        set(value) = prefs.edit().putInt("midDayEndHour", value).apply()

    var midDayEndMinute: Int
        get() = prefs.getInt("midDayEndMinute", 0)
        set(value) = prefs.edit().putInt("midDayEndMinute", value).apply()

    var youtubeUsageDate: String
        get() = prefs.getString("youtubeUsageDate", "") ?: ""
        set(value) = prefs.edit().putString("youtubeUsageDate", value).apply()

    var youtubeUsageMillis: Long
        get() = prefs.getLong("youtubeUsageMillis", 0L)
        set(value) = prefs.edit().putLong("youtubeUsageMillis", value).apply()

    var quickLockEndTimeMillis: Long
        get() = prefs.getLong("quickLockEndTimeMillis", 0L)
        set(value) = prefs.edit().putLong("quickLockEndTimeMillis", value).apply()

    var allowSettingsUntilMillis: Long
        get() = prefs.getLong("allowSettingsUntilMillis", 0L)
        set(value) = prefs.edit().putLong("allowSettingsUntilMillis", value).apply()

    var allowDocumentPickerUntilMillis: Long
        get() = prefs.getLong("allowDocumentPickerUntilMillis", 0L)
        set(value) = prefs.edit().putLong("allowDocumentPickerUntilMillis", value).apply()

    var lastOpenedPdfId: String
        get() = prefs.getString("lastOpenedPdfId", "") ?: ""
        set(value) = prefs.edit().putString("lastOpenedPdfId", value).apply()

    fun getAppLockExpiration(packageName: String): Long {
        return prefs.getLong("lock_exp_$packageName", 0L)
    }

    fun setAppLockExpiration(packageName: String, timeMillis: Long) {
        prefs.edit().putLong("lock_exp_$packageName", timeMillis).apply()
    }

    fun getAppSessionUsage(packageName: String): Long {
        return prefs.getLong("session_usage_$packageName", 0L)
    }

    fun setAppSessionUsage(packageName: String, usageMillis: Long) {
        prefs.edit().putLong("session_usage_$packageName", usageMillis).apply()
    }

    fun getAppLastUsed(packageName: String): Long {
        return prefs.getLong("last_used_$packageName", 0L)
    }

    fun setAppLastUsed(packageName: String, timeMillis: Long) {
        prefs.edit().putLong("last_used_$packageName", timeMillis).apply()
    }

    fun isLockTimeActive(): Boolean {
        val now = System.currentTimeMillis()

        // 1. Manual immediate lock has highest priority
        if (manualLockActive) {
            if (manualLockEndTimeMillis == 0L || now < manualLockEndTimeMillis) {
                return true
            } else {
                manualLockActive = false
            }
        }

        // 2. Quick lock
        if (now < quickLockEndTimeMillis) {
            return true
        }

        // 3. Auto schedules (only checked if enabled by user)
        if (!isLockEnabled && !midDayLockEnabled) return false

        val rightNow = java.util.Calendar.getInstance()
        val currentTotalMinutes = rightNow.get(java.util.Calendar.HOUR_OF_DAY) * 60 + rightNow.get(java.util.Calendar.MINUTE)

        var isOvernightActive = false
        if (isLockEnabled) {
            val startTotalMinutes = startHour * 60 + startMinute
            val endTotalMinutes = endHour * 60 + endMinute
            isOvernightActive = if (startTotalMinutes <= endTotalMinutes) {
                currentTotalMinutes in startTotalMinutes until endTotalMinutes
            } else {
                currentTotalMinutes >= startTotalMinutes || currentTotalMinutes < endTotalMinutes
            }
        }

        var isMidDayActive = false
        if (midDayLockEnabled) {
            val midStartTotal = midDayStartHour * 60 + midDayStartMinute
            val midEndTotal = midDayEndHour * 60 + midDayEndMinute
            isMidDayActive = if (midStartTotal <= midEndTotal) {
                currentTotalMinutes in midStartTotal until midEndTotal
            } else {
                currentTotalMinutes >= midStartTotal || currentTotalMinutes < midEndTotal
            }
        }

        return isOvernightActive || isMidDayActive
    }
}
