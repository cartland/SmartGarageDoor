/*
 * Copyright 2024 Chris Cartland. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.chriscartland.garage.door

import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.LoadingResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
        assertTrue(result is LoadingResult.Loading)
    }

    @Test
    fun completeIsCorrectType() {
        val result: LoadingResult<String> = LoadingResult.Complete("data")
        assertTrue(result is LoadingResult.Complete)
    }

    @Test
    fun errorIsCorrectType() {
        val result: LoadingResult<String> = LoadingResult.Error(RuntimeException())
        assertTrue(result is LoadingResult.Error)
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
