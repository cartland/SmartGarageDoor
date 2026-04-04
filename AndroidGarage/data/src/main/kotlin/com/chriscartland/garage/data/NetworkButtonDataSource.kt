package com.chriscartland.garage.data

/**
 * Pure data source interface for remote button and snooze operations.
 *
 * Implementations handle HTTP communication. The Repository layer
 * never sees HTTP response codes or network DTOs.
 */
interface NetworkButtonDataSource {
    /**
     * Send a remote button push request.
     *
     * @return true if the server acknowledged the request
     */
    suspend fun pushButton(
        remoteButtonBuildTimestamp: String,
        buttonAckToken: String,
        remoteButtonPushKey: String,
        idToken: String,
    ): Boolean

    /**
     * Request to snooze open-door notifications.
     *
     * @return true if the server acknowledged, false on error
     */
    suspend fun snoozeNotifications(
        buildTimestamp: String,
        remoteButtonPushKey: String,
        idToken: String,
        snoozeDurationHours: String,
        snoozeEventTimestampSeconds: Long,
    ): Boolean

    /**
     * Fetch the current snooze end time.
     *
     * @return Snooze end time in epoch seconds, or 0 if not snoozing
     */
    suspend fun fetchSnoozeEndTimeSeconds(buildTimestamp: String): Long
}
