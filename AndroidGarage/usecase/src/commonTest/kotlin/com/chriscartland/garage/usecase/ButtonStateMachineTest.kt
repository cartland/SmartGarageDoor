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
import com.chriscartland.garage.domain.model.RemoteButtonState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private const val PREPARING_DELAY = 500L
private const val CONFIRMATION_TIMEOUT = 5_000L
private const val NETWORK_TIMEOUT = 10_000L
private const val DISPLAY = 10_000L

@OptIn(ExperimentalCoroutinesApi::class)
class ButtonStateMachineTest {
    private lateinit var doorPosition: MutableStateFlow<DoorPosition>
    private var submitCount = 0

    private fun TestScope.create(): ButtonStateMachine {
        doorPosition = MutableStateFlow(DoorPosition.CLOSED)
        submitCount = 0
        val dispatcher = StandardTestDispatcher(testScheduler)
        val sm = ButtonStateMachine(
            doorPosition = doorPosition,
            onSubmit = { submitCount++ },
            scope = backgroundScope,
            dispatcher = dispatcher,
            preparingDelayMillis = PREPARING_DELAY,
            confirmationTimeoutMillis = CONFIRMATION_TIMEOUT,
            networkTimeoutMillis = NETWORK_TIMEOUT,
            displayMillis = DISPLAY,
        )
        testScheduler.runCurrent()
        return sm
    }

    private fun TestScope.prepareAndConfirm(): ButtonStateMachine {
        val sm = create()
        sm.onTap()
        testScheduler.runCurrent()
        advanceTimeBy(PREPARING_DELAY + 1)
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
    fun firstTapTransitionsReadyToPreparing() =
        runTest {
            val sm = create()
            sm.onTap()
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Preparing, sm.state.value)
        }

    @Test
    fun preparingTransitionsToAwaitingConfirmationAfterDelay() =
        runTest {
            val sm = create()
            sm.onTap()
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Preparing, sm.state.value)

            advanceTimeBy(PREPARING_DELAY + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.AwaitingConfirmation, sm.state.value)
        }

    @Test
    fun secondTapInAwaitingConfirmationConfirmsAndCallsSubmit() =
        runTest {
            val sm = create()
            sm.onTap()
            testScheduler.runCurrent()
            advanceTimeBy(PREPARING_DELAY + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.AwaitingConfirmation, sm.state.value)

            sm.onTap()
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.SendingToServer, sm.state.value)
            assertEquals(1, submitCount)
        }

    @Test
    fun tapDuringPreparingIsIgnored() =
        runTest {
            val sm = create()
            sm.onTap()
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Preparing, sm.state.value)

            sm.onTap()
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Preparing, sm.state.value)
            assertEquals(0, submitCount)
        }

    @Test
    fun awaitingConfirmationTimesOutToCancelledThenReady() =
        runTest {
            val sm = create()
            sm.onTap()
            testScheduler.runCurrent()
            advanceTimeBy(PREPARING_DELAY + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.AwaitingConfirmation, sm.state.value)

            advanceTimeBy(CONFIRMATION_TIMEOUT + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Cancelled, sm.state.value)

            advanceTimeBy(DISPLAY + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Ready, sm.state.value)
        }

    // --- Network/door request flow ---

    @Test
    fun onNetworkCompletedTransitionsToSendingToDoor() =
        runTest {
            val sm = prepareAndConfirm()
            assertEquals(RemoteButtonState.SendingToServer, sm.state.value)

            sm.onNetworkStarted()
            testScheduler.runCurrent()
            sm.onNetworkCompleted()
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.SendingToDoor, sm.state.value)
        }

    @Test
    fun doorMovementDuringSendingToServerTransitionsToSucceeded() =
        runTest {
            val sm = prepareAndConfirm()

            doorPosition.value = DoorPosition.OPENING
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Succeeded, sm.state.value)
        }

    @Test
    fun doorMovementDuringSendingToDoorTransitionsToSucceeded() =
        runTest {
            val sm = prepareAndConfirm()
            sm.onNetworkStarted()
            testScheduler.runCurrent()
            sm.onNetworkCompleted()
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.SendingToDoor, sm.state.value)

            doorPosition.value = DoorPosition.OPENING
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Succeeded, sm.state.value)
        }

    @Test
    fun succeededAutoResetsToReadyAfterDisplayTimeout() =
        runTest {
            val sm = prepareAndConfirm()
            doorPosition.value = DoorPosition.OPENING
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Succeeded, sm.state.value)

            advanceTimeBy(DISPLAY + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Ready, sm.state.value)
        }

    @Test
    fun sendingToServerTimesOutToServerFailed() =
        runTest {
            val sm = prepareAndConfirm()
            sm.onNetworkStarted()
            testScheduler.runCurrent()

            advanceTimeBy(NETWORK_TIMEOUT + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.ServerFailed, sm.state.value)

            advanceTimeBy(DISPLAY + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Ready, sm.state.value)
        }

    @Test
    fun onNetworkFailedTransitionsToServerFailed() =
        runTest {
            val sm = prepareAndConfirm()
            sm.onNetworkStarted()
            testScheduler.runCurrent()

            sm.onNetworkFailed()
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.ServerFailed, sm.state.value)

            advanceTimeBy(DISPLAY + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Ready, sm.state.value)
        }

    @Test
    fun sendingToServerDoesNotTimeOutBeforeNetworkStarted() =
        runTest {
            val sm = prepareAndConfirm()
            assertEquals(RemoteButtonState.SendingToServer, sm.state.value)

            // No onNetworkStarted() — timeout timer hasn't started.
            advanceTimeBy(NETWORK_TIMEOUT * 3)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.SendingToServer, sm.state.value)
        }

    @Test
    fun sendingToDoorTimesOutToDoorFailed() =
        runTest {
            val sm = prepareAndConfirm()
            sm.onNetworkStarted()
            testScheduler.runCurrent()
            sm.onNetworkCompleted()
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.SendingToDoor, sm.state.value)

            advanceTimeBy(NETWORK_TIMEOUT + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.DoorFailed, sm.state.value)

            advanceTimeBy(DISPLAY + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Ready, sm.state.value)
        }

    // --- Full happy path ---

    @Test
    fun fullHappyPathReadyToSucceededToReady() =
        runTest {
            val sm = create()

            sm.onTap()
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Preparing, sm.state.value)

            advanceTimeBy(PREPARING_DELAY + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.AwaitingConfirmation, sm.state.value)

            sm.onTap()
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.SendingToServer, sm.state.value)
            assertEquals(1, submitCount)

            sm.onNetworkStarted()
            testScheduler.runCurrent()
            sm.onNetworkCompleted()
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.SendingToDoor, sm.state.value)

            doorPosition.value = DoorPosition.OPENING
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Succeeded, sm.state.value)

            advanceTimeBy(DISPLAY + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Ready, sm.state.value)
        }

    // --- Edge cases ---

    @Test
    fun tapDuringSendingToServerIsIgnored() =
        runTest {
            val sm = prepareAndConfirm()
            val before = submitCount

            sm.onTap()
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.SendingToServer, sm.state.value)
            assertEquals(before, submitCount)
        }

    @Test
    fun resetFromAnyStateGoesToReady() =
        runTest {
            val sm = prepareAndConfirm()
            assertEquals(RemoteButtonState.SendingToServer, sm.state.value)

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
            advanceTimeBy(PREPARING_DELAY + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.AwaitingConfirmation, sm.state.value)

            sm.reset()
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Ready, sm.state.value)

            advanceTimeBy(CONFIRMATION_TIMEOUT + DISPLAY)
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
    fun onNetworkStartedStaysInSendingToServerAndStartsTimeout() =
        runTest {
            val sm = prepareAndConfirm()
            sm.onNetworkStarted()
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.SendingToServer, sm.state.value)
        }

    @Test
    fun doorMovementInServerFailedTransitionsToSucceeded() =
        runTest {
            val sm = prepareAndConfirm()
            sm.onNetworkStarted()
            testScheduler.runCurrent()
            advanceTimeBy(NETWORK_TIMEOUT + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.ServerFailed, sm.state.value)

            doorPosition.value = DoorPosition.OPENING
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Succeeded, sm.state.value)
        }

    @Test
    fun secondTapAfterCompleteFlowStartsPreparing() =
        runTest {
            val sm = create()
            sm.onTap()
            testScheduler.runCurrent()
            advanceTimeBy(PREPARING_DELAY + 1)
            testScheduler.runCurrent()
            sm.onTap()
            testScheduler.runCurrent()
            sm.onNetworkStarted()
            testScheduler.runCurrent()
            sm.onNetworkCompleted()
            testScheduler.runCurrent()
            doorPosition.value = DoorPosition.OPENING
            testScheduler.runCurrent()
            advanceTimeBy(DISPLAY + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Ready, sm.state.value)

            sm.onTap()
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Preparing, sm.state.value)
        }

    @Test
    fun confirmCallsOnSubmitExactlyOnce() =
        runTest {
            val sm = prepareAndConfirm()
            sm.onNetworkStarted()
            sm.onNetworkCompleted()
            testScheduler.runCurrent()
            assertEquals(1, submitCount)
        }

    @Test
    fun rapidDoubleTapInAwaitingConfirmationDoesNotSubmitTwice() =
        runTest {
            val sm = create()
            sm.onTap()
            testScheduler.runCurrent()
            advanceTimeBy(PREPARING_DELAY + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.AwaitingConfirmation, sm.state.value)

            sm.onTap()
            sm.onTap()
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.SendingToServer, sm.state.value)
            assertEquals(1, submitCount)
        }

    @Test
    fun resetDuringPreparingThenTapStartsPreparingAgain() =
        runTest {
            val sm = create()
            sm.onTap()
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Preparing, sm.state.value)

            sm.reset()
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Ready, sm.state.value)

            sm.onTap()
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Preparing, sm.state.value)
        }

    @Test
    fun displayTimeoutFromServerFailedReturnsToReady() =
        runTest {
            val sm = prepareAndConfirm()
            sm.onNetworkStarted()
            testScheduler.runCurrent()

            advanceTimeBy(NETWORK_TIMEOUT + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.ServerFailed, sm.state.value)

            advanceTimeBy(DISPLAY + 1)
            testScheduler.runCurrent()
            assertEquals(RemoteButtonState.Ready, sm.state.value)
        }
}
