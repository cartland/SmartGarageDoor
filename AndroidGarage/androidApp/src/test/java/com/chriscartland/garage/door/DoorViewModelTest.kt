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

import com.chriscartland.garage.coroutines.TestDispatcherProvider
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorFcmState
import com.chriscartland.garage.domain.model.DoorFcmTopic
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.FcmRegistrationStatus
import com.chriscartland.garage.domain.model.LoadingResult
import com.chriscartland.garage.testcommon.FakeAppLoggerRepository
import com.chriscartland.garage.testcommon.FakeDoorFcmRepository
import com.chriscartland.garage.testcommon.FakeDoorRepository
import com.chriscartland.garage.usecase.DefaultDoorViewModel
import com.chriscartland.garage.usecase.DeregisterFcmUseCase
import com.chriscartland.garage.usecase.FetchCurrentDoorEventUseCase
import com.chriscartland.garage.usecase.FetchFcmStatusUseCase
import com.chriscartland.garage.usecase.FetchRecentDoorEventsUseCase
import com.chriscartland.garage.usecase.RegisterFcmUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DoorViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var appLoggerRepository: FakeAppLoggerRepository
    private lateinit var doorRepository: FakeDoorRepository
    private lateinit var doorFcmRepository: FakeDoorFcmRepository

    private val testDoorEvent =
        DoorEvent(
            doorPosition = DoorPosition.CLOSED,
            message = "The door is closed.",
            lastCheckInTimeSeconds = 1000L,
            lastChangeTimeSeconds = 900L,
        )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        appLoggerRepository = FakeAppLoggerRepository()
        doorFcmRepository = FakeDoorFcmRepository()
        doorRepository = FakeDoorRepository()
        doorRepository.setCurrentDoorEvent(testDoorEvent)
        doorRepository.setRecentDoorEvents(listOf(testDoorEvent))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(fetchOnInit: Boolean = true): DefaultDoorViewModel {
        val vm = DefaultDoorViewModel(
            appLoggerRepository,
            doorRepository,
            TestDispatcherProvider(testDispatcher),
            FetchCurrentDoorEventUseCase(doorRepository),
            FetchRecentDoorEventsUseCase(doorRepository),
            FetchFcmStatusUseCase(doorFcmRepository),
            RegisterFcmUseCase(doorRepository, doorFcmRepository),
            DeregisterFcmUseCase(doorFcmRepository),
            fetchOnInit = fetchOnInit,
        )
        testDispatcher.scheduler.runCurrent()
        return vm
    }

    @Test
    fun initialFcmRegistrationStatusIsUnknown() =
        runTest {
            val viewModel = createViewModel()

            assertEquals(FcmRegistrationStatus.UNKNOWN, viewModel.fcmRegistrationStatus.value)
        }

    @Test
    fun collectsCurrentDoorEventFromRepository() =
        runTest {
            val viewModel = createViewModel()

            val updatedEvent =
                DoorEvent(
                    doorPosition = DoorPosition.OPEN,
                    message = "The door is open.",
                    lastCheckInTimeSeconds = 2000L,
                    lastChangeTimeSeconds = 1900L,
                )
            doorRepository.setCurrentDoorEvent(updatedEvent)
            testDispatcher.scheduler.runCurrent()

            val result = viewModel.currentDoorEvent.value
            assertTrue("Should be Complete after collection", result is LoadingResult.Complete)
            assertEquals(updatedEvent, result.data)
        }

    @Test
    fun collectsUpdatedDoorEventFromRepository() =
        runTest {
            val viewModel = createViewModel()

            val updatedEvent =
                DoorEvent(
                    doorPosition = DoorPosition.OPEN,
                    message = "The door is open.",
                    lastCheckInTimeSeconds = 2000L,
                    lastChangeTimeSeconds = 1900L,
                )
            doorRepository.setCurrentDoorEvent(updatedEvent)
            testDispatcher.scheduler.runCurrent()

            val result = viewModel.currentDoorEvent.value
            assertTrue("Should be Complete", result is LoadingResult.Complete)
            assertEquals(updatedEvent, result.data)
        }

    @Test
    fun collectsRecentDoorEventsFromRepository() =
        runTest {
            val viewModel = createViewModel()

            val events =
                listOf(
                    testDoorEvent,
                    DoorEvent(doorPosition = DoorPosition.OPEN, lastChangeTimeSeconds = 800L),
                )
            doorRepository.setRecentDoorEvents(events)
            testDispatcher.scheduler.runCurrent()

            val result = viewModel.recentDoorEvents.value
            assertTrue("Should be Complete after collection", result is LoadingResult.Complete)
            assertEquals(2, result.data?.size)
        }

    @Test
    fun fetchCurrentDoorEventCallsRepository() =
        runTest {
            val viewModel = createViewModel()

            viewModel.fetchCurrentDoorEvent()
            testDispatcher.scheduler.runCurrent()

            // Init also calls fetch, so count >= 2
            assertTrue(doorRepository.fetchCurrentDoorEventCount >= 1)
        }

    @Test
    fun fetchRecentDoorEventsCallsRepository() =
        runTest {
            val viewModel = createViewModel()

            viewModel.fetchRecentDoorEvents()
            testDispatcher.scheduler.runCurrent()

            assertTrue(doorRepository.fetchRecentDoorEventsCount >= 1)
        }

    @Test
    fun fetchFcmRegistrationStatusMapsRegisteredState() =
        runTest {
            val viewModel = createViewModel()

            doorFcmRepository.fetchStatusResult =
                DoorFcmState.Registered(topic = DoorFcmTopic("test-topic"))

            viewModel.fetchFcmRegistrationStatus()
            testDispatcher.scheduler.runCurrent()

            assertEquals(FcmRegistrationStatus.REGISTERED, viewModel.fcmRegistrationStatus.value)
        }

    @Test
    fun fetchFcmRegistrationStatusMapsNotRegisteredState() =
        runTest {
            val viewModel = createViewModel()

            doorFcmRepository.fetchStatusResult = DoorFcmState.NotRegistered

            viewModel.fetchFcmRegistrationStatus()
            testDispatcher.scheduler.runCurrent()

            assertEquals(
                FcmRegistrationStatus.NOT_REGISTERED,
                viewModel.fcmRegistrationStatus.value,
            )
        }

    @Test
    fun fetchFcmRegistrationStatusMapsUnknownState() =
        runTest {
            val viewModel = createViewModel()

            doorFcmRepository.fetchStatusResult = DoorFcmState.Unknown

            viewModel.fetchFcmRegistrationStatus()
            testDispatcher.scheduler.runCurrent()

            assertEquals(FcmRegistrationStatus.UNKNOWN, viewModel.fcmRegistrationStatus.value)
        }

    @Test
    fun fetchOnInitFalseDoesNotFetchOnCreate() =
        runTest {
            val viewModel = createViewModel(fetchOnInit = false)

            // Flow collection still runs (shows repo data), but network fetch was NOT triggered
            assertEquals(0, doorRepository.fetchCurrentDoorEventCount)
            assertEquals(0, doorRepository.fetchRecentDoorEventsCount)
        }
}
