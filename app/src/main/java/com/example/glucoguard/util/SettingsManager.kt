package com.example.glucoguard.util

import android.content.Context
import android.content.SharedPreferences
import com.example.glucoguard.Config

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("glucoguard_prefs", Context.MODE_PRIVATE)

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
}
