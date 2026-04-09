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

    /** Network call failed (HTTP error or connection failure). */
    data object NetworkFailed : ActionError {
        override val message: String = "Network request failed"
    }
}
