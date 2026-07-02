package com.chriscartland.garage.domain.model

sealed class LoadingResult<out T> {
    data class Loading<out T>(
        internal val d: T?,
    ) : LoadingResult<T>()

    data class Complete<out T>(
        internal val d: T?,
    ) : LoadingResult<T>()

    data class Error(
        val exception: Throwable,
    ) : LoadingResult<Nothing>()

    val data: T?
        get() =
            when (this) {
                is Loading -> this.d
                is Complete -> this.d
                is Error -> null
            }
}
