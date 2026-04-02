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

import android.app.Activity
import com.chriscartland.garage.applogger.AppLoggerRepository
import com.chriscartland.garage.fcm.DoorFcmRepository
import com.chriscartland.garage.fcm.DoorFcmState
import com.chriscartland.garage.fcm.DoorFcmTopic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class DoorViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var appLoggerRepository: AppLoggerRepository
    private lateinit var doorRepository: DoorRepository
    private lateinit var doorFcmRepository: DoorFcmRepository

    private val testDoorEvent =
        DoorEvent(
            doorPosition = DoorPosition.CLOSED,
            message = "The door is closed.",
            lastCheckInTimeSeconds = 1000L,
            lastChangeTimeSeconds = 900L,
        )

    private lateinit var currentDoorEventFlow: MutableStateFlow<DoorEvent>
    private lateinit var recentDoorEventsFlow: MutableStateFlow<List<DoorEvent>>
    private lateinit var currentDoorPositionFlow: MutableStateFlow<DoorPosition>

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        appLoggerRepository = mock(AppLoggerRepository::class.java)
        doorFcmRepository = mock(DoorFcmRepository::class.java)

        currentDoorEventFlow = MutableStateFlow(testDoorEvent)
        recentDoorEventsFlow = MutableStateFlow(listOf(testDoorEvent))
        currentDoorPositionFlow = MutableStateFlow(DoorPosition.CLOSED)

        doorRepository = mock(DoorRepository::class.java)
        `when`(doorRepository.currentDoorEvent).thenReturn(currentDoorEventFlow)
        `when`(doorRepository.recentDoorEvents).thenReturn(recentDoorEventsFlow)
        `when`(doorRepository.currentDoorPosition).thenReturn(currentDoorPositionFlow)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): DoorViewModelImpl =
        DoorViewModelImpl(appLoggerRepository, doorRepository, doorFcmRepository)

    @Test
    fun initialFcmRegistrationStatusIsUnknown() {
        val viewModel = createViewModel()

        assertEquals(FcmRegistrationStatus.UNKNOWN, viewModel.fcmRegistrationStatus.value)
    }

    @Test
    fun collectsCurrentDoorEventFromRepository() =
        runTest {
            val viewModel = createViewModel()

            // Allow IO coroutines to process
            testDispatcher.scheduler.advanceUntilIdle()

            val result = viewModel.currentDoorEvent.value
            assertTrue("Should be Complete after collection", result is LoadingResult.Complete)
            assertEquals(testDoorEvent, result.data)
        }

    @Test
    fun collectsUpdatedDoorEventFromRepository() =
        runTest {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val updatedEvent =
                DoorEvent(
                    doorPosition = DoorPosition.OPEN,
                    message = "The door is open.",
                    lastCheckInTimeSeconds = 2000L,
                    lastChangeTimeSeconds = 1900L,
                )
            currentDoorEventFlow.value = updatedEvent
            testDispatcher.scheduler.advanceUntilIdle()

            val result = viewModel.currentDoorEvent.value
            assertTrue("Should be Complete", result is LoadingResult.Complete)
            assertEquals(updatedEvent, result.data)
        }

    @Test
    fun collectsRecentDoorEventsFromRepository() =
        runTest {
            val events =
                listOf(
                    testDoorEvent,
                    DoorEvent(doorPosition = DoorPosition.OPEN, lastChangeTimeSeconds = 800L),
                )
            recentDoorEventsFlow.value = events

            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val result = viewModel.recentDoorEvents.value
            assertTrue("Should be Complete after collection", result is LoadingResult.Complete)
            assertEquals(2, result.data?.size)
        }

    @Test
    fun fetchCurrentDoorEventCallsRepository() =
        runTest {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.fetchCurrentDoorEvent()
            testDispatcher.scheduler.advanceUntilIdle()

            verify(doorRepository).fetchCurrentDoorEvent()
        }

    @Test
    fun fetchRecentDoorEventsCallsRepository() =
        runTest {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.fetchRecentDoorEvents()
            testDispatcher.scheduler.advanceUntilIdle()

            verify(doorRepository).fetchRecentDoorEvents()
        }

    @Test
    fun fetchFcmRegistrationStatusMapsRegisteredState() =
        runTest {
            val viewModel = createViewModel()
            val activity = mock(Activity::class.java)

            `when`(doorFcmRepository.fetchStatus(activity))
                .thenReturn(DoorFcmState.Registered(topic = DoorFcmTopic("test-topic")))

            viewModel.fetchFcmRegistrationStatus(activity)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(FcmRegistrationStatus.REGISTERED, viewModel.fcmRegistrationStatus.value)
        }

    @Test
    fun fetchFcmRegistrationStatusMapsNotRegisteredState() =
        runTest {
            val viewModel = createViewModel()
            val activity = mock(Activity::class.java)

            `when`(doorFcmRepository.fetchStatus(activity))
                .thenReturn(DoorFcmState.NotRegistered)

            viewModel.fetchFcmRegistrationStatus(activity)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(
                FcmRegistrationStatus.NOT_REGISTERED,
                viewModel.fcmRegistrationStatus.value,
            )
        }

    @Test
    fun fetchFcmRegistrationStatusMapsUnknownState() =
        runTest {
            val viewModel = createViewModel()
            val activity = mock(Activity::class.java)

            `when`(doorFcmRepository.fetchStatus(activity))
                .thenReturn(DoorFcmState.Unknown)

            viewModel.fetchFcmRegistrationStatus(activity)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(FcmRegistrationStatus.UNKNOWN, viewModel.fcmRegistrationStatus.value)
        }
}
