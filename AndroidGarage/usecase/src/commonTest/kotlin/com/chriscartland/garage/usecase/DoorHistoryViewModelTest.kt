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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DoorHistoryViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var doorRepository: FakeDoorRepository
    private lateinit var appLoggerRepository: FakeAppLoggerRepository
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
        doorRepository = FakeDoorRepository()
        appLoggerRepository = FakeAppLoggerRepository()
        doorFcmRepository = FakeDoorFcmRepository()
        doorRepository.setRecentDoorEvents(listOf(testDoorEvent))
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * [scope] is used for the CheckInStalenessManager (infinite loop) and
     * LiveClock (10s ticker). Tests pass `backgroundScope` from `runTest`
     * so those loops are cancelled when the test completes.
     */
    private fun createViewModel(
        scope: CoroutineScope,
        fetchOnInit: Boolean = true,
    ): DefaultDoorHistoryViewModel {
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
        val vm = DefaultDoorHistoryViewModel(
            observeDoorEvents = ObserveDoorEventsUseCase(doorRepository),
            logAppEvent = LogAppEventUseCase(
                appLoggerRepository,
                FakeDiagnosticsCountersRepository(),
            ),
            dispatchers = TestDispatcherProvider(testDispatcher),
            fetchRecentDoorEventsUseCase = FetchRecentDoorEventsUseCase(doorRepository),
            deregisterFcmUseCase = DeregisterFcmUseCase(doorFcmRepository),
            checkInStalenessManager = stalenessManager,
            liveClock = liveClock,
            fetchOnInit = fetchOnInit,
        )
        testDispatcher.scheduler.runCurrent()
        return vm
    }

    @Test
    fun collectsRecentDoorEventsFromRepository() =
        runTest {
            val viewModel = createViewModel(scope = backgroundScope)

            val events = listOf(
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
    fun fetchRecentDoorEventsCallsRepository() =
        runTest {
            val viewModel = createViewModel(scope = backgroundScope, fetchOnInit = false)

            viewModel.fetchRecentDoorEvents()
            advanceUntilIdle()

            assertEquals(1, doorRepository.fetchRecentDoorEventsCount)
        }

    @Test
    fun fetchOnInitFalseSkipsInitialFetch() =
        runTest {
            createViewModel(scope = backgroundScope, fetchOnInit = false)

            assertEquals(0, doorRepository.fetchRecentDoorEventsCount)
        }

    @Test
    fun fetchOnInitTrueTriggersInitialFetch() =
        runTest {
            createViewModel(scope = backgroundScope, fetchOnInit = true)
            advanceUntilIdle()

            assertEquals(1, doorRepository.fetchRecentDoorEventsCount)
        }

    @Test
    fun fetchOnInitTrueLogsInitRecentDoor() =
        runTest {
            createViewModel(scope = backgroundScope, fetchOnInit = true)
            advanceUntilIdle()

            assertTrue(
                appLoggerRepository.loggedKeys.any { it == AppLoggerKeys.INIT_RECENT_DOOR },
                "INIT_RECENT_DOOR should be logged on init when fetchOnInit=true",
            )
        }

    @Test
    fun logForwardsToAppLogger() =
        runTest {
            val viewModel = createViewModel(scope = backgroundScope, fetchOnInit = false)

            viewModel.log("custom_key")
            advanceUntilIdle()

            assertTrue(appLoggerRepository.loggedKeys.any { it == "custom_key" })
        }

    @Test
    fun nowEpochSecondsIsLiveClockReference() =
        runTest {
            val viewModel = createViewModel(scope = backgroundScope, fetchOnInit = false)

            assertEquals(0L, viewModel.nowEpochSeconds.value)
        }
}
