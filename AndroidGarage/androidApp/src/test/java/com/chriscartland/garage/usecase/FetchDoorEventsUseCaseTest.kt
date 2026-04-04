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

package com.chriscartland.garage.usecase

import com.chriscartland.garage.testcommon.FakeDoorRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FetchDoorEventsUseCaseTest {
    private lateinit var fakeDoor: FakeDoorRepository
    private lateinit var fetchCurrent: FetchCurrentDoorEventUseCase
    private lateinit var fetchRecent: FetchRecentDoorEventsUseCase

    @Before
    fun setup() {
        fakeDoor = FakeDoorRepository()
        fetchCurrent = FetchCurrentDoorEventUseCase(fakeDoor)
        fetchRecent = FetchRecentDoorEventsUseCase(fakeDoor)
    }

    @Test
    fun fetchCurrentDelegatesToRepository() =
        runTest {
            fetchCurrent()
            assertEquals(1, fakeDoor.fetchCurrentDoorEventCount)
        }

    @Test
    fun fetchCurrentCanBeCalledMultipleTimes() =
        runTest {
            fetchCurrent()
            fetchCurrent()
            fetchCurrent()
            assertEquals(3, fakeDoor.fetchCurrentDoorEventCount)
        }

    @Test
    fun fetchRecentDelegatesToRepository() =
        runTest {
            fetchRecent()
            assertEquals(1, fakeDoor.fetchRecentDoorEventsCount)
        }

    @Test
    fun fetchCurrentAndRecentAreIndependent() =
        runTest {
            fetchCurrent()
            fetchRecent()
            assertEquals(1, fakeDoor.fetchCurrentDoorEventCount)
            assertEquals(1, fakeDoor.fetchRecentDoorEventsCount)
        }
}
