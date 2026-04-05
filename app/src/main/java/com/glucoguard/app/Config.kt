package com.glucoguard.app

object Config {
    const val BASE_URL = "https://api-eu.libreview.io"
    const val POLL_INTERVAL_MS = 60_000L // 1 minute
    const val SNOOZE_DURATION_MS = 30 * 60 * 1000L // 30 minutes

    // Default thresholds
    const val DEFAULT_NORMAL_LOW = 70
    const val DEFAULT_NORMAL_HIGH = 180
    const val DEFAULT_DND_LOW = 60
    const val DEFAULT_DND_HIGH = 250
}
