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

package com.chriscartland.garage.viewmodel

import com.chriscartland.garage.domain.coroutines.AppClock
import com.chriscartland.garage.domain.model.AppLoggerKeys
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorFcmState
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.FcmRegistrationStatus
import com.chriscartland.garage.domain.model.LoadingResult
import com.chriscartland.garage.domain.model.PaginationState
import com.chriscartland.garage.domain.model.toFcmTopic
import com.chriscartland.garage.testcommon.FakeAppLoggerRepository
import com.chriscartland.garage.testcommon.FakeDiagnosticsCountersRepository
import com.chriscartland.garage.testcommon.FakeDoorFcmRepository
import com.chriscartland.garage.testcommon.FakeDoorRepository
import com.chriscartland.garage.testcommon.TestDispatcherProvider
import com.chriscartland.garage.usecase.DefaultLiveClock
import com.chriscartland.garage.usecase.DefaultRegisterFcmUseCase
import com.chriscartland.garage.usecase.FcmRegistrationManager
import com.chriscartland.garage.usecase.FetchOlderDoorEventsUseCase
import com.chriscartland.garage.usecase.FetchRecentDoorEventsUseCase
import com.chriscartland.garage.usecase.LogAppEventUseCase
import com.chriscartland.garage.usecase.ObserveDoorEventsUseCase
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

    /**
     * The real app-scoped manager over the fake repositories, hoisted so a
     * test can call `start()` the way AppStartup does and read its status.
     */
    private lateinit var fcmRegistrationManager: FcmRegistrationManager

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
     * #1311: the stale banner's "fix outdated info" action used to deregister
     * the door topic, and nothing subscribed it again until the next cold
     * start — the manager still said REGISTERED, so `start()` was a no-op.
     * The action must subscribe again, through the app-scoped manager.
     */
    @Test
    fun refreshFcmRegistrationSubscribesTheDoorTopicAgain() =
        runTest {
            val topic = BUILD_TIMESTAMP.toFcmTopic()
            doorRepository.setBuildTimestamp(BUILD_TIMESTAMP)
            doorFcmRepository.setRegisterResult(DoorFcmState.Registered(topic = topic))
            val viewModel = createViewModel(scope = backgroundScope, fetchOnInit = false)
            // What AppStartup does on launch.
            fcmRegistrationManager.start()
            testDispatcher.scheduler.runCurrent()
            assertEquals(1, doorFcmRepository.registerCount)

            viewModel.refreshFcmRegistration()
            testDispatcher.scheduler.runCurrent()

            assertEquals(2, doorFcmRepository.registerCount)
            assertEquals(topic, doorFcmRepository.lastRegisteredTopic)
            assertEquals(FcmRegistrationStatus.REGISTERED, fcmRegistrationManager.registrationStatus.value)
        }

    @Test
    fun refreshFcmRegistrationReportsAFailedSubscribeInsteadOfStayingRegistered() =
        runTest {
            doorRepository.setBuildTimestamp(BUILD_TIMESTAMP)
            doorFcmRepository.setRegisterResult(DoorFcmState.Registered(topic = BUILD_TIMESTAMP.toFcmTopic()))
            val viewModel = createViewModel(scope = backgroundScope, fetchOnInit = false)
            fcmRegistrationManager.start()
            testDispatcher.scheduler.runCurrent()

            doorFcmRepository.setRegisterResult(DoorFcmState.NotRegistered)
            viewModel.refreshFcmRegistration()
            testDispatcher.scheduler.runCurrent()

            // NOT_REGISTERED, with the manager's retry loop running — never a
            // stale REGISTERED that would make the next start() a no-op.
            assertEquals(FcmRegistrationStatus.NOT_REGISTERED, fcmRegistrationManager.registrationStatus.value)
        }

    /**
     * [scope] is used for the LiveClock ticker (staleness comes from
     * [FakeCheckInStalenessManager], which owns no jobs). Tests pass
     * `backgroundScope` from `runTest` so the loop is cancelled when the
     * test completes.
     */
    private fun createViewModel(
        scope: CoroutineScope,
        fetchOnInit: Boolean = true,
        // Default true to keep existing behavior. The no-flicker
        // tests pass false so they can observe the VM's state
        // immediately after construction, before the
        // `viewModelScope.launch(dispatchers.io) { collect ... }`
        // body has executed. That's the state a real Composable
        // sees on first composition in production (where the IO
        // launch races the first-frame read of .value).
        runScheduler: Boolean = true,
    ): DefaultDoorHistoryViewModel {
        val stalenessManager = FakeCheckInStalenessManager()
        val liveClock = DefaultLiveClock(
            clock = AppClock { 0L },
            scope = scope,
            dispatcher = testDispatcher,
        )
        val fcmRegistrationManager = FcmRegistrationManager(
            registerFcmUseCase = DefaultRegisterFcmUseCase(doorRepository, doorFcmRepository),
            scope = scope,
            dispatcher = testDispatcher,
        )
        this@DoorHistoryViewModelTest.fcmRegistrationManager = fcmRegistrationManager
        val vm = DefaultDoorHistoryViewModel(
            observeDoorEvents = ObserveDoorEventsUseCase(doorRepository),
            logAppEvent = LogAppEventUseCase(
                appLoggerRepository,
                FakeDiagnosticsCountersRepository(),
            ),
            dispatchers = TestDispatcherProvider(testDispatcher),
            fetchRecentDoorEventsUseCase = FetchRecentDoorEventsUseCase(doorRepository),
            fetchOlderDoorEventsUseCase = FetchOlderDoorEventsUseCase(doorRepository),
            fcmRegistrationManager = fcmRegistrationManager,
            checkInStalenessManager = stalenessManager,
            liveClock = liveClock,
            fetchOnInit = fetchOnInit,
        )
        if (runScheduler) {
            testDispatcher.scheduler.runCurrent()
        }
        return vm
    }

    /**
     * Reads `viewModel.recentDoorEvents.value` BEFORE
     * `runCurrent()` — simulates the production race where the
     * Composable's first composition reads `.value` before the
     * `viewModelScope.launch(dispatchers.io) { collect { ... } }`
     * body has had a chance to fire on the IO thread.
     *
     * Bug class this guards (ADR-023 / PR #739): if
     * `_recentDoorEvents` is seeded with `Loading(emptyList())`,
     * the first-composition read returns the empty list, the
     * history `LazyColumn` renders zero items for one frame, then
     * the IO launch lands `Complete(actualList)` and the list
     * pops in. Visible flicker on every fresh `NavBackStackEntry`.
     *
     * Correct: seed from `observeDoorEvents.recent().value` so
     * the synchronous initial value is `Complete(cachedList)`.
     */
    @Test
    fun initialRecentDoorEventsValueIsCompleteFromUpstreamCache() =
        runTest {
            // testDoorEvent is pre-seeded into the repo in setup().
            val viewModel = createViewModel(
                scope = backgroundScope,
                fetchOnInit = false,
                runScheduler = false,
            )

            val initial = viewModel.recentDoorEvents.value
            assertTrue(
                initial is LoadingResult.Complete,
                "Expected Complete on construction (no flicker), was $initial. " +
                    "If this fails, the VM's mirror MutableStateFlow is probably " +
                    "seeded with Loading(...) instead of Complete(upstream.value). " +
                    "See ADR-023 / PR #739.",
            )
            assertEquals(listOf(testDoorEvent), initial.data)
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

    @Test
    fun paginationStatePassesThroughRepository() =
        runTest {
            val viewModel = createViewModel(scope = backgroundScope, fetchOnInit = false)

            doorRepository.setPaginationState(
                PaginationState(nextPageToken = "tok", canLoadMore = true),
            )

            assertEquals("tok", viewModel.paginationState.value.nextPageToken)
            assertTrue(viewModel.paginationState.value.canLoadMore)
        }

    @Test
    fun fetchOlderDoorEventsCallsRepository() =
        runTest {
            val viewModel = createViewModel(scope = backgroundScope, fetchOnInit = false)

            viewModel.fetchOlderDoorEvents()
            advanceUntilIdle()

            assertEquals(1, doorRepository.fetchOlderDoorEventsCount)
        }

    @Test
    fun fetchOlderDoorEventsAppendsOlderPageAndAdvancesState() =
        runTest {
            val viewModel = createViewModel(scope = backgroundScope, fetchOnInit = false)
            val older = DoorEvent(doorPosition = DoorPosition.OPEN, lastChangeTimeSeconds = 100L)
            doorRepository.setPaginationState(
                PaginationState(nextPageToken = "tok", canLoadMore = true),
            )
            // Reaching the oldest event: the next state has no token.
            doorRepository.setOlderPage(
                events = listOf(older),
                resultingState = PaginationState(nextPageToken = null, canLoadMore = false),
            )

            viewModel.fetchOlderDoorEvents()
            advanceUntilIdle()

            val result = viewModel.recentDoorEvents.value
            assertTrue(result is LoadingResult.Complete, "Recent events should be Complete after append")
            assertTrue(
                result.data?.contains(older) == true,
                "Older page should be appended to the recent-events list",
            )
            assertEquals(false, viewModel.paginationState.value.canLoadMore)
        }
}

private const val BUILD_TIMESTAMP = "Sat Mar 13 14:45:00 2021"
