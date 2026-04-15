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
import com.chriscartland.garage.domain.model.AppLoggerKeys
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.FcmRegistrationStatus
import com.chriscartland.garage.domain.model.LoadingResult
import com.chriscartland.garage.testcommon.FakeAppLoggerRepository
import com.chriscartland.garage.testcommon.FakeClock
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
     * Pass the [scope] from `runTest { this }` so staleness coroutines
     * are cancelled when the test completes (same pattern as FcmRegistrationManagerTest).
     */
    private fun createViewModel(
        scope: CoroutineScope,
        clock: AppClock = AppClock { 0L },
        fetchOnInit: Boolean = true,
    ): DefaultDoorViewModel {
        val fcmManager = FcmRegistrationManager(
            registerFcmUseCase = RegisterFcmUseCase(doorRepository, doorFcmRepository),
            scope = scope,
            dispatcher = testDispatcher,
        )
        val vm = DefaultDoorViewModel(
            observeDoorEvents = ObserveDoorEventsUseCase(doorRepository),
            logAppEvent = LogAppEventUseCase(appLoggerRepository),
            dispatchers = TestDispatcherProvider(testDispatcher),
            fetchCurrentDoorEventUseCase = FetchCurrentDoorEventUseCase(doorRepository),
            fetchRecentDoorEventsUseCase = FetchRecentDoorEventsUseCase(doorRepository),
            deregisterFcmUseCase = DeregisterFcmUseCase(doorFcmRepository),
            fcmRegistrationManager = fcmManager,
            clock = clock,
            scope = scope,
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

    // --- Staleness tests ---

    private val staleCheckInterval = DefaultDoorViewModel.STALE_CHECK_INTERVAL_MS

    @Test
    fun isCheckInStale_isFalse_whenCheckInIsRecent() =
        runTest {
            val clock = FakeClock(nowSeconds = 1000L)
            doorRepository.setCurrentDoorEvent(
                testDoorEvent.copy(lastCheckInTimeSeconds = 1000L),
            )
            val viewModel = createViewModel(
                scope = backgroundScope,
                clock = clock,
            )

            assertEquals(false, viewModel.isCheckInStale.value)
        }

    @Test
    fun isCheckInStale_isTrue_whenStaleEventArrives() =
        runTest {
            // Start with fresh check-in.
            val clock = FakeClock(nowSeconds = 1000L)
            doorRepository.setCurrentDoorEvent(
                testDoorEvent.copy(lastCheckInTimeSeconds = 1000L),
            )
            val viewModel = createViewModel(
                scope = backgroundScope,
                clock = clock,
            )
            assertEquals(false, viewModel.isCheckInStale.value)

            // Emit a new event with old check-in — reactive path (no ticker needed).
            val staleCheckInTime = clock.nowEpochSeconds() -
                DefaultDoorViewModel.CHECK_IN_STALE_THRESHOLD_SECONDS - 1
            doorRepository.setCurrentDoorEvent(
                testDoorEvent.copy(lastCheckInTimeSeconds = staleCheckInTime),
            )
            testDispatcher.scheduler.runCurrent()

            assertEquals(true, viewModel.isCheckInStale.value)
        }

    @Test
    fun isCheckInStale_isTrue_whenCheckInIsOld_viaTicker() =
        runTest {
            val checkInTime = 1000L
            val clock = FakeClock(
                nowSeconds = checkInTime + DefaultDoorViewModel.CHECK_IN_STALE_THRESHOLD_SECONDS + 1,
            )
            doorRepository.setCurrentDoorEvent(
                testDoorEvent.copy(lastCheckInTimeSeconds = checkInTime),
            )
            val viewModel = createViewModel(
                scope = backgroundScope,
                clock = clock,
            )

            // Advance past the ticker interval so the periodic check fires.
            testDispatcher.scheduler.advanceTimeBy(staleCheckInterval + 1)
            testDispatcher.scheduler.runCurrent()

            assertEquals(true, viewModel.isCheckInStale.value)
        }

    @Test
    fun isCheckInStale_becomesTrue_afterClockAdvances() =
        runTest {
            val checkInTime = 1000L
            val clock = FakeClock(nowSeconds = checkInTime)
            doorRepository.setCurrentDoorEvent(
                testDoorEvent.copy(lastCheckInTimeSeconds = checkInTime),
            )
            val viewModel = createViewModel(
                scope = backgroundScope,
                clock = clock,
            )

            assertEquals(false, viewModel.isCheckInStale.value)

            // Advance the fake clock past threshold.
            clock.advanceSeconds(DefaultDoorViewModel.CHECK_IN_STALE_THRESHOLD_SECONDS + 1)
            // Advance coroutine time so the ticker fires.
            testDispatcher.scheduler.advanceTimeBy(staleCheckInterval + 1)
            testDispatcher.scheduler.runCurrent()

            assertEquals(true, viewModel.isCheckInStale.value)
        }

    @Test
    fun isCheckInStale_becomesFresh_whenNewEventArrives() =
        runTest {
            val checkInTime = 1000L
            val clock = FakeClock(
                nowSeconds = checkInTime + DefaultDoorViewModel.CHECK_IN_STALE_THRESHOLD_SECONDS + 1,
            )
            doorRepository.setCurrentDoorEvent(
                testDoorEvent.copy(lastCheckInTimeSeconds = checkInTime),
            )
            val viewModel = createViewModel(
                scope = backgroundScope,
                clock = clock,
            )

            // Becomes stale via ticker.
            testDispatcher.scheduler.advanceTimeBy(staleCheckInterval + 1)
            testDispatcher.scheduler.runCurrent()
            assertEquals(true, viewModel.isCheckInStale.value)

            // New event arrives with fresh check-in time — triggers collect, not ticker.
            doorRepository.setCurrentDoorEvent(
                testDoorEvent.copy(lastCheckInTimeSeconds = clock.nowEpochSeconds()),
            )
            testDispatcher.scheduler.runCurrent()

            assertEquals(false, viewModel.isCheckInStale.value)
        }

    @Test
    fun logsStaleEvent_whenCheckInBecomesStale() =
        runTest {
            val checkInTime = 1000L
            val clock = FakeClock(nowSeconds = checkInTime)
            doorRepository.setCurrentDoorEvent(
                testDoorEvent.copy(lastCheckInTimeSeconds = checkInTime),
            )
            createViewModel(
                scope = backgroundScope,
                clock = clock,
            )

            // Advance past threshold.
            clock.advanceSeconds(DefaultDoorViewModel.CHECK_IN_STALE_THRESHOLD_SECONDS + 1)
            testDispatcher.scheduler.advanceTimeBy(staleCheckInterval + 1)
            testDispatcher.scheduler.runCurrent()

            assertTrue(
                appLoggerRepository.loggedKeys.contains(
                    AppLoggerKeys.EXCEEDED_EXPECTED_TIME_WITHOUT_FCM,
                ),
                "Expected staleness log, got: ${appLoggerRepository.loggedKeys}",
            )
        }

    @Test
    fun doesNotLogFresh_onFirstEmission() =
        runTest {
            val clock = FakeClock(nowSeconds = 1000L)
            doorRepository.setCurrentDoorEvent(
                testDoorEvent.copy(lastCheckInTimeSeconds = 1000L),
            )
            createViewModel(
                scope = backgroundScope,
                clock = clock,
            )

            // The first emission is false (fresh) — should NOT log "in range".
            val freshLogs = appLoggerRepository.loggedKeys.filter {
                it == AppLoggerKeys.TIME_WITHOUT_FCM_IN_EXPECTED_RANGE
            }
            assertTrue(
                freshLogs.isEmpty(),
                "Should not log fresh on first emission, got: ${appLoggerRepository.loggedKeys}",
            )
        }
}
