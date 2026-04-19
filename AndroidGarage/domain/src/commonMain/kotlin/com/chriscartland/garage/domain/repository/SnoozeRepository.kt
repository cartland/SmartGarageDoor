package com.chriscartland.garage.domain.repository

import com.chriscartland.garage.domain.model.ActionError
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.SnoozeState
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages snooze-notification state for the garage door.
 *
 * Snoozing suppresses open-door notifications for a specified duration.
 * The snooze end time is persisted server-side and fetched on demand.
 *
 * State ownership (ADR-022): the repository owns the authoritative
 * `StateFlow<SnoozeState>` and an always-on collector populates it from the
 * network on construction. ViewModels and UseCases expose this same
 * [StateFlow] by reference — no mirrors.
 */
interface SnoozeRepository {
    /** Observation: the authoritative snooze state as an owned [StateFlow]. */
    val snoozeState: StateFlow<SnoozeState>

    suspend fun fetchSnoozeStatus()

    /**
     * Send a snooze request. On success returns the new [SnoozeState]
     * computed from the server's authoritative response — the same value
     * is simultaneously written to [snoozeState].
     *
     * Callers can use the returned state directly or rely on [snoozeState]
     * for broader reactivity.
     *
     * Returns [AppResult.Error] with [ActionError.NetworkFailed] on any
     * network-layer failure. Auth/input errors (NotAuthenticated,
     * MissingData) are not produced here — they're the UseCase's concern.
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
    ): AppResult<SnoozeState, ActionError>
}
