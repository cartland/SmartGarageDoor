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
fun VoiceCommandIgnoreReason.displayText(): String =
    when (this) {
        VoiceCommandIgnoreReason.NO_SPEECH ->
            stringResource(R.string.voice_control_ignored_no_speech)
        VoiceCommandIgnoreReason.RECOGNIZER_UNAVAILABLE ->
            stringResource(R.string.voice_control_ignored_unavailable)
        VoiceCommandIgnoreReason.NOT_A_COMMAND ->
            stringResource(R.string.voice_control_ignored_not_a_command)
        VoiceCommandIgnoreReason.NOT_CONFIDENT ->
            stringResource(R.string.voice_control_ignored_not_confident)
        VoiceCommandIgnoreReason.DOOR_ALREADY_OPEN ->
            stringResource(R.string.voice_control_ignored_already_open)
        VoiceCommandIgnoreReason.DOOR_ALREADY_CLOSED ->
            stringResource(R.string.voice_control_ignored_already_closed)
        VoiceCommandIgnoreReason.DOOR_MOVING ->
            stringResource(R.string.voice_control_ignored_moving)
        VoiceCommandIgnoreReason.DOOR_STUCK ->
            stringResource(R.string.voice_control_ignored_stuck)
        VoiceCommandIgnoreReason.DOOR_STATE_UNKNOWN ->
            stringResource(R.string.voice_control_ignored_state_unknown)
        VoiceCommandIgnoreReason.DOOR_STATE_CHANGED ->
            stringResource(R.string.voice_control_ignored_state_changed)
        VoiceCommandIgnoreReason.SERVER_UNREACHABLE ->
            stringResource(R.string.voice_control_ignored_server_unreachable)
    }
