package com.example.glucoguard

object Config {
    const val EMAIL = "user@example.com"
    const val PASSWORD = "REDACTED"
    const val BASE_URL = "https://api-eu.libreview.io"

    const val POLL_INTERVAL_MS = 60_000L // 1 minute

    // Normal thresholds
    const val NORMAL_LOW = 70
    const val NORMAL_HIGH = 170

    // DND thresholds (more relaxed)
    const val DND_LOW = 60
    const val DND_HIGH = 250

    const val SNOOZE_DURATION_MS = 30 * 60 * 1000L // 30 minutes
}
