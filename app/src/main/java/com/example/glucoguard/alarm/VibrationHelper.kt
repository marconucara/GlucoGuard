package com.example.glucoguard.alarm

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator

object VibrationHelper {

    // Pattern: wait 0ms, vibrate 900ms, pause 300ms, vibrate 900ms, pause 300ms ...
    // repeat = 0 means loop from index 0 indefinitely
    private val ALARM_PATTERN = longArrayOf(0, 900, 300, 900, 300, 900, 300, 900, 600)
    private const val REPEAT_FROM = 0

    fun start(context: Context) {
        vibrator(context).vibrate(
            VibrationEffect.createWaveform(ALARM_PATTERN, REPEAT_FROM)
        )
    }

    fun stop(context: Context) {
        vibrator(context).cancel()
    }

    @Suppress("DEPRECATION")
    private fun vibrator(context: Context): Vibrator =
        context.getSystemService(Vibrator::class.java)
}
