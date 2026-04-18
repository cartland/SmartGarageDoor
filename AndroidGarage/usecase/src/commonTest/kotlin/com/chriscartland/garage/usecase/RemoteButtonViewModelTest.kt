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

import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.DisplayName
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.FirebaseIdToken
import com.chriscartland.garage.domain.model.RemoteButtonState
import com.chriscartland.garage.domain.model.SnoozeAction
import com.chriscartland.garage.domain.model.SnoozeDurationUIOption
import com.chriscartland.garage.domain.model.SnoozeState
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.testcommon.FakeAuthRepository
import com.chriscartland.garage.testcommon.FakeDoorRepository
import com.chriscartland.garage.testcommon.FakeRemoteButtonRepository
import com.chriscartland.garage.testcommon.FakeSnoozeRepository
import com.chriscartland.garage.testcommon.TestDispatcherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteButtonViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var remoteButtonRepository: FakeRemoteButtonRepository
    private lateinit var snoozeRepository: FakeSnoozeRepository
    private lateinit var doorRepository: FakeDoorRepository
    private lateinit var authRepository: FakeAuthRepository

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        remoteButtonRepository = FakeRemoteButtonRepository()
        snoozeRepository = FakeSnoozeRepository()
        doorRepository = FakeDoorRepository()
        authRepository = FakeAuthRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createAuthenticatedViewModel(): DefaultRemoteButtonViewModel =
        createViewModel(
            authState = AuthState.Authenticated(
                user = User(
                    name = DisplayName("Test"),
                    email = Email("test@test.com"),
                    idToken = FirebaseIdToken(idToken = "token", exp = Long.MAX_VALUE),
                ),
            ),
        )

    private fun createViewModel(authState: AuthState = AuthState.Unauthenticated): DefaultRemoteButtonViewModel {
        authRepository.setAuthState(authState)
        val ensureFreshIdToken = EnsureFreshIdTokenUseCase(authRepository)
        val vm = DefaultRemoteButtonViewModel(
            observeDoorEvents = ObserveDoorEventsUseCase(doorRepository),
            dispatchers = TestDispatcherProvider(testDispatcher),
            pushRemoteButtonUseCase = PushRemoteButtonUseCase(ensureFreshIdToken, authRepository, remoteButtonRepository),
            snoozeNotificationsUseCase = SnoozeNotificationsUseCase(ensureFreshIdToken, authRepository, snoozeRepository),
            fetchSnoozeStatusUseCase = FetchSnoozeStatusUseCase(snoozeRepository),
            observeSnoozeStateUseCase = ObserveSnoozeStateUseCase(snoozeRepository),
            appVersion = "test-version",
        )
        testDispatcher.scheduler.runCurrent()
        return vm
    }

    // --- Button state wiring ---

    @Test
    fun initialButtonStateIsReady() {
        val viewModel = createViewModel()
        assertEquals(RemoteButtonState.Ready, viewModel.buttonState.value)
    }

    @Test
    fun onButtonTapTransitionsToPreparing() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onButtonTap()
            testDispatcher.scheduler.runCurrent()

            assertEquals(RemoteButtonState.Preparing, viewModel.buttonState.value)
        }

    @Test
    fun confirmTriggersPushAndTransitionsToSendingToDoor() =
        runTest {
            val viewModel = createAuthenticatedViewModel()
            assertEquals(0, remoteButtonRepository.pushCount)

            // Prepare and confirm
            viewModel.onButtonTap()
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(ButtonStateMachine.DEFAULT_PREPARING_DELAY + 1)
            testDispatcher.scheduler.runCurrent()
            assertEquals(RemoteButtonState.AwaitingConfirmation, viewModel.buttonState.value)

            // Confirm — triggers UseCase → repository sets SENDING→yield→IDLE
            viewModel.onButtonTap()
            testDispatcher.scheduler.runCurrent()

            // After the fake repository completes (SENDING → yield → IDLE),
            // the state machine transitions: SendingToServer → SendingToDoor.
            assertEquals(RemoteButtonState.SendingToDoor, viewModel.buttonState.value)
            assertEquals(1, remoteButtonRepository.pushCount)
        }

    @Test
    fun confirmWhenNotAuthenticatedResetsToReady() =
        runTest {
            val viewModel = createViewModel(authState = AuthState.Unauthenticated)

            viewModel.onButtonTap()
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(ButtonStateMachine.DEFAULT_PREPARING_DELAY + 1)
            testDispatcher.scheduler.runCurrent()
            viewModel.onButtonTap()
            testDispatcher.scheduler.runCurrent()

            // UseCase fails auth check, ViewModel resets state machine.
            assertEquals(RemoteButtonState.Ready, viewModel.buttonState.value)
            assertEquals(0, remoteButtonRepository.pushCount)
        }

    @Test
    fun confirmWhenNetworkFailsShowsServerFailed() =
        runTest {
            remoteButtonRepository.setPushSucceeds(false)
            val viewModel = createAuthenticatedViewModel()

            viewModel.onButtonTap()
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(ButtonStateMachine.DEFAULT_PREPARING_DELAY + 1)
            testDispatcher.scheduler.runCurrent()
            viewModel.onButtonTap()
            testDispatcher.scheduler.runCurrent()

            // UseCase returns NetworkFailed, ViewModel calls onNetworkFailed().
            assertEquals(RemoteButtonState.ServerFailed, viewModel.buttonState.value)
            assertEquals(1, remoteButtonRepository.pushCount)
        }

    @Test
    fun resetButtonReturnsToReady() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onButtonTap()
            testDispatcher.scheduler.runCurrent()
            assertEquals(RemoteButtonState.Preparing, viewModel.buttonState.value)

            viewModel.resetButton()
            testDispatcher.scheduler.runCurrent()
            assertEquals(RemoteButtonState.Ready, viewModel.buttonState.value)
        }

    // --- Snooze state ---

    @Test
    fun initialSnoozeStateIsLoading() {
        val viewModel = createViewModel()
        assertEquals(SnoozeState.Loading, viewModel.snoozeState.value)
    }

    @Test
    fun initialSnoozeActionIsIdle() {
        val viewModel = createViewModel()
        assertEquals(SnoozeAction.Idle, viewModel.snoozeAction.value)
    }

    @Test
    fun snoozeStateFollowsRepository() =
        runTest {
            val viewModel = createViewModel()

            snoozeRepository.setSnoozeState(SnoozeState.Snoozing(12345L))
            testDispatcher.scheduler.runCurrent()
            assertEquals(SnoozeState.Snoozing(12345L), viewModel.snoozeState.value)

            snoozeRepository.setSnoozeState(SnoozeState.NotSnoozing)
            testDispatcher.scheduler.runCurrent()
            assertEquals(SnoozeState.NotSnoozing, viewModel.snoozeState.value)
        }

    @Test
    fun initDoesNotFetchSnoozeStatus() {
        // First fetch is driven by NetworkSnoozeRepository.init on app scope,
        // not by the ViewModel. Running it from viewModelScope risked
        // cancellation mid-fetch stranding the singleton at Loading.
        createViewModel()
        assertEquals(0, snoozeRepository.fetchCount)
    }

    @Test
    fun fetchSnoozeStatusCallsThroughToRepository() =
        runTest {
            val viewModel = createViewModel()
            assertEquals(0, snoozeRepository.fetchCount)

            viewModel.fetchSnoozeStatus()
            testDispatcher.scheduler.runCurrent()

            assertEquals(1, snoozeRepository.fetchCount)
        }

    @Test
    fun snoozeActionTransitionsToSendingThenSucceeded() =
        runTest {
            val viewModel = createAuthenticatedViewModel()
            doorRepository.setCurrentDoorEvent(DoorEvent(lastChangeTimeSeconds = 1000L))
            testDispatcher.scheduler.runCurrent()
            assertEquals(SnoozeAction.Idle, viewModel.snoozeAction.value)

            viewModel.snoozeOpenDoorsNotifications(SnoozeDurationUIOption.OneHour)
            testDispatcher.scheduler.runCurrent()

            assertEquals(true, viewModel.snoozeAction.value is SnoozeAction.Succeeded)
        }

    @Test
    fun snoozeActionSucceededAutoResetsToIdleAfter10Seconds() =
        runTest {
            val viewModel = createAuthenticatedViewModel()
            doorRepository.setCurrentDoorEvent(DoorEvent(lastChangeTimeSeconds = 1000L))
            testDispatcher.scheduler.runCurrent()

            viewModel.snoozeOpenDoorsNotifications(SnoozeDurationUIOption.OneHour)
            testDispatcher.scheduler.runCurrent()
            assertEquals(true, viewModel.snoozeAction.value is SnoozeAction.Succeeded)

            advanceTimeBy(9_000)
            testDispatcher.scheduler.runCurrent()
            assertEquals(true, viewModel.snoozeAction.value is SnoozeAction.Succeeded)

            advanceTimeBy(2_000)
            testDispatcher.scheduler.runCurrent()
            assertEquals(SnoozeAction.Idle, viewModel.snoozeAction.value)
        }

    @Test
    fun snoozeActionFailedNotAuthenticatedAutoResetsToIdle() =
        runTest {
            val viewModel = createViewModel(authState = AuthState.Unauthenticated)

            viewModel.snoozeOpenDoorsNotifications(SnoozeDurationUIOption.OneHour)
            testDispatcher.scheduler.runCurrent()
            assertEquals(SnoozeAction.Failed.NotAuthenticated, viewModel.snoozeAction.value)

            advanceTimeBy(11_000)
            testDispatcher.scheduler.runCurrent()
            assertEquals(SnoozeAction.Idle, viewModel.snoozeAction.value)
        }

    @Test
    fun snoozeActionFailedNetworkErrorWhenRepositoryReturnsFalse() =
        runTest {
            val viewModel = createAuthenticatedViewModel()
            doorRepository.setCurrentDoorEvent(DoorEvent(lastChangeTimeSeconds = 1000L))
            snoozeRepository.setSnoozeResult(false)
            testDispatcher.scheduler.runCurrent()

            viewModel.snoozeOpenDoorsNotifications(SnoozeDurationUIOption.OneHour)
            testDispatcher.scheduler.runCurrent()

            assertEquals(SnoozeAction.Failed.NetworkError, viewModel.snoozeAction.value)
        }

    @Test
    fun snoozeActionWithNoneDurationDoesNotShowSnoozingTime() =
        runTest {
            val viewModel = createAuthenticatedViewModel()
            doorRepository.setCurrentDoorEvent(DoorEvent(lastChangeTimeSeconds = 1000L))
            testDispatcher.scheduler.runCurrent()

            viewModel.snoozeOpenDoorsNotifications(SnoozeDurationUIOption.None)
            testDispatcher.scheduler.runCurrent()

            val action = viewModel.snoozeAction.value
            assertEquals(true, action is SnoozeAction.Succeeded.Cleared)
        }

    @Test
    fun snoozeActionFailedMissingDataWhenNoDoorEvent() =
        runTest {
            val viewModel = createAuthenticatedViewModel()

            viewModel.snoozeOpenDoorsNotifications(SnoozeDurationUIOption.OneHour)
            testDispatcher.scheduler.runCurrent()
            assertEquals(SnoozeAction.Failed.MissingData, viewModel.snoozeAction.value)
        }
}
