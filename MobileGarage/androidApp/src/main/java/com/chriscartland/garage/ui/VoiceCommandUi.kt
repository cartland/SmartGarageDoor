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

package com.chriscartland.garage.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.chriscartland.garage.R
import com.chriscartland.garage.domain.model.VoiceIntent
import com.chriscartland.garage.usecase.VoiceCommandIgnoreReason
import com.chriscartland.garage.usecase.VoiceCommandState
import kotlin.math.ceil

/**
 * Shared visual vocabulary for the voice-command surfaces (the Home
 * voice card and the Settings playground sheet) so the two render the
 * state machine identically: same icons, same countdown math, same
 * refusal wording.
 */
object VoiceCommandUi {
    /** Whole seconds remaining in the cancel window, floored at 1. */
    fun secondsLeft(
        windowMs: Long,
        progress: Float,
    ): Int = ceil((1f - progress) * windowMs / 1000f).toInt().coerceAtLeast(1)

    // "0.5", "1", "1.5" — trims the trailing .0 on whole seconds.
    fun windowSecondsLabel(windowMs: Long): String {
        val seconds = windowMs / 1000f
        val whole = seconds.toInt()
        return if (seconds == whole.toFloat()) whole.toString() else seconds.toString()
    }

    // Door-motion metaphor: up = opening, down = closing.
    fun directionIcon(intent: VoiceIntent): ImageVector =
        if (intent == VoiceIntent.CLOSE) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward

    /**
     * Whether the card's tap cancels rather than opens the mic. The label
     * under the countdown says "Tap to cancel", so while a command is armed
     * the tap cancels and does nothing else (strategy 2.5 — Wear's rule);
     * every other state's tap is the mic.
     */
    fun cancelsOnTap(state: VoiceCommandState): Boolean = state is VoiceCommandState.Armed
}

/** Icon shown inside the mic button for each state. */
fun VoiceCommandState.micIcon(): ImageVector =
    when (this) {
        VoiceCommandState.Ready, is VoiceCommandState.Listening, is VoiceCommandState.Ignored ->
            Icons.Outlined.Mic
        is VoiceCommandState.Armed -> VoiceCommandUi.directionIcon(intent)
        is VoiceCommandState.Sending -> VoiceCommandUi.directionIcon(intent)
        is VoiceCommandState.Sent -> Icons.Outlined.Check
        is VoiceCommandState.Failed -> Icons.Outlined.ErrorOutline
    }

@Composable
fun VoiceCommandState.micContentDescription(): String =
    when (this) {
        VoiceCommandState.Ready, is VoiceCommandState.Ignored ->
            stringResource(R.string.voice_control_mic_cd_ready)
        is VoiceCommandState.Listening -> stringResource(R.string.voice_control_mic_cd_listening)
        is VoiceCommandState.Armed -> stringResource(R.string.voice_control_mic_cd_armed)
        is VoiceCommandState.Sending -> stringResource(R.string.voice_control_mic_cd_sending)
        is VoiceCommandState.Sent, is VoiceCommandState.Failed ->
            stringResource(R.string.voice_control_mic_cd_ready)
    }

@Composable
fun VoiceCommandIgnoreReason.displayText(): String = stringResource(VoiceWords.ignoredLine(this))

/**
 * Which door a voice surface acts on. Ported from Wear's `VoiceSurfaceMode`:
 * the Home card presses the real remote; the Settings sheet presses a pretend
 * one, and its words must never claim otherwise.
 */
enum class VoiceSurfaceMode {
    Live,
    Simulated,
}

/**
 * The words for each voice state, per surface: the live surface states the
 * action as fact, the simulation keeps it conditional and ends by saying
 * outright that nothing was sent (strategy 2.5 — Wear's rule, ported). Pure
 * resource ids, so `VoiceWordsTest` can pin on the JVM that no
 * action-describing state shares one string across the two surfaces.
 *
 * Refusals are the same words on both: they are about the utterance or about
 * the door, and the simulated sheet already labels which door it is showing.
 */
object VoiceWords {
    @StringRes
    fun primaryLine(
        state: VoiceCommandState,
        mode: VoiceSurfaceMode,
    ): Int {
        val live = mode == VoiceSurfaceMode.Live
        return when (state) {
            VoiceCommandState.Ready -> R.string.home_voice_ready_title
            is VoiceCommandState.Listening -> R.string.voice_control_listening
            is VoiceCommandState.Armed ->
                when {
                    state.intent == VoiceIntent.CLOSE && live -> R.string.voice_control_armed_closing
                    state.intent == VoiceIntent.CLOSE -> R.string.voice_sim_armed_closing
                    live -> R.string.voice_control_armed_opening
                    else -> R.string.voice_sim_armed_opening
                }
            is VoiceCommandState.Sending -> if (live) R.string.voice_control_sending else R.string.voice_sim_sending
            is VoiceCommandState.Sent -> if (live) R.string.voice_control_sent else R.string.voice_sim_sent
            is VoiceCommandState.Failed -> if (live) R.string.voice_control_failed else R.string.voice_sim_failed
            is VoiceCommandState.Ignored -> ignoredLine(state.reason)
        }
    }

    /** The second line when there is no transcript to quote; the card quotes one when there is. */
    @StringRes
    fun secondaryLine(
        state: VoiceCommandState,
        mode: VoiceSurfaceMode,
    ): Int {
        val live = mode == VoiceSurfaceMode.Live
        return when (state) {
            is VoiceCommandState.Sending -> if (live) R.string.home_voice_sending_subtitle else R.string.voice_sim_sending_subtitle
            is VoiceCommandState.Sent -> if (live) R.string.home_voice_sent_subtitle else R.string.voice_sim_sent_subtitle
            is VoiceCommandState.Failed -> if (live) R.string.home_voice_failed_subtitle else R.string.voice_sim_failed_subtitle
            VoiceCommandState.Ready,
            is VoiceCommandState.Listening,
            is VoiceCommandState.Armed,
            is VoiceCommandState.Ignored,
            -> R.string.home_voice_hint
        }
    }

    @StringRes
    fun ignoredLine(reason: VoiceCommandIgnoreReason): Int =
        when (reason) {
            VoiceCommandIgnoreReason.NO_SPEECH -> R.string.voice_control_ignored_no_speech
            VoiceCommandIgnoreReason.RECOGNIZER_UNAVAILABLE -> R.string.voice_control_ignored_unavailable
            VoiceCommandIgnoreReason.NOT_A_COMMAND -> R.string.voice_control_ignored_not_a_command
            VoiceCommandIgnoreReason.NOT_CONFIDENT -> R.string.voice_control_ignored_not_confident
            VoiceCommandIgnoreReason.DOOR_ALREADY_OPEN -> R.string.voice_control_ignored_already_open
            VoiceCommandIgnoreReason.DOOR_ALREADY_CLOSED -> R.string.voice_control_ignored_already_closed
            VoiceCommandIgnoreReason.DOOR_MOVING -> R.string.voice_control_ignored_moving
            VoiceCommandIgnoreReason.DOOR_STUCK -> R.string.voice_control_ignored_stuck
            VoiceCommandIgnoreReason.DOOR_STATE_UNKNOWN -> R.string.voice_control_ignored_state_unknown
            VoiceCommandIgnoreReason.DOOR_STATE_CHANGED -> R.string.voice_control_ignored_state_changed
            VoiceCommandIgnoreReason.SERVER_UNREACHABLE -> R.string.voice_control_ignored_server_unreachable
        }
}
