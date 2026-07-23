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
import com.chriscartland.garage.domain.model.DoorPosition
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
 *     stays armed. Every touch on the screen — [onScreenTouch] from the
 *     UI's screen-wide observer, plus the hold callbacks themselves —
 *     restarts the machine's confirmation timeout, so the armed window
 *     counts from the LAST touch and expires only after a quiet period
 *     with no touches. Because finger-down restarts the window, a hold
 *     started at the last instant can always run its full 2 seconds; the
 *     machine can never disarm mid-hold.
 *
 * A quick tap while armed never submits — it only keeps the button armed.
 * Only a completed continuous hold confirms. This is deliberately stricter
 * than the phone's second-tap confirm; a watch face is far easier to touch
 * accidentally. There is deliberately no hard cap on how long touches can
 * keep the button armed: the execute gate is the continuous hold, not the
 * window length, and the quiet-period timeout handles abandonment.
 *
 * Freshness: the watch has no FCM registration, so [onVisible] starts a
 * foreground-only refresh loop (stopped by [onHidden]). While a press is
 * waiting on the door, polling tightens to [ACTIVE_POLL_MILLIS] so the
 * machine's door-moved success detection fires promptly.
 *
 * Screen wake: [keepScreenOn] is true (for at most [KEEP_SCREEN_ON_MILLIS]
 * per trigger) only while a submitted press is in flight or the door is
 * physically moving — the moments the user needs to watch the screen.
 * Normal viewing, including the armed state, never holds the screen awake;
 * the battery cost is only paid while something is actually happening.
 *
 * Failure grace: the watch's network path (BT relay / Wi-Fi at the garage)
 * is less reliable than the phone's, and door-moved detection additionally
 * waits on the foreground poll — so the machine gets a wear-specific
 * [DOOR_RESPONSE_TIMEOUT_MILLIS] (longer than the shared default) before
 * declaring a press failed.
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
        networkTimeoutMillis = DOOR_RESPONSE_TIMEOUT_MILLIS,
    )
    val buttonState: StateFlow<RemoteButtonState> = stateMachine.state

    private val _isHolding = MutableStateFlow(false)

    /** True while a hold-to-confirm countdown is running (drives the radial indicator). */
    val isHolding: StateFlow<Boolean> = _isHolding

    private val _signInError = MutableStateFlow(false)

    /** True after a failed sign-in attempt; cleared by [onSignInStarted]. */
    val signInError: StateFlow<Boolean> = _signInError

    private val _keepScreenOn = MutableStateFlow(false)

    /**
     * True while the UI should hold the screen awake: a submitted press in
     * flight or a door in motion, capped at [KEEP_SCREEN_ON_MILLIS] per
     * trigger. The window restarts on each new trigger (server ack, door
     * starts moving) and clears immediately when nothing is happening.
     */
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn

    private var holdJob: Job? = null
    private var refreshJob: Job? = null
    private var signInErrorJob: Job? = null
    private var keepScreenOnJob: Job? = null

    init {
        viewModelScope.launch(dispatchers.default) {
            combine(stateMachine.state, currentDoorEvent) { button, doorEvent ->
                keepScreenOnTrigger(button, doorEvent?.doorPosition)
            }.distinctUntilChanged().collect { trigger ->
                if (trigger != null) restartKeepScreenOnWindow() else clearKeepScreenOnWindow()
            }
        }
    }

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
        // Any touch keeps the armed window alive (machine no-ops unless armed).
        stateMachine.onUserInteraction()
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
        // The quiet period runs from the LAST touch, so release also resets it.
        stateMachine.onUserInteraction()
        holdJob?.cancel()
        holdJob = null
        _isHolding.value = false
    }

    /**
     * Any touch observed anywhere on the screen (the UI's non-consuming
     * screen-wide observer). While armed this restarts the machine's
     * confirmation timeout so the button stays armed while the user keeps
     * interacting; no-op in every other state.
     */
    fun onScreenTouch() {
        stateMachine.onUserInteraction()
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

    /** A sign-in attempt is starting: clear any stale failure message. */
    fun onSignInStarted() {
        _signInError.value = false
    }

    /**
     * Result of the platform sign-in flow. `null` means Credential Manager
     * failed or was dismissed (e.g. `NoCredentialException:
     * Auth.Api.Identity.SignIn.API is not available` on watches whose Play
     * services lack the Identity module) — surface it instead of silently
     * doing nothing, which reads as an unresponsive button (0.1.1 bug).
     * The error is transient ([SIGN_IN_ERROR_DISPLAY_MILLIS]) because the
     * UI renders it as a bottom-edge overlay on the round screen.
     */
    fun onSignInResult(googleIdToken: GoogleIdToken?) {
        if (googleIdToken == null) {
            showSignInError()
            return
        }
        viewModelScope.launch(dispatchers.io) {
            val result = signInWithGoogleUseCase(googleIdToken)
            if (result !is AuthState.Authenticated) {
                showSignInError()
            }
        }
    }

    private fun showSignInError() {
        _signInError.value = true
        signInErrorJob?.cancel()
        signInErrorJob = viewModelScope.launch(dispatchers.default) {
            delay(SIGN_IN_ERROR_DISPLAY_MILLIS)
            _signInError.value = false
        }
    }

    /**
     * The moments worth holding the screen awake for, as a distinct value per
     * trigger so the window restarts on each new phase (server ack, door
     * starts moving) but not on repeated identical emissions. Null = nothing
     * happening; the armed state is deliberately NOT a trigger.
     */
    private fun keepScreenOnTrigger(
        buttonState: RemoteButtonState,
        doorPosition: DoorPosition?,
    ): String? =
        when {
            buttonState is RemoteButtonState.SendingToServer -> "sending-to-server"
            buttonState is RemoteButtonState.SendingToDoor -> "sending-to-door"
            doorPosition == DoorPosition.OPENING || doorPosition == DoorPosition.CLOSING ->
                "door-moving-$doorPosition"
            else -> null
        }

    private fun restartKeepScreenOnWindow() {
        _keepScreenOn.value = true
        keepScreenOnJob?.cancel()
        keepScreenOnJob = viewModelScope.launch(dispatchers.default) {
            delay(KEEP_SCREEN_ON_MILLIS)
            _keepScreenOn.value = false
        }
    }

    private fun clearKeepScreenOnWindow() {
        keepScreenOnJob?.cancel()
        keepScreenOnJob = null
        _keepScreenOn.value = false
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

        /** How long the transient sign-in failure message stays visible. */
        const val SIGN_IN_ERROR_DISPLAY_MILLIS: Long = 5_000L

        /**
         * Cap on how long one trigger holds the screen awake. Real presses
         * take 10+ seconds end to end (server ack + door start + travel);
         * 15 seconds covers watching that without an unbounded wake.
         */
        const val KEEP_SCREEN_ON_MILLIS: Long = 15_000L

        /**
         * Wear-specific press timeout (the machine's `networkTimeoutMillis`,
         * covering both the server call and the wait for the door to move).
         * Longer than [ButtonStateMachine.DEFAULT_NETWORK_TIMEOUT] because
         * the watch's network path is less reliable and door-moved detection
         * additionally waits on the foreground poll cadence — declaring
         * DoorFailed while the door is actually about to move reads as a
         * false alarm.
         */
        const val DOOR_RESPONSE_TIMEOUT_MILLIS: Long = 15_000L
    }
}
