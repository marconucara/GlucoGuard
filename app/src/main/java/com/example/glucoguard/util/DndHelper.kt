package com.example.glucoguard.util

import android.app.NotificationManager
import android.content.Context

object DndHelper {

    /** Returns true if DND is active (use relaxed thresholds). */
    fun isDndActive(context: Context): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java)
        return when (nm.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_NONE,
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> true
            else -> false
        }
    }
}
