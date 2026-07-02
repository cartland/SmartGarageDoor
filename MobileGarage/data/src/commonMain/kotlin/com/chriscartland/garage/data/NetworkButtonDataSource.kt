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

    /**
     * Submits a snooze request. On success, returns the authoritative
     * `snoozeEndTimeSeconds` from the server response so callers can update
     * state without a follow-up GET.
     */
    suspend fun snoozeNotifications(
        buildTimestamp: String,
        remoteButtonPushKey: String,
        idToken: String,
        snoozeDurationHours: String,
        snoozeEventTimestampSeconds: Long,
    ): NetworkResult<Long>

    suspend fun fetchSnoozeEndTimeSeconds(buildTimestamp: String): NetworkResult<Long>
}
