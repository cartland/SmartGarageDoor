package com.chriscartland.garage.repository

sealed class Result<out T> {
    data class Loading<out T>(val data: T?) : Result<T>()
    data class Complete<out T>(val data: T?) : Result<T>()
    data class Error(val exception: Throwable) : Result<Nothing>()
}

fun <T> Result<T>.dataOrNull(): T? {
    return when (this) {
        is Result.Error -> null
        is Result.Loading -> this.data
        is Result.Complete -> this.data
    }
}
