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

package com.chriscartland.garage.usecase

import com.chriscartland.garage.domain.model.VoiceIntent
import com.chriscartland.garage.domain.model.VoiceIntentClassification
import com.chriscartland.garage.domain.model.VoiceIntentConfidence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Door state as the voice-command gate sees it. A projection of the
 * richer door-event model: the gate only needs "is the door settled,
 * and which side" to decide whether a spoken direction is actionable.
 */
enum class VoiceDoorState { CLOSED, OPEN, MOVING, UNKNOWN }

/** Why a capture ended without arming a command. */
enum class VoiceCommandIgnoreReason {
    /** The recognizer returned no usable speech (silence, cancel). */
    NO_SPEECH,

    /** No speech recognizer exists on this device. */
    RECOGNIZER_UNAVAILABLE,

    /** Classifier verdict UNKNOWN: not a door command at all. */
    NOT_A_COMMAND,

    /** Classifier verdict MEDIUM: heard a direction, not confident enough to act. */
    NOT_CONFIDENT,

    /** Open command while the door is already open. */
    DOOR_ALREADY_OPEN,

    /** Close command while the door is already closed. */
    DOOR_ALREADY_CLOSED,

    /** The door is mid-motion; commands only apply to settled states. */
    DOOR_MOVING,

    /** The door state is unknown; refuse rather than guess. */
    DOOR_STATE_UNKNOWN,

    /** The door state changed during the cancel window; commit aborted. */
    DOOR_STATE_CHANGED,
}

/**
 * States of the voice-command loop. The mic button renders these; taps
 * feed back into [VoiceCommandController].
 *
 * There are two verbs, and a surface picks the one that matches its tap
 * target. [VoiceCommandController.onMicTap] means "listen to me now" and
 * restarts from any pre-commit state, which is right for a small mic button.
 * [VoiceCommandController.onCancel] means "stop", which is right when the
 * whole screen is the target and a brush must not open a live mic.
 */
sealed interface VoiceCommandState {
    /** Mic idle, waiting for a tap. */
    data object Ready : VoiceCommandState

    /**
     * Speech capture in progress. [attempt] increments per capture so UI
     * launch effects re-fire when a cancel immediately re-listens.
     */
    data class Listening(
        val attempt: Int,
    ) : VoiceCommandState

    /**
     * A HIGH-confidence command passed the door-state gate and is
     * counting down. Before [windowMs] elapses the surface can still call
     * [VoiceCommandController.onMicTap] (cancel and re-listen) or
     * [VoiceCommandController.onCancel] (cancel and stop); letting it
     * complete re-checks the gate and commits.
     */
    data class Armed(
        val intent: VoiceIntent,
        val transcript: String,
        val windowMs: Long,
    ) : VoiceCommandState

    /** Button press in flight. Not cancellable — a press cannot be unsent. */
    data class Sending(
        val intent: VoiceIntent,
    ) : VoiceCommandState

    /** Press succeeded; brief confirmation before returning to [Ready]. */
    data class Sent(
        val intent: VoiceIntent,
    ) : VoiceCommandState

    /** Press failed; brief error before returning to [Ready]. */
    data class Failed(
        val intent: VoiceIntent,
    ) : VoiceCommandState

    /**
     * The capture ended without a committed action. Renders as a
     * transient explanation, then auto-returns to [Ready].
     */
    data class Ignored(
        val reason: VoiceCommandIgnoreReason,
        val transcript: String?,
        val classification: VoiceIntentClassification?,
        val engineName: String,
    ) : VoiceCommandState {
        /**
         * Paste-friendly structured summary (same conventions as the
         * transcription playground's verdict copy) so misses can be
         * shared and turned into eval-corpus rows.
         */
        fun clipboardSummary(): String =
            buildString {
                appendLine("input: " + (transcript?.let { "\"$it\"" } ?: "none"))
                classification?.let {
                    appendLine("intent: ${it.intent.name}")
                    appendLine("confidence: ${it.confidence.name}")
                    appendLine("engine: $engineName")
                }
                append("outcome: ${reason.name}")
            }
    }
}

/**
 * The world the voice-command loop acts on: the door's current state
 * and the one action it can take. The playground injects
 * [SimulatedVoiceCommandEnvironment]; the Home surface injects
 * [RemoteButtonVoiceCommandEnvironment] (real door state in, real
 * remote button press out).
 *
 * Contract: [pressButton] reports failure by returning `false`, never
 * by throwing.
 */
interface VoiceCommandEnvironment {
    val doorState: StateFlow<VoiceDoorState>

    suspend fun pressButton(intent: VoiceIntent): Boolean
}

/**
 * The voice-command state machine (shared KMP; UI-free). One capture
 * flows: transcript → classify → gate → cancel window → commit.
 *
 * Safety properties, per the standing principle "okay to incorrectly
 * ignore, never okay to incorrectly execute":
 * - Only a HIGH-confidence classification can arm. MEDIUM and UNKNOWN
 *   are ignored with an explanation.
 * - The door-state gate runs twice: at arm time and again when the
 *   cancel window elapses — a door that moved mid-countdown aborts the
 *   commit ([VoiceCommandIgnoreReason.DOOR_STATE_CHANGED]).
 * - [onBackgrounded] cancels a pending command: nothing commits while
 *   the UI is off-screen.
 * - [onMicTap] during [VoiceCommandState.Armed] cancels and immediately
 *   re-listens; during [VoiceCommandState.Sending] it is a no-op (the
 *   press is already on its way).
 * - [onCancel] stops instead of restarting, for surfaces whose tap target
 *   is large enough that an accidental touch must not open a live mic.
 *   Also a no-op during [VoiceCommandState.Sending].
 */
class VoiceCommandController(
    private val classify: ClassifyVoiceIntentUseCase,
    private val environment: VoiceCommandEnvironment,
    private val scope: CoroutineScope,
    initialArmedWindowMs: Long = DEFAULT_ARMED_WINDOW_MS,
    /**
     * How long [VoiceCommandState.Sent] / [VoiceCommandState.Failed] stay up
     * before returning to [VoiceCommandState.Ready]. Configurable because the
     * outcome carries different weight per surface: on the real button it is a
     * receipt for something the user already watched happen, while on the Wear
     * demo "nothing was sent" IS the point of the whole exercise and needs long
     * enough to read on a wrist.
     */
    private val resultFlashMs: Long = RESULT_FLASH_MS,
) {
    private val _state = MutableStateFlow<VoiceCommandState>(VoiceCommandState.Ready)
    val state: StateFlow<VoiceCommandState> = _state

    private val _armedWindowMs = MutableStateFlow(coerceWindow(initialArmedWindowMs))
    val armedWindowMs: StateFlow<Long> = _armedWindowMs

    // The one in-flight coroutine (countdown, or transient auto-return).
    // Only user-input handlers cancel it; transitions from inside the
    // running job overwrite the reference instead of self-cancelling.
    private var job: Job? = null
    private var listeningAttempt = 0

    fun onMicTap() {
        when (_state.value) {
            // Dialog already up / press already sent: nothing to restart.
            is VoiceCommandState.Listening, is VoiceCommandState.Sending -> Unit
            else -> startListening()
        }
    }

    /**
     * Stop whatever is in progress and return to [VoiceCommandState.Ready].
     *
     * Distinct from [onMicTap] on purpose. A tap means "listen to me now" and
     * therefore *restarts* from a pre-commit state, which is the right rule for
     * a small on-screen mic button: correcting "close… no wait, open" costs one
     * tap. It is the wrong rule when the whole screen is the tap target (Wear),
     * where a brush during the countdown would drop the user into a live mic
     * session they never asked for. Callers pick the verb that matches their
     * surface; nothing here changes for existing [onMicTap] callers.
     *
     * [VoiceCommandState.Sending] is deliberately untouched — a press cannot be
     * unsent — as are terminal states, which expire on their own.
     */
    fun onCancel() {
        when (_state.value) {
            is VoiceCommandState.Listening, is VoiceCommandState.Armed -> {
                job?.cancel()
                job = null
                _state.value = VoiceCommandState.Ready
            }
            else -> Unit
        }
    }

    /** Recognizer outcome. Null or blank means no usable speech. */
    fun onTranscript(text: String?) {
        if (_state.value !is VoiceCommandState.Listening) return
        if (text.isNullOrBlank()) {
            ignore(VoiceCommandIgnoreReason.NO_SPEECH, transcript = null, classification = null)
            return
        }
        val classification = classify(text)
        when (classification.confidence) {
            VoiceIntentConfidence.NONE ->
                ignore(VoiceCommandIgnoreReason.NOT_A_COMMAND, text, classification)
            VoiceIntentConfidence.MEDIUM ->
                ignore(VoiceCommandIgnoreReason.NOT_CONFIDENT, text, classification)
            VoiceIntentConfidence.HIGH -> arm(classification, text)
        }
    }

    /** The device has no speech recognizer (capture launch failed). */
    fun onCaptureUnavailable() {
        if (_state.value !is VoiceCommandState.Listening) return
        ignore(VoiceCommandIgnoreReason.RECOGNIZER_UNAVAILABLE, transcript = null, classification = null)
    }

    /**
     * The app went to the background (lifecycle stop). Cancels a pending
     * command so nothing commits off-screen.
     *
     * [VoiceCommandState.Listening] is deliberately untouched: on a surface
     * that captures speech with the *system* recognizer, the recognizer's own
     * activity coming to the front is exactly what backgrounds us, so
     * cancelling here would abort every capture at the moment it started. A
     * surface with in-app capture, or one that wants "leaving ends the
     * session", calls [onCancel] instead — see the Wear demo, which does both
     * (this on stop, [onCancel] when its screen is popped).
     */
    fun onBackgrounded() {
        if (_state.value is VoiceCommandState.Armed) {
            job?.cancel()
            job = null
            _state.value = VoiceCommandState.Ready
        }
    }

    fun setArmedWindowMs(ms: Long) {
        _armedWindowMs.value = coerceWindow(ms)
    }

    private fun startListening() {
        job?.cancel()
        job = null
        listeningAttempt += 1
        _state.value = VoiceCommandState.Listening(listeningAttempt)
    }

    private fun arm(
        classification: VoiceIntentClassification,
        transcript: String,
    ) {
        val gateReason = gateReason(classification.intent, environment.doorState.value)
        if (gateReason != null) {
            ignore(gateReason, transcript, classification)
            return
        }
        val windowMs = _armedWindowMs.value
        _state.value = VoiceCommandState.Armed(classification.intent, transcript, windowMs)
        job = scope.launch {
            delay(windowMs)
            commit(classification, transcript)
        }
    }

    private suspend fun commit(
        classification: VoiceIntentClassification,
        transcript: String,
    ) {
        // Second gate check: the world may have moved during the window.
        if (gateReason(classification.intent, environment.doorState.value) != null) {
            ignore(VoiceCommandIgnoreReason.DOOR_STATE_CHANGED, transcript, classification)
            return
        }
        _state.value = VoiceCommandState.Sending(classification.intent)
        val success = environment.pressButton(classification.intent)
        val result = if (success) {
            VoiceCommandState.Sent(classification.intent)
        } else {
            VoiceCommandState.Failed(classification.intent)
        }
        _state.value = result
        scheduleReturnToReady(result, resultFlashMs)
    }

    private fun ignore(
        reason: VoiceCommandIgnoreReason,
        transcript: String?,
        classification: VoiceIntentClassification?,
    ) {
        val ignored = VoiceCommandState.Ignored(
            reason = reason,
            transcript = transcript,
            classification = classification,
            engineName = classify.engineName,
        )
        _state.value = ignored
        scheduleReturnToReady(ignored, IGNORED_DISMISS_MS)
    }

    private fun scheduleReturnToReady(
        from: VoiceCommandState,
        delayMs: Long,
    ) {
        // Overwrites `job` — safe when called from inside the old job
        // (the old coroutine is completing) and the identity guard keeps
        // a stale timer from clobbering a newer state.
        job = scope.launch {
            delay(delayMs)
            if (_state.value === from) {
                _state.value = VoiceCommandState.Ready
            }
        }
    }

    private fun gateReason(
        intent: VoiceIntent,
        door: VoiceDoorState,
    ): VoiceCommandIgnoreReason? =
        when (door) {
            VoiceDoorState.MOVING -> VoiceCommandIgnoreReason.DOOR_MOVING
            VoiceDoorState.UNKNOWN -> VoiceCommandIgnoreReason.DOOR_STATE_UNKNOWN
            VoiceDoorState.OPEN ->
                if (intent == VoiceIntent.OPEN) VoiceCommandIgnoreReason.DOOR_ALREADY_OPEN else null
            VoiceDoorState.CLOSED ->
                if (intent == VoiceIntent.CLOSE) VoiceCommandIgnoreReason.DOOR_ALREADY_CLOSED else null
        }

    private fun coerceWindow(ms: Long): Long = ms.coerceIn(MIN_ARMED_WINDOW_MS, MAX_ARMED_WINDOW_MS)

    companion object {
        const val DEFAULT_ARMED_WINDOW_MS = 3_000L
        const val MIN_ARMED_WINDOW_MS = 500L
        const val MAX_ARMED_WINDOW_MS = 3_000L

        /** How long an [VoiceCommandState.Ignored] explanation stays up. */
        const val IGNORED_DISMISS_MS = 4_000L

        /** Default for `resultFlashMs` — how long Sent/Failed stays up. */
        const val RESULT_FLASH_MS = 1_500L
    }
}
