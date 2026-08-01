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
import com.chriscartland.garage.usecase.ClassifyVoiceIntentUseCase
import com.chriscartland.garage.usecase.VoiceCommandController
import com.chriscartland.garage.usecase.VoiceCommandEnvironment
import com.chriscartland.garage.usecase.VoiceCommandState
import com.chriscartland.garage.usecase.VoiceDoorState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Shared implementation of the watch's voice surface: the controller, the
 * haptics, and the live transcript. Subclasses supply the one thing that
 * differs — the [VoiceCommandEnvironment], which is to say which door a
 * committed command actually moves.
 *
 * ## Two surfaces, one loop
 *
 * [WearLiveVoiceViewModel] presses the REAL remote button against the REAL
 * observed door. [WearSimulatedVoiceViewModel] presses a pretend button against
 * an in-memory one. Everything else — the classifier, the two-stage door gate,
 * the cancel window, the haptic choreography — is this class, so the simulation
 * rehearses the real interaction rather than resembling it. A second copy of
 * this loop would drift from the first, and a rehearsal that has drifted
 * teaches the wrong thing.
 *
 * ## Why an abstract class
 *
 * The repo's standing rule is interface + `Default*` rather than open classes,
 * because `open` is usually there so a test can substitute behaviour. This is
 * the other case: nothing substitutes this, it is shared *implementation* for
 * two concrete surfaces that must not diverge. The subclass boundary is also
 * what carries the safety property — see [WearSimulatedVoiceViewModel], whose
 * constructor is asserted to have no route to the remote button. A single class
 * taking an environment could make no such promise, because the promise would
 * then depend on the call site rather than on the type.
 *
 * The environment is built from [viewModelScope] rather than injected so it
 * lives and dies with the surface that owns it: a fake transit or a projected
 * door state cannot outlive the screen, and nothing has to remember to tear it
 * down.
 */
abstract class WearVoiceViewModel(
    classifyVoiceIntent: ClassifyVoiceIntentUseCase,
    dispatchers: DispatcherProvider,
    environmentFactory: (CoroutineScope) -> VoiceCommandEnvironment,
) : ViewModel() {
    /**
     * The world this surface acts on. Constructed here, from this ViewModel's
     * own scope, so a subclass never has to hold a scope just to build one.
     *
     * `protected` only so a subclass can re-expose it at a narrower type when
     * that is useful — see [WearSimulatedVoiceViewModel.demoDoor]. Nothing may
     * widen it: the whole design is that the environment is decided by which
     * class you constructed, not by who can reach it afterwards.
     */
    protected val environment: VoiceCommandEnvironment = environmentFactory(viewModelScope)

    private val controller = VoiceCommandController(
        classify = classifyVoiceIntent,
        environment = environment,
        scope = viewModelScope,
        initialArmedWindowMs = ARMED_WINDOW_MILLIS,
        resultFlashMs = RESULT_FLASH_MILLIS,
    )

    /** Pass-through of the controller's state (ADR-022 — no re-wrapping). */
    val state: StateFlow<VoiceCommandState> = controller.state

    /**
     * The door the gate is reasoning about, so the screen can show it. Real on
     * the live surface, pretend on the simulated one — without it a refusal
     * like "already open" reads as arbitrary.
     */
    val doorState: StateFlow<VoiceDoorState> = environment.doorState

    private val _partialTranscript = MutableStateFlow<String?>(null)

    /**
     * What the recognizer thinks it is hearing, while it is still hearing it.
     *
     * Only populated by the in-app capture path; the system-dialog fallback
     * draws its own UI and never reports partials. Without this, replacing a
     * rich system screen with a static "Listening…" label would be a
     * downgrade — this is what makes the in-app path feel responsive.
     */
    val partialTranscript: StateFlow<String?> = _partialTranscript

    private val _hapticCues = MutableSharedFlow<HapticCue>(
        replay = 0,
        extraBufferCapacity = HAPTIC_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * One-shot haptic moments. Same primitive and same reasoning as
     * [WearHomeViewModel.hapticCues]: a plain StateFlow would conflate a cue
     * whose neighbour repeated (two refusals in a row is exactly the case that
     * matters here), and a Channel would *queue* cues for a screen that is not
     * on top and replay them all when it comes back. A buzz nobody can feel is
     * dropped, not saved for later.
     */
    val hapticCues: Flow<HapticCue> = _hapticCues

    /** Pending midpoint tick for the running cancel window, if any. */
    private var halfwayJob: Job? = null

    /** Pending second beat of the commit buzz, if any. */
    private var commitBeatJob: Job? = null

    /**
     * What the controller said last, so a move INTO `Ready` can be told apart
     * from resting there.
     *
     * `Ready` is reached three ways — at startup, when a terminal outcome
     * expires, and when the user cancels — and only the third is an abandoned
     * countdown worth buzzing about. Without this the abort cue would also
     * fire every time a refusal timed out, which is the opposite of the hold's
     * behaviour it exists to match.
     */
    private var previousState: VoiceCommandState = VoiceCommandState.Ready

    init {
        // What actually keeps opening the screen silent is the exhaustive
        // `when` below: the initial state is Ready, and Ready emits no cue.
        // (Verified by mutation — deleting this drop(1) changes no test.)
        // It is kept as defence for one plausible future: if the controller
        // ever became shared rather than per-ViewModel, re-entering the screen
        // mid-flow would replay a non-Ready state and buzz on arrival.
        viewModelScope.launch(dispatchers.default) {
            controller.state.drop(1).collect { current ->
                // Partial text belongs to exactly one capture attempt. Clearing
                // it here, in the one place state transitions are observed,
                // means it cannot survive into the outcome that replaces it.
                if (current !is VoiceCommandState.Listening) {
                    _partialTranscript.value = null
                }
                // Leaving Armed for ANY reason kills the pending midpoint tick.
                // Cancelling and then feeling a pacing cue for a countdown that
                // is no longer running would be worse than never having one.
                if (current !is VoiceCommandState.Armed) {
                    halfwayJob?.cancel()
                    halfwayJob = null
                }
                val previous = previousState
                previousState = current
                when (current) {
                    is VoiceCommandState.Armed -> {
                        _hapticCues.tryEmit(HapticCue.VoiceArmed)
                        // Scheduled rather than derived from a state change,
                        // because the midpoint of the cancel window is not a
                        // state — the same reason HoldEngaged/HoldHalfway are
                        // timer points on the hero screen rather than
                        // observable transitions.
                        halfwayJob?.cancel()
                        halfwayJob = viewModelScope.launch(dispatchers.default) {
                            delay(current.windowMs / 2)
                            _hapticCues.tryEmit(HapticCue.VoiceHalfway)
                        }
                    }
                    // Sending, not Sent: this fires the instant the cancel
                    // window elapses, which is the moment the real feature
                    // would press the remote. Sent arrives a fake round-trip
                    // later and would put the buzz in the wrong place.
                    is VoiceCommandState.Sending -> {
                        _hapticCues.tryEmit(HapticCue.VoiceCommitted)
                        emitSecondCommitBeat()
                    }
                    is VoiceCommandState.Ignored,
                    is VoiceCommandState.Failed,
                    -> _hapticCues.tryEmit(HapticCue.VoiceRefused)
                    // Leaving a RUNNING countdown for Ready is a cancellation,
                    // and it feels like lifting a finger off the door — same
                    // cue, from the same place in the same journey. Reaching
                    // Ready from anywhere else is an outcome expiring, which
                    // the user did not do and should not feel.
                    VoiceCommandState.Ready ->
                        if (previous is VoiceCommandState.Armed) {
                            _hapticCues.tryEmit(HapticCue.VoiceAborted)
                        }
                    is VoiceCommandState.Listening,
                    is VoiceCommandState.Sent,
                    -> Unit
                }
            }
        }
    }

    /**
     * The second half of the commit buzz, on its own job.
     *
     * Mirrors `WearHomeViewModel.emitSecondCommitBeat` deliberately, and for
     * the same reason: the commit is the one irreversible moment the gesture
     * has, the screen marks it with a full-screen bloom, and a single tick
     * under that reads as an understatement. Wrist actuators are too coarse to
     * convey "harder", so emphasis is expressed as *again*.
     *
     * On its own job so it cannot be cancelled by whatever the controller does
     * next — `Sending` is brief on a fast network, and the second beat belongs
     * to the commit rather than to however long the server took.
     */
    private fun emitSecondCommitBeat() {
        commitBeatJob?.cancel()
        commitBeatJob = viewModelScope.launch {
            delay(WearConfirmTiming.COMMIT_BEAT_GAP_MILLIS)
            _hapticCues.tryEmit(HapticCue.VoiceCommitted)
        }
    }

    /** Mic tapped while nothing is running: start listening. */
    fun onMicTap() = controller.onMicTap()

    /**
     * Tapped while something IS running: stop it and go back to Ready.
     *
     * The watch uses cancel-to-Ready rather than the phone's
     * cancel-and-re-listen because here the whole screen is the tap target, so
     * a brush during the countdown must not open a live mic. It also makes the
     * on-screen promise ("Tap anywhere to cancel") literally true.
     */
    fun onCancel() = controller.onCancel()

    /** Recognizer returned. Null or blank means no usable speech. */
    fun onTranscript(text: String?) = controller.onTranscript(text)

    /**
     * Interim recognizer text. Ignored unless a capture is actually running,
     * so a late callback from an abandoned attempt cannot paint text over the
     * outcome of the next one.
     */
    fun onPartialTranscript(text: String) {
        if (controller.state.value is VoiceCommandState.Listening) {
            _partialTranscript.value = text
        }
    }

    /** The watch has no speech recognizer to launch. */
    fun onCaptureUnavailable() = controller.onCaptureUnavailable()

    /** App backgrounded: cancels a pending command so nothing commits off-screen. */
    fun onBackgrounded() = controller.onBackgrounded()

    /**
     * The voice screen was popped (swiped back), which ends the session.
     *
     * Distinct from [onBackgrounded], and needed because a swipe-back is not a
     * lifecycle stop — the app is still perfectly foreground, so nothing else
     * tells the controller to stop. Without this, walking away mid-flow left
     * the countdown running behind the hero screen: it would commit off-screen,
     * buzz the wrist for a command the user had abandoned, and still be sitting
     * on that outcome when they came back.
     *
     * Cancels [VoiceCommandState.Listening] as well as `Armed`, so the
     * microphone cannot outlive the screen that opened it. `Sending` is left
     * alone for the usual reason, and the terminal states expire on their own.
     */
    fun onScreenLeft() = controller.onCancel()

    companion object {
        /**
         * Cancel window — the SAME duration the hold takes to sweep, from the
         * same constant ([WearConfirmTiming.RING_JOURNEY_MILLIS]).
         *
         * It used to be the controller's 3s maximum, on the reasoning that a
         * glanced-at watch wants the most forgiving window available. That was
         * right while voice was a simulation. Now that a completed countdown
         * presses the real button, the ring on this screen is making the same
         * promise as the ring on the door screen — "when I complete, the door
         * moves" — and a promise that takes a different time on each screen
         * teaches that the ring is decorative.
         */
        const val ARMED_WINDOW_MILLIS: Long = WearConfirmTiming.RING_JOURNEY_MILLIS

        /**
         * How long the outcome stays up — deliberately longer than the shared
         * 1.5s default, and equal to the time a refusal already gets.
         *
         * On the phone that default is right: the outcome is a receipt for
         * something the user is already looking at. On a wrist the outcome
         * carries two lines — what happened and what to expect next — and two
         * lines of new information is not a 1.5-second read. The simulation
         * needs the time even more, since "Nothing was sent" is its punchline.
         */
        const val RESULT_FLASH_MILLIS: Long = VoiceCommandController.IGNORED_DISMISS_MS

        /**
         * Room for the longest real burst (armed, then committed) plus slack.
         * Overflow drops the oldest rather than blocking the state machine.
         */
        private const val HAPTIC_BUFFER = 8
    }
}
