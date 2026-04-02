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
import com.chriscartland.garage.auth.AuthState
import com.chriscartland.garage.door.DoorEvent
import com.chriscartland.garage.door.DoorPosition
import com.chriscartland.garage.door.DoorRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteButtonViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var pushStatusFlow: MutableStateFlow<PushStatus>
    private lateinit var snoozeStatusFlow: MutableStateFlow<SnoozeRequestStatus>
    private lateinit var snoozeEndTimeFlow: MutableStateFlow<Long>
    private lateinit var doorPositionFlow: MutableSharedFlow<DoorPosition>
    private lateinit var doorEventFlow: MutableSharedFlow<DoorEvent>

    private lateinit var pushRepository: PushRepository
    private lateinit var doorRepository: DoorRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        pushStatusFlow = MutableStateFlow(PushStatus.IDLE)
        snoozeStatusFlow = MutableStateFlow(SnoozeRequestStatus.IDLE)
        snoozeEndTimeFlow = MutableStateFlow(0L)
        doorPositionFlow = MutableSharedFlow(replay = 1)
        doorEventFlow = MutableSharedFlow(replay = 1)

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

    private fun createViewModel(): RemoteButtonViewModelImpl =
        RemoteButtonViewModelImpl(pushRepository, doorRepository)

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
    fun pushStatusSendingTransitionsRequestStatusToSending() {
        val viewModel = createViewModel()

        pushStatusFlow.value = PushStatus.SENDING

        assertEquals(RequestStatus.SENDING, viewModel.requestStatus.value)
    }

    @Test
    fun pushStatusIdleAfterSendingTransitionsToSent() {
        val viewModel = createViewModel()

        pushStatusFlow.value = PushStatus.SENDING
        assertEquals(RequestStatus.SENDING, viewModel.requestStatus.value)

        pushStatusFlow.value = PushStatus.IDLE
        assertEquals(RequestStatus.SENT, viewModel.requestStatus.value)
    }

    @Test
    fun doorPositionChangeDuringSendingTransitionsToReceived() {
        val viewModel = createViewModel()

        pushStatusFlow.value = PushStatus.SENDING
        assertEquals(RequestStatus.SENDING, viewModel.requestStatus.value)

        doorPositionFlow.tryEmit(DoorPosition.OPENING)
        assertEquals(RequestStatus.RECEIVED, viewModel.requestStatus.value)
    }

    @Test
    fun doorPositionChangeDuringSentTransitionsToReceived() {
        val viewModel = createViewModel()

        pushStatusFlow.value = PushStatus.SENDING
        pushStatusFlow.value = PushStatus.IDLE
        assertEquals(RequestStatus.SENT, viewModel.requestStatus.value)

        doorPositionFlow.tryEmit(DoorPosition.OPENING)
        assertEquals(RequestStatus.RECEIVED, viewModel.requestStatus.value)
    }

    @Test
    fun doorPositionChangeDuringNoneDoesNotChangeState() {
        val viewModel = createViewModel()
        assertEquals(RequestStatus.NONE, viewModel.requestStatus.value)

        doorPositionFlow.tryEmit(DoorPosition.OPENING)
        assertEquals(RequestStatus.NONE, viewModel.requestStatus.value)
    }

    @Test
    fun resetRemoteButtonSetsStatusToNone() {
        val viewModel = createViewModel()

        pushStatusFlow.value = PushStatus.SENDING
        assertEquals(RequestStatus.SENDING, viewModel.requestStatus.value)

        viewModel.resetRemoteButton()
        assertEquals(RequestStatus.NONE, viewModel.requestStatus.value)
    }

    @Test
    fun snoozeRequestStatusFollowsPushRepository() {
        val viewModel = createViewModel()

        snoozeStatusFlow.value = SnoozeRequestStatus.SENDING
        assertEquals(SnoozeRequestStatus.SENDING, viewModel.snoozeRequestStatus.value)

        snoozeStatusFlow.value = SnoozeRequestStatus.ERROR
        assertEquals(SnoozeRequestStatus.ERROR, viewModel.snoozeRequestStatus.value)

        snoozeStatusFlow.value = SnoozeRequestStatus.IDLE
        assertEquals(SnoozeRequestStatus.IDLE, viewModel.snoozeRequestStatus.value)
    }

    @Test
    fun snoozeEndTimeSecondsComesFromPushRepository() {
        val viewModel = createViewModel()

        snoozeEndTimeFlow.value = 12345L
        assertEquals(12345L, viewModel.snoozeEndTimeSeconds.value)
    }

    @Test
    fun pushRemoteButtonDoesNothingWhenNotAuthenticated() {
        val viewModel = createViewModel()
        val authRepository = mock(AuthRepository::class.java)
        `when`(authRepository.authState).thenReturn(
            MutableStateFlow<AuthState>(AuthState.Unauthenticated),
        )

        viewModel.pushRemoteButton(authRepository)

        // Status should remain NONE since auth check fails before sending
        assertEquals(RequestStatus.NONE, viewModel.requestStatus.value)
    }

    @Test
    fun pushIdleAfterNoneRemainsNone() {
        val viewModel = createViewModel()
        assertEquals(RequestStatus.NONE, viewModel.requestStatus.value)

        pushStatusFlow.value = PushStatus.IDLE
        assertEquals(RequestStatus.NONE, viewModel.requestStatus.value)
    }

    @Test
    fun doorPositionChangeDuringSendingTimeoutTransitionsToReceived() {
        val viewModel = createViewModel()

        pushStatusFlow.value = PushStatus.SENDING
        assertEquals(RequestStatus.SENDING, viewModel.requestStatus.value)

        doorPositionFlow.tryEmit(DoorPosition.OPEN)
        assertEquals(RequestStatus.RECEIVED, viewModel.requestStatus.value)
    }

    @Test
    fun fullHappyPathSendToSentToReceived() {
        val viewModel = createViewModel()

        assertEquals(RequestStatus.NONE, viewModel.requestStatus.value)

        pushStatusFlow.value = PushStatus.SENDING
        assertEquals(RequestStatus.SENDING, viewModel.requestStatus.value)

        pushStatusFlow.value = PushStatus.IDLE
        assertEquals(RequestStatus.SENT, viewModel.requestStatus.value)

        doorPositionFlow.tryEmit(DoorPosition.OPENING)
        assertEquals(RequestStatus.RECEIVED, viewModel.requestStatus.value)
    }
}
