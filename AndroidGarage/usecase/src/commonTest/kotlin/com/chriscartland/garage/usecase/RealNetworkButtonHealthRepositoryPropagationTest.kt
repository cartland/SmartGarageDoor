/*
 * Copyright 2026 Chris Cartland. All rights reserved.
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
import com.chriscartland.garage.data.repository.NetworkButtonHealthRepository
import com.chriscartland.garage.domain.coroutines.AppClock
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.ButtonHealth
import com.chriscartland.garage.domain.model.ButtonHealthState
import com.chriscartland.garage.domain.model.DisplayName
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.FirebaseIdToken
import com.chriscartland.garage.domain.model.ServerConfig
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.testcommon.FakeAppLoggerRepository
import com.chriscartland.garage.testcommon.FakeAuthRepository
import com.chriscartland.garage.testcommon.FakeDiagnosticsCountersRepository
import com.chriscartland.garage.testcommon.FakeDoorFcmRepository
import com.chriscartland.garage.testcommon.FakeDoorRepository
import com.chriscartland.garage.testcommon.FakeNetworkButtonHealthDataSource
import com.chriscartland.garage.testcommon.FakeNetworkConfigDataSource
import com.chriscartland.garage.testcommon.FakeRemoteButtonRepository
import com.chriscartland.garage.testcommon.TestDispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * JVM-only equivalent of an instrumented Compose UI test for the
 * button-health pill propagation chain.
 *
 * Companion to `RealNetworkSnoozeRepositoryPropagationTest`; both close
 * the same gap for different state-y data: existing repo-level tests
 * (`NetworkButtonHealthRepositoryTest`) substitute the data source but
 * stop at the repo's own `StateFlow.value`. Existing VM tests
 * (`HomeViewModelTest`) substitute the repo with a `Fake*Repository`.
 * Neither test exercises the chain
 *
 *   `NetworkButtonHealthRepository.buttonHealth` (StateFlow)
 *     → `ComputeButtonHealthDisplayUseCase` (Flow combine with auth + clock)
 *     → `HomeViewModel.buttonHealthDisplay` (Flow exposed to Composable)
 *
 * end-to-end. This test wires the REAL repo + REAL UseCase + REAL
 * `DefaultHomeViewModel` around fakes so a regression in the SWR pattern
 * (ADR-022; landed in 2.12.1) or the combine wiring shows up here
 * without needing an emulator.
 *
 * If this test fails, the bug is in pure Kotlin and reproduces locally
 * — no device needed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealNetworkButtonHealthRepositoryPropagationTest {
    private val validConfig = NetworkResult.Success(
        ServerConfig(
            buildTimestamp = "door",
            remoteButtonBuildTimestamp = "button",
            remoteButtonPushKey = "key",
        ),
    )

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Build the full chain: real repo + real UseCase + real `DefaultHomeViewModel`. */
    private fun buildVm(
        externalScope: CoroutineScope,
        ds: FakeNetworkButtonHealthDataSource,
    ): Pair<NetworkButtonHealthRepository, DefaultHomeViewModel> {
        val testDispatcher = UnconfinedTestDispatcher()
        val configDs = FakeNetworkConfigDataSource().apply { setServerConfigResult(validConfig) }
        val authRepo = FakeAuthRepository().apply {
            setIdTokenResult(FirebaseIdToken(idToken = "token", exp = Long.MAX_VALUE))
            setAuthState(
                AuthState.Authenticated(
                    user = User(name = DisplayName("Test"), email = Email("test@test.com")),
                ),
            )
        }
        val healthRepo = NetworkButtonHealthRepository(
            networkButtonHealthDataSource = ds,
            serverConfigRepository = CachedServerConfigRepository(configDs, "key", externalScope),
            authRepository = authRepo,
            externalScope = externalScope,
        )
        val liveClock = DefaultLiveClock(
            clock = AppClock { 0L },
            scope = externalScope,
            dispatcher = testDispatcher,
        )
        val computeButtonHealth = ComputeButtonHealthDisplayUseCase(
            authRepository = authRepo,
            buttonHealthRepository = healthRepo,
            liveClock = liveClock,
        )
        val doorRepo = FakeDoorRepository()
        val appLogger = FakeAppLoggerRepository()
        val counters = FakeDiagnosticsCountersRepository()
        val stalenessManager = CheckInStalenessManager(
            observeDoorEvents = ObserveDoorEventsUseCase(doorRepo),
            logAppEvent = LogAppEventUseCase(appLogger, counters),
            scope = externalScope,
            dispatcher = testDispatcher,
            clock = AppClock { 0L },
        )
        val vm = DefaultHomeViewModel(
            observeDoorEvents = ObserveDoorEventsUseCase(doorRepo),
            observeAuthState = ObserveAuthStateUseCase(authRepo),
            logAppEvent = LogAppEventUseCase(appLogger, counters),
            dispatchers = TestDispatcherProvider(testDispatcher),
            fetchCurrentDoorEventUseCase = FetchCurrentDoorEventUseCase(doorRepo),
            deregisterFcmUseCase = DeregisterFcmUseCase(FakeDoorFcmRepository()),
            signInWithGoogleUseCase = SignInWithGoogleUseCase(authRepo),
            pushRemoteButtonUseCase = PushRemoteButtonUseCase(authRepo, FakeRemoteButtonRepository()),
            checkInStalenessManager = stalenessManager,
            liveClock = liveClock,
            buttonHealthDisplay = computeButtonHealth(),
            appVersion = "test",
            fetchOnInit = false,
        )
        return healthRepo to vm
    }

    @Test
    fun fetchSuccess_propagatesToVmButtonHealthDisplayFlow() =
        runTest {
            val ds = FakeNetworkButtonHealthDataSource().apply {
                setResult(NetworkResult.Success(ButtonHealth(ButtonHealthState.ONLINE, 1000L)))
            }
            val externalScope = CoroutineScope(
                SupervisorJob() + UnconfinedTestDispatcher(testScheduler),
            )
            val (healthRepo, vm) = buildVm(externalScope, ds)
            advanceUntilIdle()

            // Trigger the fetch — repo writes Complete(ONLINE).
            healthRepo.fetchButtonHealth()
            advanceUntilIdle()

            // The VM-exposed Flow must reflect the Online state.
            assertIs<ButtonHealthDisplay.Online>(vm.buttonHealthDisplay.first())
            externalScope.cancel()
        }

    @Test
    fun fetchHttpError_keepsPriorOnlineDisplayInVm() =
        runTest {
            // SWR end-to-end: an established Complete(ONLINE) value must
            // survive a subsequent HTTP failure. The VM's Flow must NOT
            // regress to Loading after the error fetch. Pre-2.12.1, a
            // single transient 500 cleared the prior good value and
            // ButtonHealthDisplayLogic mapped Error → Loading, so the
            // pill flickered to "Checking" until something else fixed it.
            val ds = FakeNetworkButtonHealthDataSource().apply {
                setResult(NetworkResult.Success(ButtonHealth(ButtonHealthState.ONLINE, 1000L)))
            }
            val externalScope = CoroutineScope(
                SupervisorJob() + UnconfinedTestDispatcher(testScheduler),
            )
            val (healthRepo, vm) = buildVm(externalScope, ds)
            advanceUntilIdle()

            // First fetch establishes Complete(ONLINE).
            healthRepo.fetchButtonHealth()
            advanceUntilIdle()
            assertIs<ButtonHealthDisplay.Online>(vm.buttonHealthDisplay.first())

            // Second fetch hits HTTP 500 — repo SWR must keep ONLINE.
            ds.setResult(NetworkResult.HttpError(500))
            healthRepo.fetchButtonHealth()
            advanceUntilIdle()

            // The VM's Flow must STILL reflect Online — the failure was
            // swallowed by SWR at the repo, the VM should never see a
            // regression. If this assertion fails, the SWR pattern is
            // broken end-to-end.
            assertEquals(
                ButtonHealthDisplay.Online,
                vm.buttonHealthDisplay.first(),
                "VM Flow should keep showing Online after a transient HTTP 500 — SWR regression?",
            )
            externalScope.cancel()
        }
}
