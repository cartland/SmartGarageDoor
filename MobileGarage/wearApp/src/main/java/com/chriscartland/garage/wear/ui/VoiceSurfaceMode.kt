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

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.chriscartland.garage.domain.model.VoiceIntent
import com.chriscartland.garage.usecase.VoiceCommandIgnoreReason
import com.chriscartland.garage.usecase.VoiceCommandState
import com.chriscartland.garage.usecase.VoiceDoorState
import com.chriscartland.garage.wear.R
import com.chriscartland.garage.wear.ui.theme.WearRingColorScheme
import com.chriscartland.garage.wear.ui.theme.WearRingColors

/**
 * Which world the voice screen is driving.
 *
 * One screen renders both, because a rehearsal that looked different from the
 * thing it rehearses would teach the wrong interaction. The mode changes only
 * what the screen SAYS and what colour it says it in — never the layout, the
 * timings, the gestures, or the loop.
 *
 * ## [Simulated] announces itself four ways
 *
 * One signal is easy to miss on a wrist, so they are layered and independent:
 *
 *  1. **A persistent SIMULATION marker** in the header, present in every state
 *     including the listening takeover that hides everything else.
 *  2. **A tinted ring** ([ringColors]). Hue survives a glance at a moving
 *     animation better than any word does — see [WearRingColors].
 *  3. **Conditional wording throughout** — "Would open the door", never
 *     "Opening" — with a terminal state that says outright nothing was sent.
 *  4. **The door line is labelled "Demo door"**, so the thing visibly reacting
 *     is never mistaken for the garage.
 *
 * Signals 1 and 2 are the ones that work without reading, which is the case
 * that matters: the risk is not misreading the screen, it is not reading it.
 */
enum class VoiceSurfaceMode {
    /** Reached from the door screen's mic. Presses the real garage button. */
    Live,

    /** Reached from Settings. Presses a pretend button on a pretend door. */
    Simulated,
    ;

    /** Neutral for the real press, azure for the rehearsal. */
    val ringColors: WearRingColorScheme
        get() = when (this) {
            Live -> WearRingColors.neutral
            Simulated -> WearRingColors.simulated
        }

    /**
     * Whether a commit has something genuinely outstanding.
     *
     * The in-flight ring means "the server has not answered yet". That is true
     * of a real press and false of a pretend one, so the simulation must not
     * draw it — it would be the single piece of the shared vocabulary that was
     * a lie, on the surface whose whole job is not to lie about this.
     */
    val hasRealRoundTrip: Boolean get() = this == Live
}

/**
 * Every user-visible string on the voice screen, chosen by [VoiceSurfaceMode].
 *
 * Kept as a pure mapper (ADR-035's shape: shared logic decides, the platform
 * words it) so the wording rules are unit-testable — in particular that the
 * simulated set never states an action as fact, which is a safety property
 * rather than a copy preference.
 */
internal object VoiceStrings {
    /**
     * States where something is running, so a tap means "stop" rather than
     * "start". Sending is excluded because a press cannot be unsent, and the
     * terminal states expire on their own.
     */
    fun isCancellable(state: VoiceCommandState): Boolean = state is VoiceCommandState.Listening || state is VoiceCommandState.Armed

    /** "Door: %1$s" or "Demo door: %1$s" — the header's context line. */
    @StringRes
    fun doorLine(mode: VoiceSurfaceMode): Int =
        when (mode) {
            VoiceSurfaceMode.Live -> R.string.voice_live_door
            VoiceSurfaceMode.Simulated -> R.string.voice_sim_door
        }

    /** The door's position, which reads the same whichever door it is. */
    @StringRes
    fun doorLabel(state: VoiceDoorState): Int =
        when (state) {
            VoiceDoorState.CLOSED -> R.string.door_state_closed
            VoiceDoorState.OPEN -> R.string.door_state_open
            VoiceDoorState.MOVING -> R.string.voice_door_moving
            VoiceDoorState.UNKNOWN -> R.string.door_state_unknown
        }

    /**
     * The headline.
     *
     * The live surface states what is happening; the simulated one keeps every
     * action conditional and ends by saying plainly that nothing was sent.
     */
    @StringRes
    fun primaryLine(
        state: VoiceCommandState,
        mode: VoiceSurfaceMode,
    ): Int {
        val live = mode == VoiceSurfaceMode.Live
        return when (state) {
            VoiceCommandState.Ready -> R.string.voice_ready
            is VoiceCommandState.Listening -> R.string.voice_listening
            is VoiceCommandState.Armed ->
                when {
                    state.intent == VoiceIntent.CLOSE && live -> R.string.voice_live_closing
                    state.intent == VoiceIntent.CLOSE -> R.string.voice_sim_would_close
                    live -> R.string.voice_live_opening
                    else -> R.string.voice_sim_would_open
                }
            is VoiceCommandState.Sending ->
                if (live) R.string.voice_live_committing else R.string.voice_sim_committing
            is VoiceCommandState.Sent ->
                if (live) R.string.voice_live_committed else R.string.voice_sim_committed
            is VoiceCommandState.Failed ->
                if (live) R.string.voice_live_failed else R.string.voice_sim_failed
            is VoiceCommandState.Ignored -> ignoredLine(state.reason, mode)
        }
    }

    /**
     * The supporting line. Armed offers the way out; refusals quote what was
     * actually heard, which is the single most useful thing to see when the
     * classifier says no.
     */
    @Composable
    fun secondaryLine(
        state: VoiceCommandState,
        mode: VoiceSurfaceMode,
        partialTranscript: String?,
    ): String =
        when (state) {
            // Live text while the in-app recognizer is hearing it. The system
            // fallback never reports partials, so it keeps the static hint.
            is VoiceCommandState.Listening ->
                partialTranscript?.let { stringResource(R.string.voice_transcript, it) }
                    ?: stringResource(R.string.voice_hint)
            is VoiceCommandState.Armed -> stringResource(R.string.voice_cancel_hint)
            is VoiceCommandState.Ignored ->
                state.transcript?.let { stringResource(R.string.voice_transcript, it) }
                    ?: stringResource(R.string.voice_hint)
            is VoiceCommandState.Sent ->
                stringResource(
                    if (mode == VoiceSurfaceMode.Live) {
                        R.string.voice_live_committed_hint
                    } else {
                        R.string.voice_sim_committed_hint
                    },
                )
            // Nothing. The example command is an invitation to speak, and the
            // one moment it must not be showing is while the screen is busy
            // committing and the mic button is disabled. The slot stays (the
            // block is bottom-anchored, so an absent line would drop the
            // headline into it) — it is simply empty.
            is VoiceCommandState.Sending -> ""
            else -> stringResource(R.string.voice_hint)
        }

    /**
     * Refusals. The four that are about the utterance read identically on both
     * surfaces; the five that are about the door name which door they mean.
     */
    @StringRes
    fun ignoredLine(
        reason: VoiceCommandIgnoreReason,
        mode: VoiceSurfaceMode,
    ): Int {
        val live = mode == VoiceSurfaceMode.Live
        return when (reason) {
            VoiceCommandIgnoreReason.NO_SPEECH -> R.string.voice_ignored_no_speech
            VoiceCommandIgnoreReason.RECOGNIZER_UNAVAILABLE -> R.string.voice_ignored_unavailable
            VoiceCommandIgnoreReason.NOT_A_COMMAND -> R.string.voice_ignored_not_a_command
            VoiceCommandIgnoreReason.NOT_CONFIDENT -> R.string.voice_ignored_not_confident
            VoiceCommandIgnoreReason.DOOR_ALREADY_OPEN ->
                if (live) R.string.voice_live_ignored_already_open else R.string.voice_sim_ignored_already_open
            VoiceCommandIgnoreReason.DOOR_ALREADY_CLOSED ->
                if (live) R.string.voice_live_ignored_already_closed else R.string.voice_sim_ignored_already_closed
            VoiceCommandIgnoreReason.DOOR_MOVING ->
                if (live) R.string.voice_live_ignored_moving else R.string.voice_sim_ignored_moving
            VoiceCommandIgnoreReason.DOOR_STATE_UNKNOWN ->
                if (live) R.string.voice_live_ignored_state_unknown else R.string.voice_sim_ignored_state_unknown
            VoiceCommandIgnoreReason.DOOR_STATE_CHANGED ->
                if (live) R.string.voice_live_ignored_state_changed else R.string.voice_sim_ignored_state_changed
        }
    }
}
