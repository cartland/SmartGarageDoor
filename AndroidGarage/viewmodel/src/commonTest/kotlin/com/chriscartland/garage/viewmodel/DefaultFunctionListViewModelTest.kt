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

import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.ButtonHealth
import com.chriscartland.garage.domain.model.ButtonHealthError
import com.chriscartland.garage.domain.model.ButtonHealthState
import com.chriscartland.garage.domain.model.DisplayName
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.FeatureAllowlist
import com.chriscartland.garage.domain.model.LoadingResult
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.domain.repository.ButtonHealthRepository
import com.chriscartland.garage.testcommon.FakeAppLoggerRepository
import com.chriscartland.garage.testcommon.FakeAuthRepository
import com.chriscartland.garage.testcommon.FakeDiagnosticsCountersRepository
import com.chriscartland.garage.testcommon.FakeDoorFcmRepository
import com.chriscartland.garage.testcommon.FakeDoorRepository
import com.chriscartland.garage.testcommon.FakeFeatureAllowlistRepository
import com.chriscartland.garage.testcommon.FakeRemoteButtonRepository
import com.chriscartland.garage.testcommon.FakeSnoozeRepository
import com.chriscartland.garage.testcommon.TestDispatcherProvider
import com.chriscartland.garage.usecase.ClearDiagnosticsUseCase
import com.chriscartland.garage.usecase.DefaultRegisterFcmUseCase
import com.chriscartland.garage.usecase.DeregisterFcmUseCase
import com.chriscartland.garage.usecase.FetchButtonHealthUseCase
import com.chriscartland.garage.usecase.FetchCurrentDoorEventUseCase
import com.chriscartland.garage.usecase.FetchRecentDoorEventsUseCase
import com.chriscartland.garage.usecase.FetchSnoozeStatusUseCase
import com.chriscartland.garage.usecase.ObserveDoorEventsUseCase
import com.chriscartland.garage.usecase.ObserveFeatureAccessUseCase
import com.chriscartland.garage.usecase.PruneDiagnosticsLogUseCase
import com.chriscartland.garage.usecase.PushRemoteButtonUseCase
import com.chriscartland.garage.usecase.SignInWithGoogleUseCase
import com.chriscartland.garage.usecase.SignOutUseCase
import com.chriscartland.garage.usecase.SnoozeNotificationsUseCase
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultFunctionListViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var doorRepository: FakeDoorRepository
    private lateinit var remoteButtonRepository: FakeRemoteButtonRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var snoozeRepository: FakeSnoozeRepository
    private lateinit var featureAllowlistRepository: FakeFeatureAllowlistRepository
    private lateinit var appLoggerRepository: FakeAppLoggerRepository
    private lateinit var diagnosticsCountersRepository: FakeDiagnosticsCountersRepository
    private lateinit var doorFcmRepository: FakeDoorFcmRepository

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        doorRepository = FakeDoorRepository()
        remoteButtonRepository = FakeRemoteButtonRepository()
        authRepository = FakeAuthRepository()
        snoozeRepository = FakeSnoozeRepository()
        featureAllowlistRepository = FakeFeatureAllowlistRepository()
        appLoggerRepository = FakeAppLoggerRepository()
        diagnosticsCountersRepository = FakeDiagnosticsCountersRepository()
        doorFcmRepository = FakeDoorFcmRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(authState: AuthState = AuthState.Unauthenticated): DefaultFunctionListViewModel {
        authRepository.setAuthState(authState)
        return DefaultFunctionListViewModel(
            pushRemoteButtonUseCase = PushRemoteButtonUseCase(
                authRepository,
                remoteButtonRepository,
            ),
            fetchCurrentDoorEventUseCase = FetchCurrentDoorEventUseCase(doorRepository),
            fetchRecentDoorEventsUseCase = FetchRecentDoorEventsUseCase(doorRepository),
            fetchSnoozeStatusUseCase = FetchSnoozeStatusUseCase(snoozeRepository),
            fetchButtonHealthUseCase = FetchButtonHealthUseCase(
                authRepository,
                NoopButtonHealthRepository(),
            ),
            snoozeNotificationsUseCase = SnoozeNotificationsUseCase(
                authRepository,
                snoozeRepository,
            ),
            signInWithGoogleUseCase = SignInWithGoogleUseCase(authRepository),
            signOutUseCase = SignOutUseCase(authRepository),
            observeDoorEventsUseCase = ObserveDoorEventsUseCase(doorRepository),
            observeFeatureAccessUseCase = ObserveFeatureAccessUseCase(featureAllowlistRepository),
            clearDiagnosticsUseCase = ClearDiagnosticsUseCase(
                appLoggerRepository,
                diagnosticsCountersRepository,
            ),
            pruneDiagnosticsLogUseCase = PruneDiagnosticsLogUseCase(appLoggerRepository),
            registerFcmUseCase = DefaultRegisterFcmUseCase(doorRepository, doorFcmRepository),
            deregisterFcmUseCase = DeregisterFcmUseCase(doorFcmRepository),
            dispatchers = TestDispatcherProvider(testDispatcher),
            appVersion = "test",
        )
    }

    private fun authenticated(): AuthState.Authenticated =
        AuthState.Authenticated(
            user = User(
                name = DisplayName("Test"),
                email = Email("test@test.com"),
            ),
        )

    // The handful of trivial passthrough tests that previously lived here
    // (refreshDoorStatus → repo.fetchCount, signOut → AuthState change,
    // etc.) have been cut as smoke-test ceremony — they verified
    // `viewModelScope.launch { useCase() }` calls the use case, which is
    // a Kotlin-language guarantee, not a behavior worth pinning. The
    // remaining tests pin the only behavior that is genuinely *new* on
    // this VM and not already covered by repo / use-case tests:
    // openOrCloseDoor's auth gate, snooze's door-event-timestamp wiring,
    // and the `accessGranted` allowlist flow.

    @Test
    fun openOrCloseDoorPushesButtonWhenAuthenticated() =
        runTest(testDispatcher) {
            val viewModel = createViewModel(authState = authenticated())
            viewModel.openOrCloseDoor()
            advanceUntilIdle()
            assertEquals(1, remoteButtonRepository.pushCount)
        }

    @Test
    fun openOrCloseDoorIsNoopWhenUnauthenticated() =
        runTest(testDispatcher) {
            val viewModel = createViewModel(authState = AuthState.Unauthenticated)
            viewModel.openOrCloseDoor()
            advanceUntilIdle()
            assertEquals(0, remoteButtonRepository.pushCount)
        }

    @Test
    fun snoozeOneHourCallsRepoWithLatestDoorEventTimestamp() =
        runTest(testDispatcher) {
            doorRepository.setCurrentDoorEvent(
                DoorEvent(lastChangeTimeSeconds = 12345L),
            )
            val viewModel = createViewModel(authState = authenticated())
            advanceUntilIdle() // let init's collector cache the door event

            viewModel.snoozeNotificationsForOneHour()
            advanceUntilIdle()

            assertEquals(1, snoozeRepository.snoozeCount)
            assertEquals(12345L, snoozeRepository.snoozeCalls.last().snoozeEventTimestampSeconds)
        }

    @Test
    fun accessGrantedIsNullBeforeAllowlistResolves() =
        runTest(testDispatcher) {
            val viewModel = createViewModel(authState = authenticated())
            advanceUntilIdle()
            assertNull(viewModel.accessGranted.value)
        }

    @Test
    fun accessGrantedReflectsAllowlistTrue() =
        runTest(testDispatcher) {
            val viewModel = createViewModel(authState = authenticated())
            featureAllowlistRepository.setAllowlist(FeatureAllowlist(functionList = true, developer = false))
            advanceUntilIdle()
            assertTrue(viewModel.accessGranted.value == true)
        }

    @Test
    fun accessGrantedReflectsAllowlistFalse() =
        runTest(testDispatcher) {
            val viewModel = createViewModel(authState = authenticated())
            featureAllowlistRepository.setAllowlist(FeatureAllowlist(functionList = false, developer = false))
            advanceUntilIdle()
            assertFalse(viewModel.accessGranted.value == true)
            // Distinguish "false" from "null"
            assertEquals(false, viewModel.accessGranted.value)
        }

    @Test
    fun accessGrantedClearsToNullWhenAllowlistClears() =
        runTest(testDispatcher) {
            val viewModel = createViewModel(authState = authenticated())
            featureAllowlistRepository.setAllowlist(FeatureAllowlist(functionList = true, developer = false))
            advanceUntilIdle()
            assertEquals(true, viewModel.accessGranted.value)
            featureAllowlistRepository.setAllowlist(null)
            advanceUntilIdle()
            assertNull(viewModel.accessGranted.value)
        }
}

private class NoopButtonHealthRepository : ButtonHealthRepository {
    override val buttonHealth: StateFlow<LoadingResult<ButtonHealth>> =
        MutableStateFlow(LoadingResult.Loading(null))

    override suspend fun fetchButtonHealth(): AppResult<ButtonHealth, ButtonHealthError> =
        AppResult.Success(ButtonHealth(ButtonHealthState.UNKNOWN, null))

    override fun applyFcmUpdate(update: ButtonHealth) {
        // no-op
    }
}
