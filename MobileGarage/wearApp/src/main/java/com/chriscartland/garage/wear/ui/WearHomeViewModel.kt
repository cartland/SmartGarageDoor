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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * ViewModel for the single Wear hero screen: animated door status plus the
 * hold-to-press remote button.
 *
 * Button flow — ONE gesture, and it still drives the shared
 * [ButtonStateMachine] rather than bypassing it:
 *  1. [onHoldStart] (finger down on the door, signed in, button at rest)
 *     sends the machine's first tap AND starts the [HOLD_TO_CONFIRM_MILLIS]
 *     countdown. The machine's 500ms `Preparing` delay therefore elapses
 *     *under the user's finger*, so `Ready -> Preparing ->
 *     AwaitingConfirmation` is never something the user waits on or reads
 *     about; it is an implementation detail of the same single hold.
 *  2. When the countdown completes the machine's second tap fires and the
 *     press is submitted.
 *  3. [onHoldEnd] (finger lifted or drifted early) cancels the countdown
 *     and resets the machine straight back to `Ready`.
 *
 * A tap does nothing whatsoever — the UI has no tap handler at all, and only
 * a completed continuous hold can reach the real garage button. This is
 * deliberately stricter than the phone's two-tap confirm; a watch face is
 * far easier to touch accidentally. Protection against a *sustained*
 * accidental contact (a sleeve, a wrist against a surface) comes from two
 * places: the UI abandons the hold if the finger drifts, and the hold is
 * long enough that the accompanying haptics announce it well before it
 * fires.
 *
 * Because an incomplete hold resets the machine, `AwaitingConfirmation`
 * exists only while a finger is down, and `Cancelled` — which the machine
 * reaches via its confirmation timeout — is unreachable here.
 *
 * Freshness: the watch has no FCM registration, so [onVisible] starts a
 * foreground-only refresh loop (stopped by [onHidden]). While a press is
 * waiting on the door, polling tightens to [ACTIVE_POLL_MILLIS] so the
 * machine's door-moved success detection fires promptly.
 *
 * Screen wake: [keepScreenOn] is true (for at most [KEEP_SCREEN_ON_MILLIS]
 * per trigger) only while a submitted press is in flight or the door is
 * physically moving — the moments the user needs to watch the screen.
 * Normal viewing, and the hold itself, never hold the screen awake; the
 * battery cost is only paid while something is actually happening.
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

    private val _hapticCues = MutableSharedFlow<HapticCue>(
        replay = 0,
        extraBufferCapacity = HAPTIC_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * One-shot haptic moments for the UI to perform. Not a StateFlow: these are
     * events, and conflation would drop a cue whose neighbour repeated.
     *
     * Not a Channel either, which is the subtler half. A Channel *queues* for
     * an absent collector, so a cue emitted while no UI is subscribed is
     * replayed later — a buzz arriving seconds after the thing it describes,
     * which is worse than no buzz at all. `replay = 0` drops instead.
     */
    val hapticCues: Flow<HapticCue> = _hapticCues

    private val _keepScreenOn = MutableStateFlow(false)

    /**
     * True while the UI should hold the screen awake: a submitted press in
     * flight or a door in motion, capped at [KEEP_SCREEN_ON_MILLIS] per
     * trigger. The window restarts on each new trigger (server ack, door
     * starts moving) and clears immediately when nothing is happening.
     */
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn

    private var holdJob: Job? = null
    private var commitBeatJob: Job? = null
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
        // Outcome haptics. drop(1) skips the StateFlow's initial Ready replay,
        // and the machine only publishes on real transitions, so each terminal
        // state buzzes exactly once.
        viewModelScope.launch(dispatchers.default) {
            stateMachine.state.drop(1).collect { state ->
                when (state) {
                    RemoteButtonState.Succeeded -> _hapticCues.tryEmit(HapticCue.PressSucceeded)
                    RemoteButtonState.ServerFailed,
                    RemoteButtonState.DoorFailed,
                    -> _hapticCues.tryEmit(HapticCue.PressFailed)
                    else -> Unit
                }
            }
        }
    }

    /**
     * Finger down on the door: begin the single hold-to-press gesture.
     *
     * Gated on being signed in and on the button being at rest, so touches
     * landing while a press is already in flight (or while a terminal result
     * is on screen) do nothing at all. The machine's first tap is sent here,
     * which means its `Preparing` delay runs inside the hold and is invisible.
     */
    fun onHoldStart() {
        if (authState.value !is AuthState.Authenticated) return
        if (holdJob?.isActive == true) return
        if (buttonState.value !is RemoteButtonState.Ready) return
        stateMachine.onTap()
        _isHolding.value = true
        _hapticCues.tryEmit(HapticCue.HoldEngaged)
        holdJob = viewModelScope.launch(dispatchers.default) {
            delay(HOLD_HALFWAY_MILLIS)
            _hapticCues.tryEmit(HapticCue.HoldHalfway)
            delay(HOLD_TO_CONFIRM_MILLIS - HOLD_HALFWAY_MILLIS)
            _isHolding.value = false
            if (buttonState.value is RemoteButtonState.AwaitingConfirmation) {
                _hapticCues.tryEmit(HapticCue.PressCommitted)
                // The machine's second tap — submits the press via onSubmit.
                stateMachine.onTap()
                emitSecondCommitBeat()
            }
        }
    }

    /**
     * The second half of the commit buzz, on its own job.
     *
     * The commit is the one irreversible moment in the gesture and the screen
     * now marks it with a full-screen bloom; a single tick under that reads as
     * an understatement. Two beats is the doubling — wrist actuators are too
     * coarse to convey "harder", so more is expressed as again.
     *
     * Deliberately NOT inside [holdJob]: that job is cancelled by [onHoldEnd]
     * the instant the finger lifts, which on a completed hold is typically
     * within a few tens of milliseconds. Sharing the job would make the second
     * beat depend on how slowly the user let go. Emitted after
     * [ButtonStateMachine.onTap] so the haptic can never delay the press.
     */
    private fun emitSecondCommitBeat() {
        commitBeatJob?.cancel()
        commitBeatJob = viewModelScope.launch(dispatchers.default) {
            delay(COMMIT_BEAT_GAP_MILLIS)
            _hapticCues.tryEmit(HapticCue.PressCommitted)
        }
    }

    /**
     * Finger lifted or drifted away. An incomplete hold resets the machine to
     * `Ready` rather than leaving it parked mid-sequence: with no separate
     * arming step there is no half-armed state worth preserving, and the next
     * hold should start from scratch. A completed hold already cleared
     * [isHolding], so this is a no-op after one.
     */
    fun onHoldEnd() {
        val incomplete = _isHolding.value
        holdJob?.cancel()
        holdJob = null
        _isHolding.value = false
        if (incomplete) {
            _hapticCues.tryEmit(HapticCue.HoldAborted)
            stateMachine.reset()
        }
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
     * happening; an in-progress hold is deliberately NOT a trigger (the
     * user's finger is already on the screen, keeping it awake).
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

        /**
         * When the midpoint haptic fires. A pacing cue ("about one more
         * second"), deliberately NOT a point of no return — releasing cancels
         * right up to the end, which is a safety property worth more than
         * tidier haptic semantics.
         */
        const val HOLD_HALFWAY_MILLIS: Long = HOLD_TO_CONFIRM_MILLIS / 2

        /**
         * Gap between the two beats of the commit buzz. Long enough to be felt
         * as two events rather than one smeared one, short enough to still be
         * the same event — and it lands under the screen's commit bloom.
         */
        const val COMMIT_BEAT_GAP_MILLIS: Long = 110L

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

        /**
         * Room for the longest real burst (engaged, halfway, committed) plus
         * slack. Overflow drops the oldest rather than blocking the hold timer.
         */
        private const val HAPTIC_BUFFER = 8
    }
}
