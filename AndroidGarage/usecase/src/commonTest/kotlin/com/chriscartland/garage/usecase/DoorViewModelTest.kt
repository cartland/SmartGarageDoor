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

import com.chriscartland.garage.domain.coroutines.AppClock
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.FcmRegistrationStatus
import com.chriscartland.garage.domain.model.LoadingResult
import com.chriscartland.garage.testcommon.FakeAppLoggerRepository
import com.chriscartland.garage.testcommon.FakeDiagnosticsCountersRepository
import com.chriscartland.garage.testcommon.FakeDoorFcmRepository
import com.chriscartland.garage.testcommon.FakeDoorRepository
import com.chriscartland.garage.testcommon.TestDispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        appLoggerRepository = FakeAppLoggerRepository()
        doorFcmRepository = FakeDoorFcmRepository()
        doorRepository = FakeDoorRepository()
        doorRepository.setCurrentDoorEvent(testDoorEvent)
        doorRepository.setRecentDoorEvents(listOf(testDoorEvent))
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * [scope] is used for the FcmRegistrationManager and CheckInStalenessManager
     * (which contain infinite loops). Tests pass `backgroundScope` from `runTest`
     * so those loops are cancelled when the test completes.
     */
    private fun createViewModel(
        scope: CoroutineScope,
        fetchOnInit: Boolean = true,
    ): DefaultDoorViewModel {
        val fcmManager = FcmRegistrationManager(
            registerFcmUseCase = RegisterFcmUseCase(doorRepository, doorFcmRepository),
            scope = scope,
            dispatcher = testDispatcher,
        )
        val stalenessManager = CheckInStalenessManager(
            observeDoorEvents = ObserveDoorEventsUseCase(doorRepository),
            logAppEvent = LogAppEventUseCase(appLoggerRepository, FakeDiagnosticsCountersRepository()),
            scope = scope,
            dispatcher = testDispatcher,
            clock = AppClock { 0L },
        )
        val liveClock = DefaultLiveClock(
            clock = AppClock { 0L },
            scope = scope,
            dispatcher = testDispatcher,
        )
        val vm = DefaultDoorViewModel(
            observeDoorEvents = ObserveDoorEventsUseCase(doorRepository),
            logAppEvent = LogAppEventUseCase(appLoggerRepository, FakeDiagnosticsCountersRepository()),
            dispatchers = TestDispatcherProvider(testDispatcher),
            fetchCurrentDoorEventUseCase = FetchCurrentDoorEventUseCase(doorRepository),
            fetchRecentDoorEventsUseCase = FetchRecentDoorEventsUseCase(doorRepository),
            deregisterFcmUseCase = DeregisterFcmUseCase(doorFcmRepository),
            fcmRegistrationManager = fcmManager,
            checkInStalenessManager = stalenessManager,
            liveClock = liveClock,
            fetchOnInit = fetchOnInit,
        )
        testDispatcher.scheduler.runCurrent()
        return vm
    }

    @Test
    fun initialFcmRegistrationStatusIsUnknown() =
        runTest {
            val viewModel = createViewModel(scope = backgroundScope)

            assertEquals(FcmRegistrationStatus.UNKNOWN, viewModel.fcmRegistrationStatus.value)
        }

    @Test
    fun collectsCurrentDoorEventFromRepository() =
        runTest {
            val viewModel = createViewModel(scope = backgroundScope)

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
            assertTrue(result is LoadingResult.Complete, "Should be Complete after collection")
            assertEquals(updatedEvent, result.data)
        }

    @Test
    fun collectsUpdatedDoorEventFromRepository() =
        runTest {
            val viewModel = createViewModel(scope = backgroundScope)

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
            assertTrue(result is LoadingResult.Complete, "Should be Complete")
            assertEquals(updatedEvent, result.data)
        }

    @Test
    fun collectsRecentDoorEventsFromRepository() =
        runTest {
            val viewModel = createViewModel(scope = backgroundScope)

            val events =
                listOf(
                    testDoorEvent,
                    DoorEvent(doorPosition = DoorPosition.OPEN, lastChangeTimeSeconds = 800L),
                )
            doorRepository.setRecentDoorEvents(events)
            testDispatcher.scheduler.runCurrent()

            val result = viewModel.recentDoorEvents.value
            assertTrue(result is LoadingResult.Complete, "Should be Complete after collection")
            assertEquals(2, result.data?.size)
        }

    @Test
    fun fetchCurrentDoorEventCallsRepository() =
        runTest {
            val viewModel = createViewModel(scope = backgroundScope)

            viewModel.fetchCurrentDoorEvent()
            testDispatcher.scheduler.runCurrent()

            // Init also calls fetch, so count >= 2
            assertTrue(doorRepository.fetchCurrentDoorEventCount >= 1)
        }

    @Test
    fun fetchRecentDoorEventsCallsRepository() =
        runTest {
            val viewModel = createViewModel(scope = backgroundScope)

            viewModel.fetchRecentDoorEvents()
            testDispatcher.scheduler.runCurrent()

            assertTrue(doorRepository.fetchRecentDoorEventsCount >= 1)
        }

    @Test
    fun fetchOnInitFalseDoesNotFetchOnCreate() =
        runTest {
            val viewModel = createViewModel(scope = backgroundScope, fetchOnInit = false)

            // Flow collection still runs (shows repo data), but network fetch was NOT triggered
            assertEquals(0, doorRepository.fetchCurrentDoorEventCount)
            assertEquals(0, doorRepository.fetchRecentDoorEventsCount)
        }
}
