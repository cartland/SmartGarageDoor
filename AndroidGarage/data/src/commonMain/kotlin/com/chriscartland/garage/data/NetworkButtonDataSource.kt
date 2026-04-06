package com.chriscartland.garage.data

/**
 * Data source interface for remote button and snooze operations.
 *
 * Returns [NetworkResult] so callers handle errors with exhaustive `when`.
 */
interface NetworkButtonDataSource {
    suspend fun pushButton(
        remoteButtonBuildTimestamp: String,
        buttonAckToken: String,
        remoteButtonPushKey: String,
        idToken: String,
    ): NetworkResult<Unit>

    suspend fun snoozeNotifications(
        buildTimestamp: String,
        remoteButtonPushKey: String,
        idToken: String,
        snoozeDurationHours: String,
        snoozeEventTimestampSeconds: Long,
    ): NetworkResult<Unit>

    suspend fun fetchSnoozeEndTimeSeconds(buildTimestamp: String): NetworkResult<Long>
}
