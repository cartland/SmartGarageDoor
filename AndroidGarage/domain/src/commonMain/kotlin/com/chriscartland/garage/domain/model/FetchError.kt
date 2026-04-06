package com.chriscartland.garage.domain.model

/**
 * Errors that can occur when fetching door events from the server.
 *
 * Sealed type enables exhaustive `when` handling — adding a new variant
 * forces all callers to handle it at compile time.
 */
sealed interface FetchError : AppError {
    /** Server configuration has not been loaded yet. */
    data object NotReady : FetchError {
        override val message: String = "Server config not loaded"
    }

    /** Network request failed or returned no data. */
    data object NetworkFailed : FetchError {
        override val message: String = "Network request failed"
    }
}
