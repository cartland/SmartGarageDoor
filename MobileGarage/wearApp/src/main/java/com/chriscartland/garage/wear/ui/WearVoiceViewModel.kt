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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the **simulated** voice-command demo.
 *
 * ## This can never operate the real garage door
 *
 * The demo drives the same shared [VoiceCommandController] the phone uses —
 * same classifier, same door-state gate, same cancel window — but points it at
 * a [VoiceCommandEnvironment] that is a fake in-memory door. The safety
 * property is *structural*, not a runtime check, and rests on three things
 * that are each pinned by a test:
 *
 *  1. This class has no reference to the remote button whatsoever: no
 *     `PushRemoteButtonUseCase`, no `RemoteButtonRepository`, no
 *     `ButtonStateMachine`. `WearVoiceViewModelTest` asserts that over the
 *     constructor by reflection, so wiring one in later fails a test rather
 *     than quietly shipping.
 *  2. The only [VoiceCommandEnvironment] in the Wear DI graph is
 *     `SimulatedVoiceCommandEnvironment` (`WearComponentGraphTest`).
 *  3. That environment's `pressButton` touches nothing but its own in-memory
 *     StateFlow (`SimulatedVoiceCommandEnvironmentTest`, in `:usecase`).
 *
 * The real remote button remains reachable only by holding the door on the
 * hero screen ([WearHomeViewModel]).
 *
 * ## Why route it through the real controller at all
 *
 * The point of the experiment is to learn whether the interaction works on a
 * watch — the recognizer round-trip, the cancel window, how refusals read on
 * a tiny screen. Reimplementing a toy version would demo the toy. Everything
 * except the final press is the production path.
 *
 * [demoDoorState] is exposed so the UI can show the fake door the gate is
 * reasoning about; without it, a refusal like "already open" looks arbitrary.
 */
class WearVoiceViewModel(
    classifyVoiceIntent: ClassifyVoiceIntentUseCase,
    environment: VoiceCommandEnvironment,
    dispatchers: DispatcherProvider,
) : ViewModel() {
    private val controller = VoiceCommandController(
        classify = classifyVoiceIntent,
        environment = environment,
        scope = viewModelScope,
        initialArmedWindowMs = ARMED_WINDOW_MILLIS,
    )

    /** Pass-through of the controller's state (ADR-022 — no re-wrapping). */
    val state: StateFlow<VoiceCommandState> = controller.state

    /** The simulated door the gate reads. Never the real one. */
    val demoDoorState: StateFlow<VoiceDoorState> = environment.doorState

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

    private val _hapticCues = Channel<HapticCue>(Channel.BUFFERED)

    /**
     * One-shot haptic moments, [Channel]-backed for the same reason as
     * [WearHomeViewModel.hapticCues]: these are events, and a StateFlow's
     * conflation would drop a cue whose neighbour repeated (two refusals in a
     * row is exactly the case that matters here).
     */
    val hapticCues: Flow<HapticCue> = _hapticCues.receiveAsFlow()

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
                when (current) {
                    is VoiceCommandState.Armed -> _hapticCues.trySend(HapticCue.VoiceArmed)
                    // Sending, not Sent: this fires the instant the cancel
                    // window elapses, which is the moment the real feature
                    // would press the remote. Sent arrives a fake round-trip
                    // later and would put the buzz in the wrong place.
                    is VoiceCommandState.Sending -> _hapticCues.trySend(HapticCue.VoiceCommitted)
                    is VoiceCommandState.Ignored,
                    is VoiceCommandState.Failed,
                    -> _hapticCues.trySend(HapticCue.VoiceRefused)
                    VoiceCommandState.Ready,
                    is VoiceCommandState.Listening,
                    is VoiceCommandState.Sent,
                    -> Unit
                }
            }
        }
    }

    /** Mic tapped. Always means "listen to me now" — see [VoiceCommandController]. */
    fun onMicTap() = controller.onMicTap()

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

    /** Screen left: cancels a pending command so nothing commits off-screen. */
    fun onBackgrounded() = controller.onBackgrounded()

    companion object {
        /**
         * Cancel window. The shared maximum (3s), chosen deliberately: a watch
         * is glanced at rather than watched, so the demo wants the most
         * forgiving window the controller allows.
         */
        const val ARMED_WINDOW_MILLIS: Long = VoiceCommandController.MAX_ARMED_WINDOW_MS
    }
}
