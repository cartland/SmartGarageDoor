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

import co.touchlab.kermit.Logger
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.RemoteButtonState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Unified state machine for the remote garage button.
 *
 * Owns BOTH the tap-to-confirm interaction
 * (Ready → Preparing → AwaitingConfirmation → confirm)
 * AND the network/door request lifecycle
 * (SendingToServer → SendingToDoor → Succeeded).
 *
 * Architecture:
 * - All inputs (taps, network signals, door movement, timer events) flow
 *   through a single [Channel] consumed by one coroutine. This serializes
 *   transitions and eliminates races by construction.
 * - Timers are scheduled via [scope.launch][CoroutineScope.launch] + [delay].
 *   With a [TestDispatcher] in tests, all timing is virtual — tests run in
 *   milliseconds, not seconds.
 * - The state machine is pure: it does NOT call use cases or repositories
 *   directly. The [onSubmit] callback is invoked when the user confirms,
 *   and the caller (ViewModel) is responsible for triggering the network
 *   request and calling [onNetworkStarted] / [onNetworkCompleted] when done.
 *
 * Happy path:
 *   Ready --tap--> Preparing --(preparingDelayMillis)--> AwaitingConfirmation
 *     --tap--> SendingToServer --onNetworkStarted()--> (starts timeout)
 *     --onNetworkCompleted()--> SendingToDoor --doorMoves--> Succeeded
 *     --(displayMillis)--> Ready
 *
 * Failure paths:
 *   AwaitingConfirmation --(confirmationTimeoutMillis)--> Cancelled
 *     --(cancelledDisplayMillis)--> Ready
 *   SendingToServer --(networkTimeoutMillis)--> ServerFailed
 *     --(displayMillis)--> Ready
 *   SendingToDoor --(networkTimeoutMillis)--> DoorFailed
 *     --(displayMillis)--> Ready
 */
class ButtonStateMachine(
    doorPosition: Flow<DoorPosition>,
    private val onSubmit: () -> Unit,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val preparingDelayMillis: Long = DEFAULT_PREPARING_DELAY,
    private val confirmationTimeoutMillis: Long = DEFAULT_CONFIRMATION_TIMEOUT,
    private val networkTimeoutMillis: Long = DEFAULT_NETWORK_TIMEOUT,
    private val displayMillis: Long = DEFAULT_DISPLAY,
    private val cancelledDisplayMillis: Long = DEFAULT_CANCELLED_DISPLAY,
) {
    private val _state = MutableStateFlow<RemoteButtonState>(RemoteButtonState.Ready)
    val state: StateFlow<RemoteButtonState> = _state

    private val events = Channel<Event>(Channel.UNLIMITED)
    private var timerJob: Job? = null

    init {
        // Forward door position changes into the channel.
        // drop(1) skips the StateFlow's initial replay so cold-start doesn't
        // emit a spurious door movement event.
        scope.launch(dispatcher) {
            doorPosition.drop(1).collect { events.send(Event.DoorMoved) }
        }
        // Single consumer — all transitions serialized through one coroutine.
        scope.launch(dispatcher) {
            for (event in events) {
                handleEvent(event)
            }
        }
    }

    /** User tapped the button. */
    fun onTap() {
        events.trySend(Event.Tap)
    }

    /** Force back to [RemoteButtonState.Ready]. Cancels any pending timers. */
    fun reset() {
        events.trySend(Event.Reset)
    }

    /**
     * The network request has started (HTTP call is in flight).
     * Called by ViewModel before the UseCase suspend call.
     * Starts the network timeout.
     */
    fun onNetworkStarted() {
        events.trySend(Event.NetworkStarted)
    }

    /**
     * The network request completed successfully (server acknowledged).
     * Called by ViewModel after the UseCase suspend call returns success.
     * Transitions to SendingToDoor (waiting for door to move).
     */
    fun onNetworkCompleted() {
        events.trySend(Event.NetworkCompleted)
    }

    /**
     * The network request failed (server error, connection failure, etc.).
     * Called by ViewModel when the UseCase returns a network-level error.
     * Transitions to ServerFailed so the user sees what went wrong.
     */
    fun onNetworkFailed() {
        events.trySend(Event.NetworkFailed)
    }

    private fun handleEvent(event: Event) {
        val current = _state.value
        when (event) {
            Event.Tap -> handleTap(current)
            Event.Reset -> transitionTo(RemoteButtonState.Ready)
            Event.NetworkStarted -> {
                if (current == RemoteButtonState.SendingToServer) {
                    scheduleTimer(networkTimeoutMillis, Event.NetworkTimedOut)
                }
            }
            Event.NetworkCompleted -> {
                if (current == RemoteButtonState.SendingToServer) {
                    transitionTo(RemoteButtonState.SendingToDoor)
                    scheduleTimer(networkTimeoutMillis, Event.NetworkTimedOut)
                }
            }
            Event.NetworkFailed -> {
                if (current == RemoteButtonState.SendingToServer) {
                    transitionTo(RemoteButtonState.ServerFailed)
                    scheduleTimer(displayMillis, Event.DisplayTimedOut)
                }
            }
            Event.DoorMoved -> handleDoorMoved(current)
            Event.PreparingComplete -> if (current == RemoteButtonState.Preparing) {
                transitionTo(RemoteButtonState.AwaitingConfirmation)
                scheduleTimer(confirmationTimeoutMillis, Event.ConfirmationTimedOut)
            }
            Event.ConfirmationTimedOut -> {
                if (current == RemoteButtonState.AwaitingConfirmation) {
                    transitionTo(RemoteButtonState.Cancelled)
                    scheduleTimer(cancelledDisplayMillis, Event.DisplayTimedOut)
                }
            }
            Event.NetworkTimedOut -> when (current) {
                RemoteButtonState.SendingToServer -> {
                    transitionTo(RemoteButtonState.ServerFailed)
                    scheduleTimer(displayMillis, Event.DisplayTimedOut)
                }
                RemoteButtonState.SendingToDoor -> {
                    transitionTo(RemoteButtonState.DoorFailed)
                    scheduleTimer(displayMillis, Event.DisplayTimedOut)
                }
                else -> {} // Stale timer, ignore
            }
            Event.DisplayTimedOut -> when (current) {
                RemoteButtonState.Cancelled,
                RemoteButtonState.Succeeded,
                RemoteButtonState.ServerFailed,
                RemoteButtonState.DoorFailed,
                -> transitionTo(RemoteButtonState.Ready)
                else -> {} // Stale timer, ignore
            }
        }
    }

    private fun handleTap(current: RemoteButtonState) {
        when (current) {
            RemoteButtonState.Ready -> {
                transitionTo(RemoteButtonState.Preparing)
                scheduleTimer(preparingDelayMillis, Event.PreparingComplete)
            }
            RemoteButtonState.AwaitingConfirmation -> {
                // Confirm — trigger the network request and optimistically
                // transition to SendingToServer. The ViewModel will call
                // onNetworkStarted() and onNetworkCompleted() based on the
                // UseCase suspend call lifecycle.
                onSubmit()
                transitionTo(RemoteButtonState.SendingToServer)
            }
            // Tap ignored in all other states
            else -> {}
        }
    }

    private fun handleDoorMoved(current: RemoteButtonState) {
        when (current) {
            RemoteButtonState.SendingToServer,
            RemoteButtonState.SendingToDoor,
            RemoteButtonState.ServerFailed,
            RemoteButtonState.DoorFailed,
            -> {
                transitionTo(RemoteButtonState.Succeeded)
                scheduleTimer(displayMillis, Event.DisplayTimedOut)
            }
            // Door movement ignored if not in a request state
            else -> {}
        }
    }

    private fun transitionTo(newState: RemoteButtonState) {
        if (_state.value != newState) {
            Logger.d { "ButtonStateMachine: ${_state.value} -> $newState" }
            _state.value = newState
        }
    }

    private fun scheduleTimer(
        delayMillis: Long,
        event: Event,
    ) {
        timerJob?.cancel()
        timerJob = scope.launch(dispatcher) {
            delay(delayMillis)
            events.send(event)
        }
    }

    private sealed interface Event {
        /** User tapped the button. */
        data object Tap : Event

        /** Caller requested reset. */
        data object Reset : Event

        /** ViewModel: network request started (HTTP in flight). */
        data object NetworkStarted : Event

        /** ViewModel: network request completed (server acknowledged). */
        data object NetworkCompleted : Event

        /** ViewModel: network request failed (server error, connection failure). */
        data object NetworkFailed : Event

        /** External flow: door position changed. */
        data object DoorMoved : Event

        /** Internal timer: preparing delay complete. */
        data object PreparingComplete : Event

        /** Internal timer: awaiting confirmation timed out. */
        data object ConfirmationTimedOut : Event

        /** Internal timer: network call exceeded timeout. */
        data object NetworkTimedOut : Event

        /** Internal timer: terminal state display complete. */
        data object DisplayTimedOut : Event
    }

    companion object {
        const val DEFAULT_PREPARING_DELAY = 500L
        const val DEFAULT_CONFIRMATION_TIMEOUT = 8_000L
        const val DEFAULT_NETWORK_TIMEOUT = 10_000L
        const val DEFAULT_DISPLAY = 10_000L
        const val DEFAULT_CANCELLED_DISPLAY = 2_000L
    }
}
