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
import com.chriscartland.garage.domain.model.RemoteButtonState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private const val ARMING_DELAY = 500L
private const val ARMED_TIMEOUT = 5_000L
private const val NETWORK_TIMEOUT = 10_000L
private const val DISPLAY = 10_000L

@OptIn(ExperimentalCoroutinesApi::class)
class ButtonStateMachineTest {
    private lateinit var pushStatus: MutableStateFlow<PushStatus>
    private lateinit var doorPosition: MutableStateFlow<DoorPosition>
    private var submitCount = 0

    private fun TestScope.create(): ButtonStateMachine {
        pushStatus = MutableStateFlow(PushStatus.IDLE)
        doorPosition = MutableStateFlow(DoorPosition.CLOSED)
        submitCount = 0
        val dispatcher = StandardTestDispatcher(testScheduler)
        val sm = ButtonStateMachine(
            pushButtonStatus = pushStatus,
            doorPosition = doorPosition,
            onSubmit = { submitCount++ },
            scope = backgroundScope,
            dispatcher = dispatcher,
            armingDelayMillis = ARMING_DELAY,
            armedTimeoutMillis = ARMED_TIMEOUT,
            networkTimeoutMillis = NETWORK_TIMEOUT,
            displayMillis = DISPLAY,
        )
        testScheduler.runCurrent()
        return sm
    }

    private fun TestScope.armAndConfirm(): ButtonStateMachine {
        val sm = create()
        sm.onTap()
        testScheduler.runCurrent()
        advanceTimeBy(ARMING_DELAY + 1)
        testScheduler.runCurrent()
        sm.onTap()
        testScheduler.runCurrent()
        return sm
    }

    // --- Initial state ---

    @Test
    fun initialStateIsReady() =
        runTest {
            val sm = create()
            assertEquals(RemoteButtonState.Ready, sm.state.value)
        }

    // --- Tap-to-confirm flow ---

    @Test
    fun firstTapTransitionsReadyToArming() =
        runTest {
            val sm = create()
            sm.onTap()
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Arming, sm.state.value)
        }

    @Test
    fun armingTransitionsToArmedAfterDelay() =
        runTest {
            val sm = create()
            sm.onTap()
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Arming, sm.state.value)

            advanceTimeBy(ARMING_DELAY + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Armed, sm.state.value)
        }

    @Test
    fun secondTapInArmedConfirmsAndCallsSubmit() =
        runTest {
            val sm = create()
            sm.onTap()
            testScheduler.runCurrent()
            advanceTimeBy(ARMING_DELAY + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Armed, sm.state.value)

            sm.onTap()
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Sending, sm.state.value)
            assertEquals(1, submitCount)
        }

    @Test
    fun tapDuringArmingIsIgnored() =
        runTest {
            val sm = create()
            sm.onTap()
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Arming, sm.state.value)

            sm.onTap()
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Arming, sm.state.value)
            assertEquals(0, submitCount)
        }

    @Test
    fun armedTimesOutToNotConfirmedThenReady() =
        runTest {
            val sm = create()
            sm.onTap()
            testScheduler.runCurrent()
            advanceTimeBy(ARMING_DELAY + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Armed, sm.state.value)

            advanceTimeBy(ARMED_TIMEOUT + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.NotConfirmed, sm.state.value)

            advanceTimeBy(DISPLAY + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Ready, sm.state.value)
        }

    // --- Network/door request flow ---

    @Test
    fun pushStatusIdleAfterSendingTransitionsToSent() =
        runTest {
            val sm = armAndConfirm()
            assertEquals(RemoteButtonState.Sending, sm.state.value)

            // Realistic flow: repo sets SENDING then IDLE
            pushStatus.value = PushStatus.SENDING
            testScheduler.runCurrent()
            pushStatus.value = PushStatus.IDLE
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Sent, sm.state.value)
        }

    @Test
    fun doorMovementDuringSendingTransitionsToReceived() =
        runTest {
            val sm = armAndConfirm()

            doorPosition.value = DoorPosition.OPENING
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Received, sm.state.value)
        }

    @Test
    fun doorMovementDuringSentTransitionsToReceived() =
        runTest {
            val sm = armAndConfirm()
            // Realistic flow: repo sets SENDING then IDLE
            pushStatus.value = PushStatus.SENDING
            testScheduler.runCurrent()
            pushStatus.value = PushStatus.IDLE
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Sent, sm.state.value)

            doorPosition.value = DoorPosition.OPENING
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Received, sm.state.value)
        }

    @Test
    fun receivedAutoResetsToReadyAfterDisplayTimeout() =
        runTest {
            val sm = armAndConfirm()
            doorPosition.value = DoorPosition.OPENING
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Received, sm.state.value)

            advanceTimeBy(DISPLAY + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Ready, sm.state.value)
        }

    @Test
    fun sendingTimesOutToSendingTimeoutThenReady() =
        runTest {
            val sm = armAndConfirm()
            advanceTimeBy(NETWORK_TIMEOUT + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.SendingTimeout, sm.state.value)

            advanceTimeBy(DISPLAY + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Ready, sm.state.value)
        }

    @Test
    fun sentTimesOutToSentTimeoutThenReady() =
        runTest {
            val sm = armAndConfirm()
            // Realistic flow: repo sets SENDING then IDLE
            pushStatus.value = PushStatus.SENDING
            testScheduler.runCurrent()
            pushStatus.value = PushStatus.IDLE
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Sent, sm.state.value)

            advanceTimeBy(NETWORK_TIMEOUT + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.SentTimeout, sm.state.value)

            advanceTimeBy(DISPLAY + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Ready, sm.state.value)
        }

    // --- Full happy path ---

    @Test
    fun fullHappyPathReadyToReceivedToReady() =
        runTest {
            val sm = create()

            sm.onTap()
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Arming, sm.state.value)

            advanceTimeBy(ARMING_DELAY + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Armed, sm.state.value)

            sm.onTap()
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Sending, sm.state.value)
            assertEquals(1, submitCount)

            // Realistic flow: repo sets SENDING then IDLE
            pushStatus.value = PushStatus.SENDING
            testScheduler.runCurrent()
            pushStatus.value = PushStatus.IDLE
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Sent, sm.state.value)

            doorPosition.value = DoorPosition.OPENING
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Received, sm.state.value)

            advanceTimeBy(DISPLAY + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Ready, sm.state.value)
        }

    // --- Edge cases ---

    @Test
    fun tapDuringSendingIsIgnored() =
        runTest {
            val sm = armAndConfirm()
            val before = submitCount

            sm.onTap()
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Sending, sm.state.value)
            assertEquals(before, submitCount)
        }

    @Test
    fun resetFromAnyStateGoesToReady() =
        runTest {
            val sm = armAndConfirm()
            assertEquals(RemoteButtonState.Sending, sm.state.value)

            sm.reset()
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Ready, sm.state.value)
        }

    @Test
    fun resetCancelsPendingTimer() =
        runTest {
            val sm = create()
            sm.onTap()
            testScheduler.runCurrent()
            advanceTimeBy(ARMING_DELAY + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Armed, sm.state.value)

            sm.reset()
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Ready, sm.state.value)

            // Advance past where the armed timeout would have fired
            advanceTimeBy(ARMED_TIMEOUT + DISPLAY)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Ready, sm.state.value)
        }

    @Test
    fun doorMovementInReadyIsIgnored() =
        runTest {
            val sm = create()
            doorPosition.value = DoorPosition.OPENING
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Ready, sm.state.value)
        }

    @Test
    fun pushStatusSendingArrivingAfterOptimisticIsIdempotent() =
        runTest {
            val sm = armAndConfirm()
            pushStatus.value = PushStatus.SENDING
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Sending, sm.state.value)
        }

    @Test
    fun doorMovementInSendingTimeoutTransitionsToReceived() =
        runTest {
            val sm = armAndConfirm()
            advanceTimeBy(NETWORK_TIMEOUT + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.SendingTimeout, sm.state.value)

            doorPosition.value = DoorPosition.OPENING
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Received, sm.state.value)
        }

    @Test
    fun secondTapAfterCompleteFlowStartsNewArming() =
        runTest {
            val sm = create()
            sm.onTap()
            testScheduler.runCurrent()
            advanceTimeBy(ARMING_DELAY + 1)
            testScheduler.runCurrent()
            sm.onTap()
            testScheduler.runCurrent()
            // Realistic flow: SENDING then IDLE
            pushStatus.value = PushStatus.SENDING
            testScheduler.runCurrent()
            pushStatus.value = PushStatus.IDLE
            testScheduler.runCurrent()
            doorPosition.value = DoorPosition.OPENING
            testScheduler.runCurrent()
            advanceTimeBy(DISPLAY + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Ready, sm.state.value)

            sm.onTap()
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Arming, sm.state.value)
        }

    @Test
    fun confirmCallsOnSubmitExactlyOnce() =
        runTest {
            val sm = armAndConfirm()
            pushStatus.value = PushStatus.SENDING
            pushStatus.value = PushStatus.IDLE
            testScheduler.runCurrent()
            assertEquals(1, submitCount)
        }

    @Test
    fun rapidDoubleTapInArmedDoesNotSubmitTwice() =
        runTest {
            val sm = create()
            sm.onTap()
            testScheduler.runCurrent()
            advanceTimeBy(ARMING_DELAY + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Armed, sm.state.value)

            // Rapid double tap — second tap arrives while we're already in Sending
            sm.onTap()
            sm.onTap()
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Sending, sm.state.value)
            assertEquals(1, submitCount)
        }

    @Test
    fun resetDuringArmingThenTapStartsArmingAgain() =
        runTest {
            val sm = create()
            sm.onTap()
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Arming, sm.state.value)

            sm.reset()
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Ready, sm.state.value)

            sm.onTap()
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Arming, sm.state.value)
        }

    @Test
    fun displayTimeoutFromSendingTimeoutReturnsToReady() =
        runTest {
            val sm = armAndConfirm()
            advanceTimeBy(NETWORK_TIMEOUT + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.SendingTimeout, sm.state.value)

            advanceTimeBy(DISPLAY + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Ready, sm.state.value)
        }
}
