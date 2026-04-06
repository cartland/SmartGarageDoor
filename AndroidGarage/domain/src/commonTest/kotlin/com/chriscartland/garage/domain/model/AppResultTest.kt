package com.chriscartland.garage.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppResultTest {
    @Test
    fun successContainsData() {
        val result: AppResult<String, DataError> = AppResult.Success("hello")
        assertIs<AppResult.Success<String>>(result)
        assertEquals("hello", result.data)
    }

    @Test
    fun errorContainsTypedError() {
        val result: AppResult<String, DataError.Network> =
            AppResult.Error(DataError.Network.ConnectionFailed())
        assertIs<AppResult.Error<DataError.Network.ConnectionFailed>>(result)
        assertEquals("Connection failed", result.error.message)
    }

    @Test
    fun getOrNullReturnsDataOnSuccess() {
        val result: AppResult<Int, DataError> = AppResult.Success(42)
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun getOrNullReturnsNullOnError() {
        val result: AppResult<Int, DataError> =
            AppResult.Error(DataError.Unknown("oops"))
        assertNull(result.getOrNull())
    }

    @Test
    fun getOrElseReturnsFallbackOnError() {
        val result: AppResult<Int, DataError> =
            AppResult.Error(DataError.Unknown("oops"))
        assertEquals(-1, result.getOrElse { -1 })
    }

    @Test
    fun mapTransformsSuccessData() {
        val result: AppResult<Int, DataError> = AppResult.Success(10)
        val mapped = result.map { it * 2 }
        assertEquals(20, mapped.getOrNull())
    }

    @Test
    fun mapPreservesError() {
        val result: AppResult<Int, DataError> =
            AppResult.Error(DataError.Unknown("fail"))
        val mapped = result.map { it * 2 }
        assertNull(mapped.getOrNull())
    }

    @Test
    fun onSuccessCallsActionOnSuccess() {
        var called = false
        val result: AppResult<String, DataError> = AppResult.Success("ok")
        result.onSuccess { called = true }
        assertTrue(called)
    }

    @Test
    fun onErrorCallsActionOnError() {
        var called = false
        val result: AppResult<String, DataError> =
            AppResult.Error(DataError.Unknown())
        result.onError { called = true }
        assertTrue(called)
    }

    @Test
    fun serverErrorIncludesHttpCode() {
        val error = DataError.Network.ServerError(httpCode = 500)
        assertEquals(500, error.httpCode)
        assertEquals("Server error (500)", error.message)
    }

    @Test
    fun authErrorHierarchy() {
        val notAuth: AppError = AuthError.NotAuthenticated()
        assertIs<AuthError>(notAuth)
        assertEquals("Not authenticated", notAuth.message)
    }
}
