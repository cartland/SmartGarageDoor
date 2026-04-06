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

import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.PushStatus
import com.chriscartland.garage.domain.model.RequestStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteButtonStateMachineTest {
    private lateinit var pushStatusFlow: MutableStateFlow<PushStatus>
    private lateinit var doorPositionFlow: MutableStateFlow<DoorPosition>

    private fun TestScope.createStateMachine(): RemoteButtonStateMachine {
        pushStatusFlow = MutableStateFlow(PushStatus.IDLE)
        doorPositionFlow = MutableStateFlow(DoorPosition.CLOSED)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val sm = RemoteButtonStateMachine(
            pushButtonStatus = pushStatusFlow,
            doorPosition = doorPositionFlow,
            scope = backgroundScope,
            dispatcher = dispatcher,
        )
        testScheduler.runCurrent()
        return sm
    }

    @Test
    fun initialRequestStatusIsNone() =
        runTest {
            val sm = createStateMachine()
            assertEquals(RequestStatus.NONE, sm.requestStatus.value)
        }

    @Test
    fun pushStatusSendingTransitionsToSending() =
        runTest {
            val sm = createStateMachine()

            pushStatusFlow.value = PushStatus.SENDING
            testScheduler.runCurrent()

            assertEquals(RequestStatus.SENDING, sm.requestStatus.value)
        }

    @Test
    fun pushStatusIdleAfterSendingTransitionsToSent() =
        runTest {
            val sm = createStateMachine()

            pushStatusFlow.value = PushStatus.SENDING
            testScheduler.runCurrent()
            assertEquals(RequestStatus.SENDING, sm.requestStatus.value)

            pushStatusFlow.value = PushStatus.IDLE
            testScheduler.runCurrent()
            assertEquals(RequestStatus.SENT, sm.requestStatus.value)
        }

    @Test
    fun doorPositionChangeDuringSendingTransitionsToReceived() =
        runTest {
            val sm = createStateMachine()

            pushStatusFlow.value = PushStatus.SENDING
            testScheduler.runCurrent()
            assertEquals(RequestStatus.SENDING, sm.requestStatus.value)

            doorPositionFlow.value = DoorPosition.OPENING
            testScheduler.runCurrent()
            assertEquals(RequestStatus.RECEIVED, sm.requestStatus.value)
        }

    @Test
    fun doorPositionChangeDuringSentTransitionsToReceived() =
        runTest {
            val sm = createStateMachine()

            pushStatusFlow.value = PushStatus.SENDING
            testScheduler.runCurrent()
            pushStatusFlow.value = PushStatus.IDLE
            testScheduler.runCurrent()
            assertEquals(RequestStatus.SENT, sm.requestStatus.value)

            doorPositionFlow.value = DoorPosition.OPENING
            testScheduler.runCurrent()
            assertEquals(RequestStatus.RECEIVED, sm.requestStatus.value)
        }

    @Test
    fun doorPositionChangeDuringNoneDoesNotChangeState() =
        runTest {
            val sm = createStateMachine()
            assertEquals(RequestStatus.NONE, sm.requestStatus.value)

            doorPositionFlow.value = DoorPosition.OPENING
            testScheduler.runCurrent()
            assertEquals(RequestStatus.NONE, sm.requestStatus.value)
        }

    @Test
    fun resetSetsStatusToNone() =
        runTest {
            val sm = createStateMachine()

            pushStatusFlow.value = PushStatus.SENDING
            testScheduler.runCurrent()
            assertEquals(RequestStatus.SENDING, sm.requestStatus.value)

            sm.reset()
            testScheduler.runCurrent()
            assertEquals(RequestStatus.NONE, sm.requestStatus.value)
        }

    @Test
    fun pushIdleAfterNoneRemainsNone() =
        runTest {
            val sm = createStateMachine()
            assertEquals(RequestStatus.NONE, sm.requestStatus.value)

            pushStatusFlow.value = PushStatus.IDLE
            testScheduler.runCurrent()
            assertEquals(RequestStatus.NONE, sm.requestStatus.value)
        }

    @Test
    fun fullHappyPathSendToSentToReceived() =
        runTest {
            val sm = createStateMachine()
            assertEquals(RequestStatus.NONE, sm.requestStatus.value)

            pushStatusFlow.value = PushStatus.SENDING
            testScheduler.runCurrent()
            assertEquals(RequestStatus.SENDING, sm.requestStatus.value)

            pushStatusFlow.value = PushStatus.IDLE
            testScheduler.runCurrent()
            assertEquals(RequestStatus.SENT, sm.requestStatus.value)

            doorPositionFlow.value = DoorPosition.OPENING
            testScheduler.runCurrent()
            assertEquals(RequestStatus.RECEIVED, sm.requestStatus.value)
        }

    // --- Timeout tests ---

    @Test
    fun sendingTimesOutToSendingTimeout() =
        runTest {
            val sm = createStateMachine()

            pushStatusFlow.value = PushStatus.SENDING
            testScheduler.runCurrent()
            assertEquals(RequestStatus.SENDING, sm.requestStatus.value)

            advanceTimeBy(10_001)
            testScheduler.runCurrent()
            assertEquals(RequestStatus.SENDING_TIMEOUT, sm.requestStatus.value)
        }

    @Test
    fun sentTimesOutToSentTimeout() =
        runTest {
            val sm = createStateMachine()

            pushStatusFlow.value = PushStatus.SENDING
            testScheduler.runCurrent()
            pushStatusFlow.value = PushStatus.IDLE
            testScheduler.runCurrent()
            assertEquals(RequestStatus.SENT, sm.requestStatus.value)

            advanceTimeBy(10_001)
            testScheduler.runCurrent()
            assertEquals(RequestStatus.SENT_TIMEOUT, sm.requestStatus.value)
        }

    @Test
    fun sendingTimeoutResetsToNone() =
        runTest {
            val sm = createStateMachine()

            pushStatusFlow.value = PushStatus.SENDING
            testScheduler.runCurrent()

            // First timeout: SENDING -> SENDING_TIMEOUT
            advanceTimeBy(10_001)
            testScheduler.runCurrent()
            assertEquals(RequestStatus.SENDING_TIMEOUT, sm.requestStatus.value)

            // Second timeout: SENDING_TIMEOUT -> NONE
            advanceTimeBy(10_001)
            testScheduler.runCurrent()
            assertEquals(RequestStatus.NONE, sm.requestStatus.value)
        }

    @Test
    fun sentTimeoutResetsToNone() =
        runTest {
            val sm = createStateMachine()

            pushStatusFlow.value = PushStatus.SENDING
            testScheduler.runCurrent()
            pushStatusFlow.value = PushStatus.IDLE
            testScheduler.runCurrent()

            // First timeout: SENT -> SENT_TIMEOUT
            advanceTimeBy(10_001)
            testScheduler.runCurrent()
            assertEquals(RequestStatus.SENT_TIMEOUT, sm.requestStatus.value)

            // Second timeout: SENT_TIMEOUT -> NONE
            advanceTimeBy(10_001)
            testScheduler.runCurrent()
            assertEquals(RequestStatus.NONE, sm.requestStatus.value)
        }

    @Test
    fun receivedResetsToNoneAfterTimeout() =
        runTest {
            val sm = createStateMachine()

            pushStatusFlow.value = PushStatus.SENDING
            testScheduler.runCurrent()
            doorPositionFlow.value = DoorPosition.OPENING
            testScheduler.runCurrent()
            assertEquals(RequestStatus.RECEIVED, sm.requestStatus.value)

            advanceTimeBy(10_001)
            testScheduler.runCurrent()
            assertEquals(RequestStatus.NONE, sm.requestStatus.value)
        }

    @Test
    fun doorMovementBeforeTimeoutCancelsSendingTimeout() =
        runTest {
            val sm = createStateMachine()

            pushStatusFlow.value = PushStatus.SENDING
            testScheduler.runCurrent()

            // Door moves before the 10s timeout
            advanceTimeBy(5_000)
            testScheduler.runCurrent()
            assertEquals(RequestStatus.SENDING, sm.requestStatus.value)

            doorPositionFlow.value = DoorPosition.OPENING
            testScheduler.runCurrent()
            assertEquals(RequestStatus.RECEIVED, sm.requestStatus.value)

            // Original timeout fires but should not override RECEIVED
            advanceTimeBy(6_000)
            testScheduler.runCurrent()
            assertEquals(RequestStatus.RECEIVED, sm.requestStatus.value)
        }

    // --- Edge case tests ---

    @Test
    fun resetDuringTimeoutCancelsTimeout() =
        runTest {
            val sm = createStateMachine()

            pushStatusFlow.value = PushStatus.SENDING
            testScheduler.runCurrent()
            assertEquals(RequestStatus.SENDING, sm.requestStatus.value)

            // Reset before timeout fires
            sm.reset()
            testScheduler.runCurrent()
            assertEquals(RequestStatus.NONE, sm.requestStatus.value)

            // Advance past where timeout would have fired
            advanceTimeBy(15_000)
            testScheduler.runCurrent()
            // Should still be NONE — timeout was cancelled by reset
            assertEquals(RequestStatus.NONE, sm.requestStatus.value)
        }

    @Test
    fun rapidStateChangesOnlyFinalTimeoutActive() =
        runTest {
            val sm = createStateMachine()

            // SENDING
            pushStatusFlow.value = PushStatus.SENDING
            testScheduler.runCurrent()

            // Quickly transition to SENT
            pushStatusFlow.value = PushStatus.IDLE
            testScheduler.runCurrent()

            // Quickly transition to RECEIVED
            doorPositionFlow.value = DoorPosition.OPENING
            testScheduler.runCurrent()
            assertEquals(RequestStatus.RECEIVED, sm.requestStatus.value)

            // Only the RECEIVED timeout should be active (10s → NONE)
            advanceTimeBy(10_001)
            testScheduler.runCurrent()
            assertEquals(RequestStatus.NONE, sm.requestStatus.value)
        }

    @Test
    fun multipleDoorPositionChangesDuringSentStaysReceived() =
        runTest {
            val sm = createStateMachine()

            pushStatusFlow.value = PushStatus.SENDING
            testScheduler.runCurrent()
            pushStatusFlow.value = PushStatus.IDLE
            testScheduler.runCurrent()
            assertEquals(RequestStatus.SENT, sm.requestStatus.value)

            // First door change → RECEIVED
            doorPositionFlow.value = DoorPosition.OPENING
            testScheduler.runCurrent()
            assertEquals(RequestStatus.RECEIVED, sm.requestStatus.value)

            // Second door change → still RECEIVED (not oscillating)
            doorPositionFlow.value = DoorPosition.OPEN
            testScheduler.runCurrent()
            assertEquals(RequestStatus.RECEIVED, sm.requestStatus.value)

            // Third door change → still RECEIVED
            doorPositionFlow.value = DoorPosition.CLOSING
            testScheduler.runCurrent()
            assertEquals(RequestStatus.RECEIVED, sm.requestStatus.value)
        }
}
