package com.glucoguard.app.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.glucoguard.app.Config

class SettingsManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "glucoguard_encrypted_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // Bridge for migration if needed, but for now we just use the encrypted one.
    // In a real app, you might check if old prefs exist and migrate them here.

    var email: String
        get() = prefs.getString("email", "") ?: ""
        set(value) = prefs.edit().putString("email", value).apply()

    var password: String
        get() = prefs.getString("password", "") ?: ""
        set(value) = prefs.edit().putString("password", value).apply()

    var normalLow: Int
        get() = prefs.getInt("normal_low", Config.DEFAULT_NORMAL_LOW)
        set(value) = prefs.edit().putInt("normal_low", value).apply()

    var normalHigh: Int
        get() = prefs.getInt("normal_high", Config.DEFAULT_NORMAL_HIGH)
        set(value) = prefs.edit().putInt("normal_high", value).apply()

    var dndLow: Int
        get() = prefs.getInt("dnd_low", Config.DEFAULT_DND_LOW)
        set(value) = prefs.edit().putInt("dnd_low", value).apply()

    var dndHigh: Int
        get() = prefs.getInt("dnd_high", Config.DEFAULT_DND_HIGH)
        set(value) = prefs.edit().putInt("dnd_high", value).apply()

    var disclaimerAccepted: Boolean
        get() = prefs.getBoolean("disclaimer_accepted", false)
        set(value) = prefs.edit().putBoolean("disclaimer_accepted", value).apply()

    var lastSuccessfulPollTimestamp: Long
        get() = prefs.getLong("last_poll_ts", 0L)
        set(value) = prefs.edit().putLong("last_poll_ts", value).apply()

    var noDataThresholdMin: Int
        get() = prefs.getInt("no_data_threshold", Config.DEFAULT_NO_DATA_THRESHOLD_MIN)
        set(value) = prefs.edit().putInt("no_data_threshold", value).apply()

    var noDataSnoozeUntil: Long
        get() = prefs.getLong("no_data_snooze_until", 0L)
        set(value) = prefs.edit().putLong("no_data_snooze_until", value).apply()

    var glucoseSnoozeUntil: Long
        get() = prefs.getLong("glucose_snooze_until", 0L)
        set(value) = prefs.edit().putLong("glucose_snooze_until", value).apply()

    // Persistent Alarm State for reliability after restarts
    var alarmActive: Boolean
        get() = prefs.getBoolean("alarm_active", false)
        set(value) = prefs.edit().putBoolean("alarm_active", value).apply()

    var noDataAlarmActive: Boolean
        get() = prefs.getBoolean("no_data_alarm_active", false)
        set(value) = prefs.edit().putBoolean("no_data_alarm_active", value).apply()

    var lastAlarmValue: Int
        get() = prefs.getInt("last_alarm_value", 0)
        set(value) = prefs.edit().putInt("last_alarm_value", value).apply()

    var lastAlarmIsLow: Boolean
        get() = prefs.getBoolean("last_alarm_is_low", false)
        set(value) = prefs.edit().putBoolean("last_alarm_is_low", value).apply()
}
