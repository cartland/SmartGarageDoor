/*
 * Copyright 2026 Chris Cartland. All rights reserved.
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

package com.chriscartland.garage.wear.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chriscartland.garage.domain.coroutines.DispatcherProvider
import com.chriscartland.garage.domain.model.ActionError
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.GoogleIdToken
import com.chriscartland.garage.domain.model.RemoteButtonState
import com.chriscartland.garage.usecase.ButtonAckToken
import com.chriscartland.garage.usecase.ButtonStateMachine
import com.chriscartland.garage.usecase.FetchCurrentDoorEventUseCase
import com.chriscartland.garage.usecase.ObserveAuthStateUseCase
import com.chriscartland.garage.usecase.ObserveDoorEventsUseCase
import com.chriscartland.garage.usecase.PushRemoteButtonUseCase
import com.chriscartland.garage.usecase.SignInWithGoogleUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the single Wear hero screen: animated door status plus the
 * tap-to-arm / hold-to-confirm remote button.
 *
 * Button flow (drives the shared [ButtonStateMachine], never bypasses it):
 *  1. [onDoorTap] while `Ready` (and signed in) arms the button —
 *     `Ready -> Preparing -> AwaitingConfirmation`.
 *  2. [onHoldStart] while armed starts the hold-to-confirm countdown
 *     ([HOLD_TO_CONFIRM_MILLIS]). If the finger stays down the whole time,
 *     the machine's second tap fires and the press is submitted.
 *  3. [onHoldEnd] (finger lifted early) cancels the countdown; the machine
 *     stays in `AwaitingConfirmation` until its own timeout cancels it.
 *
 * A quick tap while armed does nothing — only a completed hold confirms.
 * This is deliberately stricter than the phone's second-tap confirm; a
 * watch face is far easier to touch accidentally.
 *
 * Freshness: the watch has no FCM registration, so [onVisible] starts a
 * foreground-only refresh loop (stopped by [onHidden]). While a press is
 * waiting on the door, polling tightens to [ACTIVE_POLL_MILLIS] so the
 * machine's door-moved success detection fires promptly.
 */
class WearHomeViewModel(
    observeDoorEvents: ObserveDoorEventsUseCase,
    observeAuthState: ObserveAuthStateUseCase,
    private val pushRemoteButtonUseCase: PushRemoteButtonUseCase,
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    private val fetchCurrentDoorEventUseCase: FetchCurrentDoorEventUseCase,
    private val dispatchers: DispatcherProvider,
    private val appVersion: String,
) : ViewModel() {
    /** Pass-through of the repository StateFlows (ADR-022 — no re-wrapping). */
    val authState: StateFlow<AuthState> = observeAuthState()
    val currentDoorEvent: StateFlow<DoorEvent?> = observeDoorEvents.current()

    private val stateMachine = ButtonStateMachine(
        doorPosition = observeDoorEvents.position(),
        onSubmit = ::submitButtonPress,
        scope = viewModelScope,
        dispatcher = dispatchers.io,
    )
    val buttonState: StateFlow<RemoteButtonState> = stateMachine.state

    private val _isHolding = MutableStateFlow(false)

    /** True while a hold-to-confirm countdown is running (drives the radial indicator). */
    val isHolding: StateFlow<Boolean> = _isHolding

    private var holdJob: Job? = null
    private var refreshJob: Job? = null

    /** First tap on the door: arm the button. Gated on auth and `Ready`. */
    fun onDoorTap() {
        if (authState.value !is AuthState.Authenticated) return
        if (buttonState.value is RemoteButtonState.Ready) {
            stateMachine.onTap()
        }
    }

    /**
     * Finger down on the door while armed: start the hold-to-confirm countdown.
     * `Preparing` is accepted too so a press that starts during the short
     * arming delay still counts from finger-down; the confirm itself is
     * re-checked against `AwaitingConfirmation` when the countdown completes.
     */
    fun onHoldStart() {
        val state = buttonState.value
        val armedOrArming = state is RemoteButtonState.AwaitingConfirmation || state is RemoteButtonState.Preparing
        if (!armedOrArming) return
        if (holdJob?.isActive == true) return
        _isHolding.value = true
        holdJob = viewModelScope.launch(dispatchers.default) {
            delay(HOLD_TO_CONFIRM_MILLIS)
            _isHolding.value = false
            if (buttonState.value is RemoteButtonState.AwaitingConfirmation) {
                // The machine's second tap — submits the press via onSubmit.
                stateMachine.onTap()
            }
        }
    }

    /** Finger lifted: cancel an incomplete hold (no-op after a completed one). */
    fun onHoldEnd() {
        holdJob?.cancel()
        holdJob = null
        _isHolding.value = false
    }

    /** Screen became visible: start the foreground refresh loop. */
    fun onVisible() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch(dispatchers.io) {
            while (true) {
                fetchCurrentDoorEventUseCase()
                val waitingOnDoor = buttonState.value is RemoteButtonState.SendingToServer ||
                    buttonState.value is RemoteButtonState.SendingToDoor
                delay(if (waitingOnDoor) ACTIVE_POLL_MILLIS else IDLE_POLL_MILLIS)
            }
        }
    }

    /** Screen hidden: stop polling (the watch app does no background work). */
    fun onHidden() {
        refreshJob?.cancel()
        refreshJob = null
    }

    fun signIn(googleIdToken: GoogleIdToken) {
        viewModelScope.launch(dispatchers.io) {
            signInWithGoogleUseCase(googleIdToken)
        }
    }

    private fun submitButtonPress() {
        stateMachine.onNetworkStarted()
        viewModelScope.launch(dispatchers.io) {
            val buttonAckToken = ButtonAckToken.create(
                currentTimeMillis = System.currentTimeMillis(),
                appVersion = appVersion,
            )
            when (val result = pushRemoteButtonUseCase(buttonAckToken)) {
                is AppResult.Success -> stateMachine.onNetworkCompleted()
                is AppResult.Error -> when (result.error) {
                    ActionError.NetworkFailed -> stateMachine.onNetworkFailed()
                    else -> stateMachine.reset()
                }
            }
        }
    }

    companion object {
        /** Hold duration required to confirm a press (the radial indicator sweep time). */
        const val HOLD_TO_CONFIRM_MILLIS: Long = 2_000L

        /** Foreground poll cadence while idle. */
        const val IDLE_POLL_MILLIS: Long = 10_000L

        /** Foreground poll cadence while a press is waiting on the door. */
        const val ACTIVE_POLL_MILLIS: Long = 2_000L
    }
}
