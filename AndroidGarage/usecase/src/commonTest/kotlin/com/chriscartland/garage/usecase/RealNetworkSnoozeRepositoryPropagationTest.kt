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

import com.chriscartland.garage.data.NetworkResult
import com.chriscartland.garage.data.repository.CachedServerConfigRepository
import com.chriscartland.garage.data.repository.NetworkSnoozeRepository
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.DisplayName
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.FirebaseIdToken
import com.chriscartland.garage.domain.model.ServerConfig
import com.chriscartland.garage.domain.model.SnoozeAction
import com.chriscartland.garage.domain.model.SnoozeDurationUIOption
import com.chriscartland.garage.domain.model.SnoozeState
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.testcommon.FakeAppLoggerRepository
import com.chriscartland.garage.testcommon.FakeAuthRepository
import com.chriscartland.garage.testcommon.FakeDiagnosticsCountersRepository
import com.chriscartland.garage.testcommon.FakeDoorRepository
import com.chriscartland.garage.testcommon.FakeFeatureAllowlistRepository
import com.chriscartland.garage.testcommon.FakeNetworkButtonDataSource
import com.chriscartland.garage.testcommon.FakeNetworkConfigDataSource
import com.chriscartland.garage.testcommon.FakeRemoteButtonRepository
import com.chriscartland.garage.testcommon.TestDispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JVM-only equivalent of an instrumented Compose UI test for the android/167
 * "snooze card title doesn't update" bug.
 *
 * Context (production bug on android/167):
 *   1. Profile card shows "Door notifications enabled".
 *   2. User taps snooze, selects 1 hour, taps Save.
 *   3. Overlay text (client-optimistic) shows "Saved! Snoozing until 8:30pm".
 *   4. Main card title STAYS at "Door notifications enabled" — should have
 *      flipped to "Door notifications snoozed until 8:30pm".
 *   5. Kill + relaunch → card now shows "snoozed until 8:30pm". Server did
 *      persist; only the in-memory state-flow propagation failed on device.
 *
 * Existing unit tests have already ruled out:
 *   - Singleton flow identity end-to-end (SnoozeIdentityTest).
 *   - StateFlow propagation with fake repo (SnoozeStateFlowPropagationTest).
 *   - VM-scope cancellation of fetches (NetworkSnoozeRepositoryTest).
 *
 * Gap this test closes: those tests substitute [com.chriscartland.garage
 * .testcommon.FakeSnoozeRepository] for the real repo. This test wires the
 * REAL [NetworkSnoozeRepository] (with its
 * `externalScope.async { ... }.await()` and `snoozeStateFlow.value = ...`
 * writes) into the REAL [DefaultRemoteButtonViewModel] and drives the exact
 * sequence the user does on device:
 *
 *   vm.snoozeOpenDoorsNotifications(SnoozeDurationUIOption.OneHour)
 *
 * Expected: `vm.snoozeState.value` becomes
 * `SnoozeState.Snoozing(submittedSnoozeEnd)` after the coroutine completes.
 *
 * If this test FAILS, the bug reproduces without a device — the chain is
 * broken in pure Kotlin. If it PASSES (as expected given PR #349), the bug
 * is a Compose/Nav/lifecycle phenomenon that only instrumented tests can
 * catch — see the sibling instrumented test file for that path.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealNetworkSnoozeRepositoryPropagationTest {
    private lateinit var buttonDs: FakeNetworkButtonDataSource
    private lateinit var configDs: FakeNetworkConfigDataSource
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var doorRepository: FakeDoorRepository
    private lateinit var remoteButtonRepository: FakeRemoteButtonRepository
    private lateinit var featureAllowlistRepository: FakeFeatureAllowlistRepository
    private lateinit var appLoggerRepository: FakeAppLoggerRepository

    private val validConfig = NetworkResult.Success(
        ServerConfig(
            buildTimestamp = "test-build",
            remoteButtonBuildTimestamp = "test-build",
            remoteButtonPushKey = "test-key",
        ),
    )

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        buttonDs = FakeNetworkButtonDataSource()
        configDs = FakeNetworkConfigDataSource().apply { setServerConfigResult(validConfig) }
        authRepository = FakeAuthRepository().apply {
            setIdTokenResult(FirebaseIdToken(idToken = "tok", exp = Long.MAX_VALUE))
            setAuthState(
                AuthState.Authenticated(
                    user = User(
                        name = DisplayName("Test"),
                        email = Email("test@test.com"),
                    ),
                ),
            )
        }
        doorRepository = FakeDoorRepository().apply {
            // VM's snoozeOpenDoorsNotifications reads currentDoorEvent?.lastChangeTimeSeconds
            // to build the snooze-event timestamp. Must be non-null or the
            // use case returns MissingData.
            setCurrentDoorEvent(DoorEvent(lastChangeTimeSeconds = 1000L))
        }
        remoteButtonRepository = FakeRemoteButtonRepository()
        featureAllowlistRepository = FakeFeatureAllowlistRepository()
        appLoggerRepository = FakeAppLoggerRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * The exact sequence that reproduces (or rules out) the android/167
     * visual bug, but exercised against the real repository and view model.
     */
    @Test
    fun snoozeOneHourFlipsVmSnoozeStateFromNotSnoozingToSnoozing() =
        runTest {
            val now = 1_000L
            val submittedSnoozeEnd = 4_600L // 1 hour (in test units) in the future
            buttonDs.setFetchSnoozeResult(NetworkResult.Success(0L)) // initial: not snoozing
            buttonDs.setSnoozeResult(NetworkResult.Success(submittedSnoozeEnd))

            val externalScope = CoroutineScope(
                SupervisorJob() + UnconfinedTestDispatcher(testScheduler),
            )

            val snoozeRepository = NetworkSnoozeRepository(
                networkButtonDataSource = buttonDs,
                serverConfigRepository = CachedServerConfigRepository(
                    configDs,
                    "test-key",
                    externalScope,
                ),
                authRepository = authRepository,
                snoozeNotificationsOption = true,
                currentTimeSeconds = { now },
                externalScope = externalScope,
            )
            advanceUntilIdle()
            // Repository init fetch resolves to NotSnoozing (server returned 0).
            // This matches the production precondition: card shows
            // "Door notifications enabled".
            assertEquals(SnoozeState.NotSnoozing, snoozeRepository.snoozeState.value)

            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            val vm = DefaultProfileViewModel(
                observeAuthState = ObserveAuthStateUseCase(authRepository),
                observeSnoozeState = ObserveSnoozeStateUseCase(snoozeRepository),
                observeDoorEvents = ObserveDoorEventsUseCase(doorRepository),
                observeFeatureAccessUseCase = ObserveFeatureAccessUseCase(featureAllowlistRepository),
                signInWithGoogleUseCase = SignInWithGoogleUseCase(authRepository),
                signOutUseCase = SignOutUseCase(authRepository),
                fetchSnoozeStatusUseCase = FetchSnoozeStatusUseCase(snoozeRepository),
                snoozeNotificationsUseCase = SnoozeNotificationsUseCase(
                    authRepository,
                    snoozeRepository,
                ),
                logAppEvent = LogAppEventUseCase(
                    appLoggerRepository,
                    FakeDiagnosticsCountersRepository(),
                ),
                dispatchers = TestDispatcherProvider(testDispatcher),
            )
            advanceUntilIdle()

            // Precondition: the VM's observer has caught up — snoozeState is
            // NotSnoozing. In production this corresponds to the card showing
            // "Door notifications enabled".
            assertEquals(SnoozeState.NotSnoozing, vm.snoozeState.value)
            assertEquals(SnoozeAction.Idle, vm.snoozeAction.value)

            // --- The action that triggers the bug on device ---
            vm.snoozeOpenDoorsNotifications(SnoozeDurationUIOption.OneHour)
            // Run all currently-ready coroutines but NOT delayed ones —
            // otherwise `advanceUntilIdle` also fires the 10-second
            // scheduleActionReset delay and Succeeded.Set is reverted to Idle,
            // hiding the signal.
            runCurrent()

            // Assertion A (overlay): VM sets action to Succeeded.Set — this
            // is what the small overlay text binds to. In production this
            // text DID update ("Saved! Snoozing until 8:30pm"), so we expect
            // this to pass.
            val action = vm.snoozeAction.value
            assertTrue(
                action is SnoozeAction.Succeeded.Set,
                "snoozeAction should be Succeeded.Set after a successful snooze — " +
                    "was $action",
            )

            // Assertion B (main card title): VM's snoozeState must reflect
            // Snoozing(submittedSnoozeEnd) — THIS is what the production
            // android/167 bug says fails on device. If this assertion passes,
            // the pure-Kotlin chain is intact and the bug is Compose-specific.
            assertEquals(
                SnoozeState.Snoozing(submittedSnoozeEnd),
                vm.snoozeState.value,
                "Main card snoozeState must flip to Snoozing after snooze POST " +
                    "succeeds. Staying at NotSnoozing reproduces android/167.",
            )

            // Exactly one POST, no extra GET follow-up (ADR: the POST
            // response carries the authoritative end time).
            assertEquals(1, buttonDs.snoozeCount)
            assertEquals(1, buttonDs.fetchSnoozeCount)

            // Drain the 10-second reset delay so runTest doesn't complain about
            // a leftover scheduled coroutine.
            advanceTimeBy(15_000)
            runCurrent()
            externalScope.cancel()
        }

    /**
     * Pre-snoozing case: repo starts in Snoozing, user taps None to clear.
     * After the POST returns 0, VM's snoozeState must flip to NotSnoozing.
     */
    @Test
    fun clearingSnoozeFlipsVmStateFromSnoozingToNotSnoozing() =
        runTest {
            val now = 1_000L
            val initialEnd = 5_000L
            buttonDs.setFetchSnoozeResult(NetworkResult.Success(initialEnd)) // server says snoozing
            buttonDs.setSnoozeResult(NetworkResult.Success(0L)) // POST clears

            val externalScope = CoroutineScope(
                SupervisorJob() + UnconfinedTestDispatcher(testScheduler),
            )
            val snoozeRepository = NetworkSnoozeRepository(
                networkButtonDataSource = buttonDs,
                serverConfigRepository = CachedServerConfigRepository(configDs, "test-key", externalScope),
                authRepository = authRepository,
                snoozeNotificationsOption = true,
                currentTimeSeconds = { now },
                externalScope = externalScope,
            )
            advanceUntilIdle()

            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            val vm = DefaultProfileViewModel(
                observeAuthState = ObserveAuthStateUseCase(authRepository),
                observeSnoozeState = ObserveSnoozeStateUseCase(snoozeRepository),
                observeDoorEvents = ObserveDoorEventsUseCase(doorRepository),
                observeFeatureAccessUseCase = ObserveFeatureAccessUseCase(featureAllowlistRepository),
                signInWithGoogleUseCase = SignInWithGoogleUseCase(authRepository),
                signOutUseCase = SignOutUseCase(authRepository),
                fetchSnoozeStatusUseCase = FetchSnoozeStatusUseCase(snoozeRepository),
                snoozeNotificationsUseCase = SnoozeNotificationsUseCase(
                    authRepository,
                    snoozeRepository,
                ),
                logAppEvent = LogAppEventUseCase(
                    appLoggerRepository,
                    FakeDiagnosticsCountersRepository(),
                ),
                dispatchers = TestDispatcherProvider(testDispatcher),
            )
            advanceUntilIdle()
            assertEquals(SnoozeState.Snoozing(initialEnd), vm.snoozeState.value)

            vm.snoozeOpenDoorsNotifications(SnoozeDurationUIOption.None)
            runCurrent()

            assertEquals(SnoozeAction.Succeeded.Cleared, vm.snoozeAction.value)
            assertEquals(SnoozeState.NotSnoozing, vm.snoozeState.value)

            advanceTimeBy(15_000)
            runCurrent()
            externalScope.cancel()
        }
}
