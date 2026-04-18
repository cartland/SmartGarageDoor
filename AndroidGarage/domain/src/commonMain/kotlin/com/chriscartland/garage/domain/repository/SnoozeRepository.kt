package com.chriscartland.garage.domain.repository

import com.chriscartland.garage.domain.model.SnoozeState
import kotlinx.coroutines.flow.Flow

/**
 * Manages snooze-notification state for the garage door.
 *
 * Snoozing suppresses open-door notifications for a specified duration.
 * The snooze end time is persisted server-side and fetched on demand.
 */
interface SnoozeRepository {
    /** Observation: snooze state changes over time. */
    fun observeSnoozeState(): Flow<SnoozeState>

    suspend fun fetchSnoozeStatus()

    /**
     * Send a snooze request. Returns true on success, false on any failure
     * (server config missing, HTTP error, connection failure).
     *
     * TODO: return [com.chriscartland.garage.domain.model.AppResult]
     * `<Unit, ActionError>` instead of `Boolean`. Boolean collapses
     * NotAuthenticated / MissingData / NetworkFailed into one signal; the
     * UseCase above it already discriminates via AppResult and the UI shows
     * different messages per case. Surfacing ActionError here lets the
     * repository carry that signal end-to-end without reconstruction.
     *
     * TODO: replace [snoozeDurationHours]: String with a domain value type
     * (e.g. SnoozeDurationServerOption). The "0h".."12h" encoding is
     * stringly-typed and repeats the validation from the server; a sealed
     * enum would make invalid durations impossible at the call site.
     */
    suspend fun snoozeNotifications(
        snoozeDurationHours: String,
        idToken: String,
        snoozeEventTimestampSeconds: Long,
    ): Boolean
}
