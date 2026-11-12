package com.glucoguard.app.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat

object ChargingHelper {

    /** True when the watch is on a charger, even if the battery is already full. */
    fun isOnCharger(context: Context): Boolean {
        val battery = ContextCompat.registerReceiver(
            context, null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        return (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    }

    /** True when the user chose to silence alarms while charging and the watch is on a charger. */
    fun alarmsSilenced(context: Context, settingsManager: SettingsManager): Boolean =
        settingsManager.silenceAlarmsWhileCharging && isOnCharger(context)

    /** Filter for the broadcasts sent when the charger is plugged in or removed. */
    fun powerChangeFilter() = IntentFilter().apply {
        addAction(Intent.ACTION_POWER_CONNECTED)
        addAction(Intent.ACTION_POWER_DISCONNECTED)
    }
}
