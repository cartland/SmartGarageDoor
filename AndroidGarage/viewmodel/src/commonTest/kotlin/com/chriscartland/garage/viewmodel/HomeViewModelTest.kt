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
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.ButtonHealth
import com.chriscartland.garage.domain.model.ButtonHealthError
import com.chriscartland.garage.domain.model.ButtonHealthState
import com.chriscartland.garage.domain.model.DisplayName
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.LoadingResult
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.domain.repository.ButtonHealthRepository
import com.chriscartland.garage.presentation.DoorWarning
import com.chriscartland.garage.testcommon.FakeAppLoggerRepository
import com.chriscartland.garage.testcommon.FakeAuthRepository
import com.chriscartland.garage.testcommon.FakeDiagnosticsCountersRepository
import com.chriscartland.garage.testcommon.FakeDoorFcmRepository
import com.chriscartland.garage.testcommon.FakeDoorRepository
import com.chriscartland.garage.testcommon.FakeRemoteButtonRepository
import com.chriscartland.garage.testcommon.TestDispatcherProvider
import com.chriscartland.garage.usecase.CheckInStalenessManager
import com.chriscartland.garage.usecase.ComputeButtonHealthDisplayUseCase
import com.chriscartland.garage.usecase.DefaultLiveClock
import com.chriscartland.garage.usecase.DeregisterFcmUseCase
import com.chriscartland.garage.usecase.FetchButtonHealthUseCase
import com.chriscartland.garage.usecase.FetchCurrentDoorEventUseCase
import com.chriscartland.garage.usecase.LogAppEventUseCase
import com.chriscartland.garage.usecase.ObserveAuthStateUseCase
import com.chriscartland.garage.usecase.ObserveDoorEventsUseCase
import com.chriscartland.garage.usecase.PushRemoteButtonUseCase
import com.chriscartland.garage.usecase.SignInWithGoogleUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var doorRepository: FakeDoorRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var appLoggerRepository: FakeAppLoggerRepository
    private lateinit var doorFcmRepository: FakeDoorFcmRepository
    private lateinit var remoteButtonRepository: FakeRemoteButtonRepository
    private lateinit var buttonHealthRepository: HomeTestNoopButtonHealthRepository

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
        authRepository = FakeAuthRepository()
        appLoggerRepository = FakeAppLoggerRepository()
        doorFcmRepository = FakeDoorFcmRepository()
        remoteButtonRepository = FakeRemoteButtonRepository()
        buttonHealthRepository = HomeTestNoopButtonHealthRepository()
        doorRepository.setCurrentDoorEvent(testDoorEvent)
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
        authState: AuthState = AuthState.Unauthenticated,
        fetchOnInit: Boolean = true,
        // Default true to keep existing behavior. The no-flicker
        // tests pass false so they can observe the VM's state
        // immediately after construction, before the
        // `viewModelScope.launch(dispatchers.io) { collect ... }`
        // body has executed. That's the state a real Composable
        // sees on first composition in production (where the IO
        // launch races the first-frame read of .value).
        runScheduler: Boolean = true,
    ): DefaultHomeViewModel {
        authRepository.setAuthState(authState)
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
        val computeButtonHealth = ComputeButtonHealthDisplayUseCase(
            authRepository = authRepository,
            buttonHealthRepository = buttonHealthRepository,
            liveClock = liveClock,
            applicationScope = scope,
        )
        val vm = DefaultHomeViewModel(
            observeDoorEvents = ObserveDoorEventsUseCase(doorRepository),
            observeAuthState = ObserveAuthStateUseCase(authRepository),
            logAppEvent = LogAppEventUseCase(
                appLoggerRepository,
                FakeDiagnosticsCountersRepository(),
            ),
            dispatchers = TestDispatcherProvider(testDispatcher),
            fetchCurrentDoorEventUseCase = FetchCurrentDoorEventUseCase(doorRepository),
            fetchButtonHealthUseCase = FetchButtonHealthUseCase(authRepository, buttonHealthRepository),
            deregisterFcmUseCase = DeregisterFcmUseCase(doorFcmRepository),
            signInWithGoogleUseCase = SignInWithGoogleUseCase(authRepository),
            pushRemoteButtonUseCase = PushRemoteButtonUseCase(
                authRepository,
                remoteButtonRepository,
            ),
            checkInStalenessManager = stalenessManager,
            liveClock = liveClock,
            buttonHealthDisplay = computeButtonHealth(),
            appVersion = "test",
            fetchOnInit = fetchOnInit,
        )
        if (runScheduler) {
            testDispatcher.scheduler.runCurrent()
        }
        return vm
    }

    @Test
    fun authStatePassesThroughFromRepository() =
        runTest {
            val authedUser = User(
                name = DisplayName("User"),
                email = Email("user@example.com"),
            )
            val viewModel = createViewModel(
                scope = backgroundScope,
                authState = AuthState.Authenticated(authedUser),
            )
            assertTrue(viewModel.authState.value is AuthState.Authenticated)
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
    fun fetchCurrentDoorEventCallsRepository() =
        runTest {
            val viewModel = createViewModel(scope = backgroundScope, fetchOnInit = false)

            viewModel.fetchCurrentDoorEvent()
            advanceUntilIdle()

            assertEquals(1, doorRepository.fetchCurrentDoorEventCount)
        }

    @Test
    fun fetchOnInitFalseSkipsInitialFetch() =
        runTest {
            createViewModel(scope = backgroundScope, fetchOnInit = false)

            assertEquals(0, doorRepository.fetchCurrentDoorEventCount)
        }

    @Test
    fun fetchOnInitTrueTriggersInitialFetch() =
        runTest {
            createViewModel(scope = backgroundScope, fetchOnInit = true)
            advanceUntilIdle()

            assertEquals(1, doorRepository.fetchCurrentDoorEventCount)
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
    fun fetchOnInitTrueLogsInitCurrentDoor() =
        runTest {
            createViewModel(scope = backgroundScope, fetchOnInit = true)
            advanceUntilIdle()

            assertTrue(
                appLoggerRepository.loggedKeys.any { it == AppLoggerKeys.INIT_CURRENT_DOOR },
                "INIT_CURRENT_DOOR should be logged on init when fetchOnInit=true",
            )
        }

    @Test
    fun nowEpochSecondsIsLiveClockReference() =
        runTest {
            val viewModel = createViewModel(scope = backgroundScope, fetchOnInit = false)

            // LiveClock backed by AppClock { 0L }: initial value is 0.
            assertEquals(0L, viewModel.nowEpochSeconds.value)
        }

    /**
     * Reads `viewModel.currentDoorEvent.value` BEFORE
     * `runCurrent()` — simulates the production race where the
     * Composable's first composition reads `.value` before the
     * `viewModelScope.launch(dispatchers.io) { collect { ... } }`
     * body has had a chance to fire on the IO thread.
     *
     * Bug class this guards (ADR-023 / 2.4.4 Home-tab flicker;
     * PR #738 mirror seed fix): if `_currentDoorEvent` is seeded
     * with `Loading(null)`, the first-composition read returns
     * `Loading(null)` → maps to UNKNOWN/MIDWAY door icon, then the
     * IO launch lands `Complete(actualEvent)` → maps to OPEN/CLOSED,
     * and `LaunchedEffect(doorPosition)` in `GarageIcon` visibly
     * animates MIDWAY → actual on every fresh `NavBackStackEntry`.
     *
     * Correct: seed from `observeDoorEvents.current().value` so
     * the synchronous initial value is `Complete(cachedValue)`,
     * not `Loading(null)`.
     */
    @Test
    fun initialCurrentDoorEventValueIsCompleteFromUpstreamCache() =
        runTest {
            // testDoorEvent is pre-seeded into the repo in setup().
            val viewModel = createViewModel(
                scope = backgroundScope,
                fetchOnInit = false,
                runScheduler = false,
            )

            val initial = viewModel.currentDoorEvent.value
            assertTrue(
                initial is LoadingResult.Complete,
                "Expected Complete on construction (no flicker), was $initial. " +
                    "If this fails, the VM's mirror MutableStateFlow is probably " +
                    "seeded with Loading(...) instead of Complete(upstream.value). " +
                    "See ADR-023 / 2.4.4 regression.",
            )
            assertEquals(testDoorEvent, initial.data)
        }

    @Test
    fun warningIsNullForNormalDoorState() =
        runTest {
            // setup() pre-seeds a CLOSED event → no warning (ADR-031 shared
            // DoorWarningMapper returns null for non-anomalous states).
            val viewModel = createViewModel(scope = backgroundScope, fetchOnInit = false)

            assertNull(viewModel.warning.value)
        }

    @Test
    fun warningDerivesFromCurrentDoorEvent() =
        runTest {
            val viewModel = createViewModel(scope = backgroundScope, fetchOnInit = false)

            doorRepository.setCurrentDoorEvent(
                DoorEvent(
                    doorPosition = DoorPosition.OPENING_TOO_LONG,
                    message = "Taking too long",
                    lastChangeTimeSeconds = 900L,
                ),
            )
            testDispatcher.scheduler.runCurrent()

            assertEquals(DoorWarning.ServerMessage("Taking too long"), viewModel.warning.value)
        }

    /**
     * The warning [StateFlow] is `stateIn`'d with a synchronously-computed
     * initial value seeded from the cached door event, so a fresh screen entry
     * exposes the correct warning on first read — no flicker. Mirrors
     * [initialCurrentDoorEventValueIsCompleteFromUpstreamCache] for the warning
     * slice (ADR-031).
     */
    @Test
    fun warningInitialValueSeededFromCacheNoFlicker() =
        runTest {
            // Pre-seed an anomalous event BEFORE construction; the warning must
            // be exposed synchronously, before the IO collector runs.
            doorRepository.setCurrentDoorEvent(
                DoorEvent(doorPosition = DoorPosition.OPENING_TOO_LONG, lastChangeTimeSeconds = 900L),
            )
            val viewModel = createViewModel(
                scope = backgroundScope,
                fetchOnInit = false,
                runScheduler = false,
            )

            assertEquals(DoorWarning.OpeningTooLong, viewModel.warning.value)
        }

    @Test
    fun refreshButtonHealthInvokesFetchWhenAuthenticated() =
        runTest {
            // Pull-to-refresh on Home calls this alongside fetchCurrentDoorEvent
            // so both pills refresh from a single user gesture.
            val authedUser = User(
                name = DisplayName("User"),
                email = Email("user@example.com"),
            )
            val viewModel = createViewModel(
                scope = backgroundScope,
                authState = AuthState.Authenticated(authedUser),
                fetchOnInit = false,
            )

            assertEquals(0, buttonHealthRepository.fetchCount)
            viewModel.refreshButtonHealth()
            advanceUntilIdle()

            assertEquals(1, buttonHealthRepository.fetchCount)
        }
}

private class HomeTestNoopButtonHealthRepository : ButtonHealthRepository {
    override val buttonHealth: StateFlow<LoadingResult<ButtonHealth>> =
        MutableStateFlow(LoadingResult.Loading(null))

    var fetchCount = 0
        private set

    override suspend fun fetchButtonHealth(): AppResult<ButtonHealth, ButtonHealthError> {
        fetchCount += 1
        return AppResult.Success(ButtonHealth(ButtonHealthState.UNKNOWN, null))
    }

    override fun applyFcmUpdate(update: ButtonHealth) {
        // no-op
    }
}
