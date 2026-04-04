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

package com.chriscartland.garage.remotebutton

import com.chriscartland.garage.auth.AuthRepository
import com.chriscartland.garage.coroutines.TestDispatcherProvider
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.PushStatus
import com.chriscartland.garage.domain.model.RequestStatus
import com.chriscartland.garage.domain.model.SnoozeRequestStatus
import com.chriscartland.garage.door.DoorRepository
import com.chriscartland.garage.usecase.EnsureFreshIdTokenUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteButtonViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var pushStatusFlow: MutableStateFlow<PushStatus>
    private lateinit var snoozeStatusFlow: MutableStateFlow<SnoozeRequestStatus>
    private lateinit var snoozeEndTimeFlow: MutableStateFlow<Long>
    private lateinit var doorPositionFlow: MutableStateFlow<DoorPosition>
    private lateinit var doorEventFlow: MutableStateFlow<DoorEvent>

    private lateinit var pushRepository: PushRepository
    private lateinit var doorRepository: DoorRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        pushStatusFlow = MutableStateFlow(PushStatus.IDLE)
        snoozeStatusFlow = MutableStateFlow(SnoozeRequestStatus.IDLE)
        snoozeEndTimeFlow = MutableStateFlow(0L)
        doorPositionFlow = MutableStateFlow(DoorPosition.CLOSED)
        doorEventFlow = MutableStateFlow(DoorEvent(doorPosition = DoorPosition.CLOSED))

        pushRepository = mock(PushRepository::class.java)
        `when`(pushRepository.pushButtonStatus).thenReturn(pushStatusFlow)
        `when`(pushRepository.snoozeRequestStatus).thenReturn(snoozeStatusFlow)
        `when`(pushRepository.snoozeEndTimeSeconds).thenReturn(snoozeEndTimeFlow)

        doorRepository = mock(DoorRepository::class.java)
        `when`(doorRepository.currentDoorPosition).thenReturn(doorPositionFlow)
        `when`(doorRepository.currentDoorEvent).thenReturn(doorEventFlow)
        `when`(doorRepository.recentDoorEvents).thenReturn(MutableStateFlow(emptyList()))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): RemoteButtonViewModelImpl {
        val vm = RemoteButtonViewModelImpl(
            pushRepository,
            doorRepository,
            TestDispatcherProvider(testDispatcher),
            EnsureFreshIdTokenUseCase(),
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
    fun initialSnoozeRequestStatusIsIdle() {
        val viewModel = createViewModel()
        assertEquals(SnoozeRequestStatus.IDLE, viewModel.snoozeRequestStatus.value)
    }

    @Test
    fun pushStatusSendingTransitionsRequestStatusToSending() =
        runTest {
            val viewModel = createViewModel()

            pushStatusFlow.value = PushStatus.SENDING
            testDispatcher.scheduler.runCurrent()

            assertEquals(RequestStatus.SENDING, viewModel.requestStatus.value)
        }

    @Test
    fun pushStatusIdleAfterSendingTransitionsToSent() =
        runTest {
            val viewModel = createViewModel()

            pushStatusFlow.value = PushStatus.SENDING
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.SENDING, viewModel.requestStatus.value)

            pushStatusFlow.value = PushStatus.IDLE
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.SENT, viewModel.requestStatus.value)
        }

    @Test
    fun doorPositionChangeDuringSendingTransitionsToReceived() =
        runTest {
            val viewModel = createViewModel()

            pushStatusFlow.value = PushStatus.SENDING
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.SENDING, viewModel.requestStatus.value)

            doorPositionFlow.value = DoorPosition.OPENING
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.RECEIVED, viewModel.requestStatus.value)
        }

    @Test
    fun doorPositionChangeDuringSentTransitionsToReceived() =
        runTest {
            val viewModel = createViewModel()

            pushStatusFlow.value = PushStatus.SENDING
            testDispatcher.scheduler.runCurrent()

            pushStatusFlow.value = PushStatus.IDLE
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.SENT, viewModel.requestStatus.value)

            doorPositionFlow.value = DoorPosition.OPENING
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.RECEIVED, viewModel.requestStatus.value)
        }

    @Test
    fun doorPositionChangeDuringNoneDoesNotChangeState() =
        runTest {
            val viewModel = createViewModel()
            assertEquals(RequestStatus.NONE, viewModel.requestStatus.value)

            doorPositionFlow.value = DoorPosition.OPENING
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.NONE, viewModel.requestStatus.value)
        }

    @Test
    fun resetRemoteButtonSetsStatusToNone() =
        runTest {
            val viewModel = createViewModel()

            pushStatusFlow.value = PushStatus.SENDING
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.SENDING, viewModel.requestStatus.value)

            viewModel.resetRemoteButton()
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.NONE, viewModel.requestStatus.value)
        }

    @Test
    fun snoozeRequestStatusFollowsPushRepository() =
        runTest {
            val viewModel = createViewModel()

            snoozeStatusFlow.value = SnoozeRequestStatus.SENDING
            testDispatcher.scheduler.runCurrent()
            assertEquals(SnoozeRequestStatus.SENDING, viewModel.snoozeRequestStatus.value)

            snoozeStatusFlow.value = SnoozeRequestStatus.ERROR
            testDispatcher.scheduler.runCurrent()
            assertEquals(SnoozeRequestStatus.ERROR, viewModel.snoozeRequestStatus.value)

            snoozeStatusFlow.value = SnoozeRequestStatus.IDLE
            testDispatcher.scheduler.runCurrent()
            assertEquals(SnoozeRequestStatus.IDLE, viewModel.snoozeRequestStatus.value)
        }

    @Test
    fun snoozeEndTimeSecondsComesFromPushRepository() {
        val viewModel = createViewModel()

        snoozeEndTimeFlow.value = 12345L
        assertEquals(12345L, viewModel.snoozeEndTimeSeconds.value)
    }

    @Test
    fun pushRemoteButtonDoesNothingWhenNotAuthenticated() =
        runTest {
            val viewModel = createViewModel()
            val authRepository = mock(AuthRepository::class.java)
            `when`(authRepository.authState).thenReturn(
                MutableStateFlow<AuthState>(AuthState.Unauthenticated),
            )

            viewModel.pushRemoteButton(authRepository)
            testDispatcher.scheduler.runCurrent()

            // Status should remain NONE since auth check fails before sending
            assertEquals(RequestStatus.NONE, viewModel.requestStatus.value)
        }

    @Test
    fun pushIdleAfterNoneRemainsNone() =
        runTest {
            val viewModel = createViewModel()
            assertEquals(RequestStatus.NONE, viewModel.requestStatus.value)

            pushStatusFlow.value = PushStatus.IDLE
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.NONE, viewModel.requestStatus.value)
        }

    @Test
    fun fullHappyPathSendToSentToReceived() =
        runTest {
            val viewModel = createViewModel()

            assertEquals(RequestStatus.NONE, viewModel.requestStatus.value)

            pushStatusFlow.value = PushStatus.SENDING
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.SENDING, viewModel.requestStatus.value)

            pushStatusFlow.value = PushStatus.IDLE
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.SENT, viewModel.requestStatus.value)

            doorPositionFlow.value = DoorPosition.OPENING
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.RECEIVED, viewModel.requestStatus.value)
        }

    // --- Timeout tests ---

    @Test
    fun sendingTimesOutToSendingTimeout() =
        runTest {
            val viewModel = createViewModel()

            pushStatusFlow.value = PushStatus.SENDING
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.SENDING, viewModel.requestStatus.value)

            // Advance past the 10-second timeout
            advanceTimeBy(10_001)
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.SENDING_TIMEOUT, viewModel.requestStatus.value)
        }

    @Test
    fun sentTimesOutToSentTimeout() =
        runTest {
            val viewModel = createViewModel()

            pushStatusFlow.value = PushStatus.SENDING
            testDispatcher.scheduler.runCurrent()

            pushStatusFlow.value = PushStatus.IDLE
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.SENT, viewModel.requestStatus.value)

            advanceTimeBy(10_001)
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.SENT_TIMEOUT, viewModel.requestStatus.value)
        }

    @Test
    fun sendingTimeoutResetsToNone() =
        runTest {
            val viewModel = createViewModel()

            pushStatusFlow.value = PushStatus.SENDING
            testDispatcher.scheduler.runCurrent()

            // First timeout: SENDING -> SENDING_TIMEOUT
            advanceTimeBy(10_001)
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.SENDING_TIMEOUT, viewModel.requestStatus.value)

            // Second timeout: SENDING_TIMEOUT -> NONE
            advanceTimeBy(10_001)
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.NONE, viewModel.requestStatus.value)
        }

    @Test
    fun sentTimeoutResetsToNone() =
        runTest {
            val viewModel = createViewModel()

            pushStatusFlow.value = PushStatus.SENDING
            testDispatcher.scheduler.runCurrent()
            pushStatusFlow.value = PushStatus.IDLE
            testDispatcher.scheduler.runCurrent()

            // First timeout: SENT -> SENT_TIMEOUT
            advanceTimeBy(10_001)
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.SENT_TIMEOUT, viewModel.requestStatus.value)

            // Second timeout: SENT_TIMEOUT -> NONE
            advanceTimeBy(10_001)
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.NONE, viewModel.requestStatus.value)
        }

    @Test
    fun receivedResetsToNoneAfterTimeout() =
        runTest {
            val viewModel = createViewModel()

            pushStatusFlow.value = PushStatus.SENDING
            testDispatcher.scheduler.runCurrent()
            doorPositionFlow.value = DoorPosition.OPENING
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.RECEIVED, viewModel.requestStatus.value)

            advanceTimeBy(10_001)
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.NONE, viewModel.requestStatus.value)
        }

    @Test
    fun doorMovementBeforeTimeoutCancelsSendingTimeout() =
        runTest {
            val viewModel = createViewModel()

            pushStatusFlow.value = PushStatus.SENDING
            testDispatcher.scheduler.runCurrent()

            // Door moves before the 10s timeout
            advanceTimeBy(5_000)
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.SENDING, viewModel.requestStatus.value)

            doorPositionFlow.value = DoorPosition.OPENING
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.RECEIVED, viewModel.requestStatus.value)

            // Original timeout fires but should not override RECEIVED
            advanceTimeBy(6_000)
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.RECEIVED, viewModel.requestStatus.value)
        }

    // --- Edge case tests ---

    @Test
    fun resetDuringTimeoutCancelsTimeout() =
        runTest {
            val viewModel = createViewModel()

            pushStatusFlow.value = PushStatus.SENDING
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.SENDING, viewModel.requestStatus.value)

            // Reset before timeout fires
            viewModel.resetRemoteButton()
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.NONE, viewModel.requestStatus.value)

            // Advance past where timeout would have fired
            advanceTimeBy(15_000)
            testDispatcher.scheduler.runCurrent()
            // Should still be NONE — timeout was cancelled by reset
            assertEquals(RequestStatus.NONE, viewModel.requestStatus.value)
        }

    @Test
    fun rapidStateChangesOnlyFinalTimeoutActive() =
        runTest {
            val viewModel = createViewModel()

            // SENDING
            pushStatusFlow.value = PushStatus.SENDING
            testDispatcher.scheduler.runCurrent()

            // Quickly transition to SENT
            pushStatusFlow.value = PushStatus.IDLE
            testDispatcher.scheduler.runCurrent()

            // Quickly transition to RECEIVED
            doorPositionFlow.value = DoorPosition.OPENING
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.RECEIVED, viewModel.requestStatus.value)

            // Only the RECEIVED timeout should be active (10s → NONE)
            advanceTimeBy(10_001)
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.NONE, viewModel.requestStatus.value)
        }

    @Test
    fun multipleDoorPositionChangesDuringSentStaysReceived() =
        runTest {
            val viewModel = createViewModel()

            pushStatusFlow.value = PushStatus.SENDING
            testDispatcher.scheduler.runCurrent()
            pushStatusFlow.value = PushStatus.IDLE
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.SENT, viewModel.requestStatus.value)

            // First door change → RECEIVED
            doorPositionFlow.value = DoorPosition.OPENING
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.RECEIVED, viewModel.requestStatus.value)

            // Second door change → still RECEIVED (not oscillating)
            doorPositionFlow.value = DoorPosition.OPEN
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.RECEIVED, viewModel.requestStatus.value)

            // Third door change → still RECEIVED
            doorPositionFlow.value = DoorPosition.CLOSING
            testDispatcher.scheduler.runCurrent()
            assertEquals(RequestStatus.RECEIVED, viewModel.requestStatus.value)
        }
}
