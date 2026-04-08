package com.chriscartland.garage.domain.model

/**
 * Application configuration.
 *
 * All values come from platform-specific build config (BuildConfig on Android,
 * Info.plist on iOS) — nothing is hardcoded in shared code.
 */
data class AppConfig(
    val baseUrl: String,
    val recentEventCount: Int,
    val serverConfigKey: String,
    val snoozeNotificationsOption: Boolean,
    val remoteButtonPushEnabled: Boolean,
    val logSummary: Boolean,
)
