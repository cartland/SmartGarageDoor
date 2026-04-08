package com.chriscartland.garage.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class LoadingResultTest {
    @Test
    fun loadingDataReturnsValue() {
        val loading = LoadingResult.Loading("test")
        assertEquals("test", loading.data)
    }

    @Test
    fun loadingDataReturnsNullWhenNull() {
        val loading = LoadingResult.Loading<String>(null)
        assertNull(loading.data)
    }

    @Test
    fun completeDataReturnsValue() {
        val complete = LoadingResult.Complete("test")
        assertEquals("test", complete.data)
    }

    @Test
    fun completeDataReturnsNullWhenNull() {
        val complete = LoadingResult.Complete<String>(null)
        assertNull(complete.data)
    }

    @Test
    fun errorDataReturnsNull() {
        val error = LoadingResult.Error(RuntimeException("test error"))
        assertNull(error.data)
    }

    @Test
    fun errorPreservesException() {
        val exception = RuntimeException("test error")
        val error = LoadingResult.Error(exception)
        assertEquals(exception, error.exception)
    }

    @Test
    fun loadingIsCorrectType() {
        val result: LoadingResult<String> = LoadingResult.Loading("data")
        assertIs<LoadingResult.Loading<String>>(result)
    }

    @Test
    fun completeIsCorrectType() {
        val result: LoadingResult<String> = LoadingResult.Complete("data")
        assertIs<LoadingResult.Complete<String>>(result)
    }

    @Test
    fun errorIsCorrectType() {
        val result: LoadingResult<String> = LoadingResult.Error(RuntimeException())
        assertIs<LoadingResult.Error>(result)
    }

    @Test
    fun loadingWithDoorEventPreservesData() {
        val event = DoorEvent(
            doorPosition = DoorPosition.CLOSED,
            message = "The door is closed.",
            lastCheckInTimeSeconds = 1000L,
            lastChangeTimeSeconds = 900L,
        )
        val loading = LoadingResult.Loading(event)
        assertEquals(DoorPosition.CLOSED, loading.data?.doorPosition)
    }

    @Test
    fun completeWithListPreservesData() {
        val events = listOf(
            DoorEvent(doorPosition = DoorPosition.CLOSED),
            DoorEvent(doorPosition = DoorPosition.OPEN),
        )
        val complete = LoadingResult.Complete(events)
        assertEquals(2, complete.data?.size)
    }
}
