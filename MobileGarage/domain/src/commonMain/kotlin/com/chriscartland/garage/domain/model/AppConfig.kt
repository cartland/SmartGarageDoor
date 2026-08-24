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
    /**
     * Which [DoorUpdateStrategyId] this build runs when the user has
     * expressed no preference. Deliberately has NO default value here:
     * the answer differs per platform (Android's push works, iOS's does
     * not yet), and a shared default would be exactly the hardcoded
     * platform decision this class exists to keep out of shared code.
     */
    val defaultDoorUpdateStrategy: DoorUpdateStrategyId,
)
