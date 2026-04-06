package com.chriscartland.garage.domain.model

/**
 * A typed result that distinguishes success from failure with a specific error type.
 *
 * Unlike Kotlin's built-in [kotlin.Result] which uses [Throwable], this uses a sealed
 * error hierarchy so callers handle specific failure cases at compile time.
 *
 * Usage:
 * ```
 * when (val result = repository.fetchDoorEvent()) {
 *     is AppResult.Success -> handleEvent(result.data)
 *     is AppResult.Error -> handleError(result.error)
 * }
 * ```
 */
sealed interface AppResult<out D, out E : AppError> {
    data class Success<D>(
        val data: D,
    ) : AppResult<D, Nothing>

    data class Error<E : AppError>(
        val error: E,
    ) : AppResult<Nothing, E>
}

/**
 * Base interface for all application errors.
 */
interface AppError {
    val message: String
    val cause: Throwable?
        get() = null
}

/**
 * Data layer errors — network, database, configuration.
 */
sealed interface DataError : AppError {
    sealed interface Network : DataError {
        data class ConnectionFailed(
            override val message: String = "Connection failed",
            override val cause: Throwable? = null,
        ) : Network

        data class Timeout(
            override val message: String = "Request timed out",
            override val cause: Throwable? = null,
        ) : Network

        data class ServerError(
            val httpCode: Int,
            override val message: String = "Server error ($httpCode)",
            override val cause: Throwable? = null,
        ) : Network

        data class NotReady(
            override val message: String = "Server config not loaded",
        ) : Network
    }

    data class Unknown(
        override val message: String = "Unknown error",
        override val cause: Throwable? = null,
    ) : DataError
}

/**
 * Authentication errors.
 */
sealed interface AuthError : AppError {
    data class NotAuthenticated(
        override val message: String = "Not authenticated",
    ) : AuthError

    data class TokenExpired(
        override val message: String = "Token expired",
    ) : AuthError

    data class SignInFailed(
        override val message: String = "Sign-in failed",
        override val cause: Throwable? = null,
    ) : AuthError
}

// --- Extension functions ---

fun <D, E : AppError> AppResult<D, E>.getOrNull(): D? =
    when (this) {
        is AppResult.Success -> data
        is AppResult.Error -> null
    }

fun <D, E : AppError> AppResult<D, E>.getOrElse(default: (E) -> D): D =
    when (this) {
        is AppResult.Success -> data
        is AppResult.Error -> default(error)
    }

fun <D, E : AppError, R> AppResult<D, E>.map(transform: (D) -> R): AppResult<R, E> =
    when (this) {
        is AppResult.Success -> AppResult.Success(transform(data))
        is AppResult.Error -> this
    }

fun <D, E : AppError, F : AppError> AppResult<D, E>.mapError(transform: (E) -> F): AppResult<D, F> =
    when (this) {
        is AppResult.Success -> this
        is AppResult.Error -> AppResult.Error(transform(error))
    }

fun <D, E : AppError> AppResult<D, E>.onSuccess(action: (D) -> Unit): AppResult<D, E> {
    if (this is AppResult.Success) action(data)
    return this
}

fun <D, E : AppError> AppResult<D, E>.onError(action: (E) -> Unit): AppResult<D, E> {
    if (this is AppResult.Error) action(error)
    return this
}
