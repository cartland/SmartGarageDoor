package com.chriscartland.garage.domain.model

/**
 * Errors that can occur when performing authenticated actions
 * (push button, snooze notifications).
 *
 * Sealed type enables exhaustive `when` handling — adding a new variant
 * forces all callers to handle it at compile time.
 */
sealed interface ActionError : AppError {
    /** User is not authenticated. */
    data object NotAuthenticated : ActionError {
        override val message: String = "Not authenticated"
    }

    /** Required data is missing (e.g., no door event for snooze timestamp). */
    data object MissingData : ActionError {
        override val message: String = "Required data not available"
    }

    /** Network call failed (connection failure, or any HTTP status but 403). */
    data object NetworkFailed : ActionError {
        override val message: String = "Network request failed"
    }

    /**
     * The server answered and refused this account (HTTP 403): the email is
     * not on the allowlist, or the push key does not match. Distinct from
     * [NotAuthenticated], which is the LOCAL gate (no session) and never
     * reaches the network. A refusal is a verdict, not a fault: retrying
     * cannot change it, so every surface words it as "Not allowed for this
     * account" rather than as a server error that will pass (strategy 2.6).
     */
    data object Forbidden : ActionError {
        override val message: String = "Not allowed for this account"
    }

    /**
     * Snooze-specific: the server rejected the request because the
     * `snoozeEventTimestamp` the client sent no longer matches the
     * server's current door event. Happens when the door state changes
     * between the time the user opens the snooze sheet and the time
     * they tap Save — very common during `OPENING`/`CLOSING` transitions
     * because the ESP32-poll-triggered promotion to `OpeningTooLong` /
     * `ClosingTooLong` writes a new event with a new timestamp every
     * 60 seconds of stuck-in-motion. Surfaced as HTTP 404 from the
     * server's snooze submit handler. See `docs/SNOOZE_BEHAVIOR.md`.
     *
     * The variant is in this shared error type (rather than its own
     * snooze-specific error type) for the same reason `NotAuthenticated`
     * and `MissingData` are: the cost of a per-domain error type would
     * exceed the readability benefit at the current scale (2 actions).
     */
    data object SnoozeEventChanged : ActionError {
        override val message: String = "Door state changed before snooze could apply"
    }
}
