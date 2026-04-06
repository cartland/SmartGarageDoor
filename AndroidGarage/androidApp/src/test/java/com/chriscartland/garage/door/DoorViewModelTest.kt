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
import com.chriscartland.garage.coroutines.TestDispatcherProvider
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorFcmState
import com.chriscartland.garage.domain.model.DoorFcmTopic
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.FcmRegistrationStatus
import com.chriscartland.garage.domain.model.LoadingResult
import com.chriscartland.garage.domain.repository.DoorRepository
import com.chriscartland.garage.fcm.DoorFcmRepository
import com.chriscartland.garage.usecase.DeregisterFcmUseCase
import com.chriscartland.garage.usecase.FetchCurrentDoorEventUseCase
import com.chriscartland.garage.usecase.FetchFcmStatusUseCase
import com.chriscartland.garage.usecase.FetchRecentDoorEventsUseCase
import com.chriscartland.garage.usecase.RegisterFcmUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.atLeast
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class DoorViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

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

    private fun createViewModel(): DefaultDoorViewModel {
        val vm = DefaultDoorViewModel(
            appLoggerRepository,
            doorRepository,
            TestDispatcherProvider(testDispatcher),
            FetchCurrentDoorEventUseCase(doorRepository),
            FetchRecentDoorEventsUseCase(doorRepository),
            FetchFcmStatusUseCase(doorFcmRepository),
            RegisterFcmUseCase(doorRepository, doorFcmRepository),
            DeregisterFcmUseCase(doorFcmRepository),
        )
        testDispatcher.scheduler.runCurrent()
        return vm
    }

    @Test
    fun initialFcmRegistrationStatusIsUnknown() {
        val viewModel = createViewModel()

        assertEquals(FcmRegistrationStatus.UNKNOWN, viewModel.fcmRegistrationStatus.value)
    }

    @Test
    fun collectsCurrentDoorEventFromRepository() =
        runTest {
            val viewModel = createViewModel()

            // After init, state may be Loading due to fetchCurrentDoorEvent in init.
            // Emit a new value to verify the flow collection works.
            val updatedEvent =
                DoorEvent(
                    doorPosition = DoorPosition.OPEN,
                    message = "The door is open.",
                    lastCheckInTimeSeconds = 2000L,
                    lastChangeTimeSeconds = 1900L,
                )
            currentDoorEventFlow.value = updatedEvent
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
            currentDoorEventFlow.value = updatedEvent
            testDispatcher.scheduler.runCurrent()

            val result = viewModel.currentDoorEvent.value
            assertTrue("Should be Complete", result is LoadingResult.Complete)
            assertEquals(updatedEvent, result.data)
        }

    @Test
    fun collectsRecentDoorEventsFromRepository() =
        runTest {
            val viewModel = createViewModel()

            // Emit new events to verify collection works.
            val events =
                listOf(
                    testDoorEvent,
                    DoorEvent(doorPosition = DoorPosition.OPEN, lastChangeTimeSeconds = 800L),
                )
            recentDoorEventsFlow.value = events
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

            // Init may also call fetchCurrentDoorEvent, so verify at least 1 call.
            verify(doorRepository, atLeast(1)).fetchCurrentDoorEvent()
        }

    @Test
    fun fetchRecentDoorEventsCallsRepository() =
        runTest {
            val viewModel = createViewModel()

            viewModel.fetchRecentDoorEvents()
            testDispatcher.scheduler.runCurrent()

            verify(doorRepository, atLeast(1)).fetchRecentDoorEvents()
        }

    @Test
    fun fetchFcmRegistrationStatusMapsRegisteredState() =
        runTest {
            val viewModel = createViewModel()
            val activity = mock(Activity::class.java)

            `when`(doorFcmRepository.fetchStatus(activity))
                .thenReturn(DoorFcmState.Registered(topic = DoorFcmTopic("test-topic")))

            viewModel.fetchFcmRegistrationStatus(activity)
            testDispatcher.scheduler.runCurrent()

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
            val activity = mock(Activity::class.java)

            `when`(doorFcmRepository.fetchStatus(activity))
                .thenReturn(DoorFcmState.Unknown)

            viewModel.fetchFcmRegistrationStatus(activity)
            testDispatcher.scheduler.runCurrent()

            assertEquals(FcmRegistrationStatus.UNKNOWN, viewModel.fcmRegistrationStatus.value)
        }
}
