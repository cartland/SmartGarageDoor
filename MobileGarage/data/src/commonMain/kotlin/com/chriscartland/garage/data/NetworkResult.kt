package com.chriscartland.garage.data

/**
 * Typed result from network data sources.
 *
 * Replaces nullable returns and Boolean success flags so callers can
 * distinguish between success, HTTP errors, and connection failures
 * using exhaustive `when` (no `else` branch).
 *
 * Caught at the data source boundary — Ktor/HTTP exceptions are
 * converted here, never propagated to repositories.
 */
sealed interface NetworkResult<out T> {
    /** Request succeeded with data. */
    data class Success<T>(
        val data: T,
    ) : NetworkResult<T>

    /** Server responded with a non-success HTTP status code. */
    data class HttpError(
        val code: Int,
    ) : NetworkResult<Nothing>

    /** Network request failed (timeout, DNS, connection refused, etc.). */
    data object ConnectionFailed : NetworkResult<Nothing>
}
