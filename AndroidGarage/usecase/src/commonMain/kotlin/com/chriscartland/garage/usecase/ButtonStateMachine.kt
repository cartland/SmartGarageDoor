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
import com.chriscartland.garage.domain.model.PushStatus
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
 * Owns BOTH the tap-to-confirm interaction (Ready → Arming → Armed → confirm)
 * AND the network/door request lifecycle (Sending → Sent → Received).
 *
 * Architecture:
 * - All inputs (taps, push status changes, door movement, timer events) flow
 *   through a single [Channel] consumed by one coroutine. This serializes
 *   transitions and eliminates races by construction.
 * - Timers are scheduled via [scope.launch][CoroutineScope.launch] + [delay].
 *   With a [TestDispatcher] in tests, all timing is virtual — tests run in
 *   milliseconds, not seconds.
 * - The state machine is pure: it does NOT call use cases or repositories
 *   directly. The [onSubmit] callback is invoked when the user confirms,
 *   and the caller (ViewModel) is responsible for triggering the network
 *   request, which will eventually flip [pushButtonStatus] to SENDING.
 *
 * Happy path:
 *   Ready --tap--> Arming --(armingDelayMillis)--> Armed --tap--> Sending
 *     --PushStatus.IDLE--> Sent --doorMoves--> Received --(displayMillis)--> Ready
 *
 * Failure paths:
 *   Armed --(armedTimeoutMillis)--> NotConfirmed --(displayMillis)--> Ready
 *   Sending --(networkTimeoutMillis)--> SendingTimeout --(displayMillis)--> Ready
 *   Sent --(networkTimeoutMillis)--> SentTimeout --(displayMillis)--> Ready
 */
class ButtonStateMachine(
    pushButtonStatus: Flow<PushStatus>,
    doorPosition: Flow<DoorPosition>,
    private val onSubmit: () -> Unit,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val armingDelayMillis: Long = DEFAULT_ARMING_DELAY,
    private val armedTimeoutMillis: Long = DEFAULT_ARMED_TIMEOUT,
    private val networkTimeoutMillis: Long = DEFAULT_NETWORK_TIMEOUT,
    private val displayMillis: Long = DEFAULT_DISPLAY,
) {
    private val _state = MutableStateFlow<RemoteButtonState>(RemoteButtonState.Ready)
    val state: StateFlow<RemoteButtonState> = _state

    private val events = Channel<Event>(Channel.UNLIMITED)
    private var timerJob: Job? = null

    init {
        // Forward external flows into the channel.
        // drop(1) skips the StateFlow's initial replay so cold-start doesn't
        // emit a spurious PushStatus.IDLE that would confuse transitions.
        scope.launch(dispatcher) {
            pushButtonStatus.drop(1).collect { events.send(Event.PushStatusChanged(it)) }
        }
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

    private fun handleEvent(event: Event) {
        val current = _state.value
        when (event) {
            Event.Tap -> handleTap(current)
            Event.Reset -> transitionTo(RemoteButtonState.Ready)
            is Event.PushStatusChanged -> handlePushStatusChanged(current, event.status)
            Event.DoorMoved -> handleDoorMoved(current)
            Event.ArmingComplete -> if (current == RemoteButtonState.Arming) {
                transitionTo(RemoteButtonState.Armed)
                scheduleTimer(armedTimeoutMillis, Event.ArmedTimedOut)
            }
            Event.ArmedTimedOut -> if (current == RemoteButtonState.Armed) {
                transitionTo(RemoteButtonState.NotConfirmed)
                scheduleTimer(displayMillis, Event.DisplayTimedOut)
            }
            Event.NetworkTimedOut -> when (current) {
                RemoteButtonState.Sending -> {
                    transitionTo(RemoteButtonState.SendingTimeout)
                    scheduleTimer(displayMillis, Event.DisplayTimedOut)
                }
                RemoteButtonState.Sent -> {
                    transitionTo(RemoteButtonState.SentTimeout)
                    scheduleTimer(displayMillis, Event.DisplayTimedOut)
                }
                else -> {} // Stale timer, ignore
            }
            Event.DisplayTimedOut -> when (current) {
                RemoteButtonState.NotConfirmed,
                RemoteButtonState.Received,
                RemoteButtonState.SendingTimeout,
                RemoteButtonState.SentTimeout,
                -> transitionTo(RemoteButtonState.Ready)
                else -> {} // Stale timer, ignore
            }
        }
    }

    private fun handleTap(current: RemoteButtonState) {
        when (current) {
            RemoteButtonState.Ready -> {
                transitionTo(RemoteButtonState.Arming)
                scheduleTimer(armingDelayMillis, Event.ArmingComplete)
            }
            RemoteButtonState.Armed -> {
                // Confirm — trigger the network request and optimistically transition to Sending.
                onSubmit()
                transitionTo(RemoteButtonState.Sending)
                scheduleTimer(networkTimeoutMillis, Event.NetworkTimedOut)
            }
            // Tap ignored in all other states — Arming, NotConfirmed,
            // Sending, Sent, Received, *Timeout
            else -> {}
        }
    }

    private fun handlePushStatusChanged(
        current: RemoteButtonState,
        status: PushStatus,
    ) {
        when (status) {
            PushStatus.SENDING -> {
                // Idempotent — we already optimistically transitioned to Sending on confirm.
                if (current != RemoteButtonState.Sending) {
                    transitionTo(RemoteButtonState.Sending)
                    scheduleTimer(networkTimeoutMillis, Event.NetworkTimedOut)
                }
            }
            PushStatus.IDLE -> {
                if (current == RemoteButtonState.Sending) {
                    transitionTo(RemoteButtonState.Sent)
                    scheduleTimer(networkTimeoutMillis, Event.NetworkTimedOut)
                }
            }
        }
    }

    private fun handleDoorMoved(current: RemoteButtonState) {
        when (current) {
            RemoteButtonState.Sending,
            RemoteButtonState.Sent,
            RemoteButtonState.SendingTimeout,
            RemoteButtonState.SentTimeout,
            -> {
                transitionTo(RemoteButtonState.Received)
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

        /** External flow: push button network status changed. */
        data class PushStatusChanged(
            val status: PushStatus,
        ) : Event

        /** External flow: door position changed. */
        data object DoorMoved : Event

        /** Internal timer: arming delay complete. */
        data object ArmingComplete : Event

        /** Internal timer: armed without confirmation. */
        data object ArmedTimedOut : Event

        /** Internal timer: network call exceeded timeout. */
        data object NetworkTimedOut : Event

        /** Internal timer: terminal state display complete. */
        data object DisplayTimedOut : Event
    }

    companion object {
        const val DEFAULT_ARMING_DELAY = 500L
        const val DEFAULT_ARMED_TIMEOUT = 5_000L
        const val DEFAULT_NETWORK_TIMEOUT = 10_000L
        const val DEFAULT_DISPLAY = 10_000L
    }
}
