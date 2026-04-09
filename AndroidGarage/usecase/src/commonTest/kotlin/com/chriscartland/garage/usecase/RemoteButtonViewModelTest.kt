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
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.PushStatus
import com.chriscartland.garage.domain.model.RequestStatus
import com.chriscartland.garage.domain.model.SnoozeAction
import com.chriscartland.garage.domain.model.SnoozeState
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

private val TIMEOUT = RemoteButtonStateMachine.DEFAULT_TIMEOUT_MILLIS

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

    private fun createViewModel(authState: AuthState = AuthState.Unauthenticated): DefaultRemoteButtonViewModel {
        authRepository.setAuthState(authState)
        val ensureFreshIdToken = EnsureFreshIdTokenUseCase(authRepository)
        val vm = DefaultRemoteButtonViewModel(
            remoteButtonRepository,
            doorRepository,
            TestDispatcherProvider(testDispatcher),
            PushRemoteButtonUseCase(ensureFreshIdToken, authRepository, remoteButtonRepository),
            SnoozeNotificationsUseCase(ensureFreshIdToken, authRepository, snoozeRepository),
            FetchSnoozeStatusUseCase(snoozeRepository),
            ObserveSnoozeStateUseCase(snoozeRepository),
        )
        testDispatcher.scheduler.runCurrent()
        return vm
    }

    @Test
    fun initialRequestStatusIsNone() {
        val viewModel = createViewModel()
        assertEquals(RequestStatus.NONE, viewModel.requestStatus.value)
    }

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
    fun fetchSnoozeStatusCallsThroughToRepository() =
        runTest {
            val viewModel = createViewModel()
            assertEquals(0, snoozeRepository.fetchCount)

            viewModel.fetchSnoozeStatus()
            testDispatcher.scheduler.runCurrent()

            assertEquals(1, snoozeRepository.fetchCount)
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
    fun pushStatusSendingTransitionsRequestStatusToSending() =
        runTest {
            val viewModel = createViewModel()

            remoteButtonRepository.setPushStatus(PushStatus.SENDING)
            testDispatcher.scheduler.runCurrent()

            assertEquals(RequestStatus.SENDING, viewModel.requestStatus.value)
        }

    @Test
    fun pushStatusIdleAfterSendingTransitionsToSent() =
        runTest {
            val viewModel = createViewModel()

            remoteButtonRepository.setPushStatus(PushStatus.SENDING)
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.SENDING, viewModel.requestStatus.value)

            remoteButtonRepository.setPushStatus(PushStatus.IDLE)
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.SENT, viewModel.requestStatus.value)
        }

    @Test
    fun doorPositionChangeDuringSendingTransitionsToReceived() =
        runTest {
            val viewModel = createViewModel()

            remoteButtonRepository.setPushStatus(PushStatus.SENDING)
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.SENDING, viewModel.requestStatus.value)

            doorRepository.setCurrentDoorEvent(DoorEvent(doorPosition = DoorPosition.OPENING))
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.RECEIVED, viewModel.requestStatus.value)
        }

    @Test
    fun doorPositionChangeDuringSentTransitionsToReceived() =
        runTest {
            val viewModel = createViewModel()

            remoteButtonRepository.setPushStatus(PushStatus.SENDING)
            testDispatcher.scheduler.runCurrent()

            remoteButtonRepository.setPushStatus(PushStatus.IDLE)
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.SENT, viewModel.requestStatus.value)

            doorRepository.setCurrentDoorEvent(DoorEvent(doorPosition = DoorPosition.OPENING))
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.RECEIVED, viewModel.requestStatus.value)
        }

    @Test
    fun doorPositionChangeDuringNoneDoesNotChangeState() =
        runTest {
            val viewModel = createViewModel()
            assertEquals(RequestStatus.NONE, viewModel.requestStatus.value)

            doorRepository.setCurrentDoorEvent(DoorEvent(doorPosition = DoorPosition.OPENING))
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.NONE, viewModel.requestStatus.value)
        }

    @Test
    fun resetRemoteButtonSetsStatusToNone() =
        runTest {
            val viewModel = createViewModel()

            remoteButtonRepository.setPushStatus(PushStatus.SENDING)
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.SENDING, viewModel.requestStatus.value)

            viewModel.resetRemoteButton()
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.NONE, viewModel.requestStatus.value)
        }

    @Test
    fun pushRemoteButtonDoesNothingWhenNotAuthenticated() =
        runTest {
            val viewModel = createViewModel(authState = AuthState.Unauthenticated)

            viewModel.pushRemoteButton()
            testDispatcher.scheduler.runCurrent()

            // Status should remain NONE since auth check fails before sending
            assertEquals(RequestStatus.NONE, viewModel.requestStatus.value)
        }

    @Test
    fun pushIdleAfterNoneRemainsNone() =
        runTest {
            val viewModel = createViewModel()
            assertEquals(RequestStatus.NONE, viewModel.requestStatus.value)

            remoteButtonRepository.setPushStatus(PushStatus.IDLE)
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.NONE, viewModel.requestStatus.value)
        }

    @Test
    fun fullHappyPathSendToSentToReceived() =
        runTest {
            val viewModel = createViewModel()

            assertEquals(RequestStatus.NONE, viewModel.requestStatus.value)

            remoteButtonRepository.setPushStatus(PushStatus.SENDING)
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.SENDING, viewModel.requestStatus.value)

            remoteButtonRepository.setPushStatus(PushStatus.IDLE)
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.SENT, viewModel.requestStatus.value)

            doorRepository.setCurrentDoorEvent(DoorEvent(doorPosition = DoorPosition.OPENING))
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.RECEIVED, viewModel.requestStatus.value)
        }

    // --- Timeout tests ---

    @Test
    fun sendingTimesOutToSendingTimeout() =
        runTest {
            val viewModel = createViewModel()

            remoteButtonRepository.setPushStatus(PushStatus.SENDING)
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.SENDING, viewModel.requestStatus.value)

            advanceTimeBy(TIMEOUT + 1)
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.SENDING_TIMEOUT, viewModel.requestStatus.value)
        }

    @Test
    fun sentTimesOutToSentTimeout() =
        runTest {
            val viewModel = createViewModel()

            remoteButtonRepository.setPushStatus(PushStatus.SENDING)
            testDispatcher.scheduler.runCurrent()

            remoteButtonRepository.setPushStatus(PushStatus.IDLE)
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.SENT, viewModel.requestStatus.value)

            advanceTimeBy(TIMEOUT + 1)
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.SENT_TIMEOUT, viewModel.requestStatus.value)
        }

    @Test
    fun sendingTimeoutResetsToNone() =
        runTest {
            val viewModel = createViewModel()

            remoteButtonRepository.setPushStatus(PushStatus.SENDING)
            testDispatcher.scheduler.runCurrent()

            advanceTimeBy(TIMEOUT + 1)
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.SENDING_TIMEOUT, viewModel.requestStatus.value)

            advanceTimeBy(TIMEOUT + 1)
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.NONE, viewModel.requestStatus.value)
        }

    @Test
    fun sentTimeoutResetsToNone() =
        runTest {
            val viewModel = createViewModel()

            remoteButtonRepository.setPushStatus(PushStatus.SENDING)
            testDispatcher.scheduler.runCurrent()
            remoteButtonRepository.setPushStatus(PushStatus.IDLE)
            testDispatcher.scheduler.runCurrent()

            advanceTimeBy(TIMEOUT + 1)
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.SENT_TIMEOUT, viewModel.requestStatus.value)

            advanceTimeBy(TIMEOUT + 1)
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.NONE, viewModel.requestStatus.value)
        }

    @Test
    fun receivedResetsToNoneAfterTimeout() =
        runTest {
            val viewModel = createViewModel()

            remoteButtonRepository.setPushStatus(PushStatus.SENDING)
            testDispatcher.scheduler.runCurrent()
            doorRepository.setCurrentDoorEvent(DoorEvent(doorPosition = DoorPosition.OPENING))
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.RECEIVED, viewModel.requestStatus.value)

            advanceTimeBy(TIMEOUT + 1)
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.NONE, viewModel.requestStatus.value)
        }

    @Test
    fun doorMovementBeforeTimeoutCancelsSendingTimeout() =
        runTest {
            val viewModel = createViewModel()

            remoteButtonRepository.setPushStatus(PushStatus.SENDING)
            testDispatcher.scheduler.runCurrent()

            advanceTimeBy(TIMEOUT / 2)
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.SENDING, viewModel.requestStatus.value)

            doorRepository.setCurrentDoorEvent(DoorEvent(doorPosition = DoorPosition.OPENING))
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.RECEIVED, viewModel.requestStatus.value)

            advanceTimeBy(TIMEOUT / 2 + 1_000)
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.RECEIVED, viewModel.requestStatus.value)
        }

    // --- Edge case tests ---

    @Test
    fun resetDuringTimeoutCancelsTimeout() =
        runTest {
            val viewModel = createViewModel()

            remoteButtonRepository.setPushStatus(PushStatus.SENDING)
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.SENDING, viewModel.requestStatus.value)

            viewModel.resetRemoteButton()
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.NONE, viewModel.requestStatus.value)

            advanceTimeBy(TIMEOUT + TIMEOUT / 2)
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.NONE, viewModel.requestStatus.value)
        }

    @Test
    fun rapidStateChangesOnlyFinalTimeoutActive() =
        runTest {
            val viewModel = createViewModel()

            remoteButtonRepository.setPushStatus(PushStatus.SENDING)
            testDispatcher.scheduler.runCurrent()

            remoteButtonRepository.setPushStatus(PushStatus.IDLE)
            testDispatcher.scheduler.runCurrent()

            doorRepository.setCurrentDoorEvent(DoorEvent(doorPosition = DoorPosition.OPENING))
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.RECEIVED, viewModel.requestStatus.value)

            advanceTimeBy(TIMEOUT + 1)
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.NONE, viewModel.requestStatus.value)
        }

    @Test
    fun multipleDoorPositionChangesDuringSentStaysReceived() =
        runTest {
            val viewModel = createViewModel()

            remoteButtonRepository.setPushStatus(PushStatus.SENDING)
            testDispatcher.scheduler.runCurrent()
            remoteButtonRepository.setPushStatus(PushStatus.IDLE)
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.SENT, viewModel.requestStatus.value)

            doorRepository.setCurrentDoorEvent(DoorEvent(doorPosition = DoorPosition.OPENING))
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.RECEIVED, viewModel.requestStatus.value)

            doorRepository.setCurrentDoorEvent(DoorEvent(doorPosition = DoorPosition.OPEN))
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.RECEIVED, viewModel.requestStatus.value)

            doorRepository.setCurrentDoorEvent(DoorEvent(doorPosition = DoorPosition.CLOSING))
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.RECEIVED, viewModel.requestStatus.value)
        }
}
