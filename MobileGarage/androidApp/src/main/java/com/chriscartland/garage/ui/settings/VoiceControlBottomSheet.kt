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

package com.chriscartland.garage.ui.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chriscartland.garage.R
import com.chriscartland.garage.domain.model.VoiceIntent
import com.chriscartland.garage.domain.model.VoiceIntentClassification
import com.chriscartland.garage.domain.model.VoiceIntentConfidence
import com.chriscartland.garage.usecase.VoiceCommandController
import com.chriscartland.garage.usecase.VoiceCommandIgnoreReason
import com.chriscartland.garage.usecase.VoiceCommandState
import com.chriscartland.garage.usecase.VoiceDoorState
import kotlin.math.ceil

private const val ARMED_WINDOW_STEP_MS = 1_000L

/**
 * Experimental voice-command UX playground (Settings → Developer →
 * Voice control). The full loop — mic tap → system speech dialog →
 * classify → door-state gate → cancel window → commit — running against
 * a SIMULATED door and pretend button ([com.chriscartland.garage.usecase.SimulatedVoiceCommandEnvironment]).
 * The real door is never touched from this sheet.
 *
 * The mic button is the whole interface: a tap always means "listen to
 * me now" — it starts over from any pre-commit state, cancelling a
 * pending command if there is one. The recognizer launch is driven by
 * the [VoiceCommandState.Listening] state (not the tap), so "tap in
 * Ready" and "tap cancels Armed" share one launch path.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceControlBottomSheet(
    state: VoiceCommandState,
    doorState: VoiceDoorState,
    armedWindowMs: Long,
    onMicTap: () -> Unit,
    onTranscript: (String?) -> Unit,
    onCaptureUnavailable: () -> Unit,
    onBackgrounded: () -> Unit,
    onSetDoorState: (VoiceDoorState) -> Unit,
    onSetArmedWindowMs: (Long) -> Unit,
    onCopy: (label: String, value: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val transcript = if (result.resultCode == Activity.RESULT_OK) {
            result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
        } else {
            null
        }
        onTranscript(transcript)
    }
    val voicePrompt = stringResource(R.string.voice_experiment_prompt)
    val listeningAttempt = (state as? VoiceCommandState.Listening)?.attempt
    LaunchedEffect(listeningAttempt) {
        if (listeningAttempt != null) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                ).putExtra(RecognizerIntent.EXTRA_PROMPT, voicePrompt)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            try {
                voiceLauncher.launch(intent)
            } catch (_: ActivityNotFoundException) {
                onCaptureUnavailable()
            }
        }
    }

    // Safety: never commit off-screen. ON_STOP cancels a pending
    // (Armed) command; Listening and Sending are unaffected (the system
    // dialog owns Listening, and a sent press cannot be recalled).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) onBackgrounded()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = {
            // Dismiss = leaving the playground: cancel any pending command.
            onBackgrounded()
            onDismiss()
        },
        sheetState = sheetState,
        modifier = modifier,
    ) {
        VoiceControlSheetContent(
            state = state,
            doorState = doorState,
            armedWindowMs = armedWindowMs,
            onMicTap = onMicTap,
            onSetDoorState = onSetDoorState,
            onSetArmedWindowMs = onSetArmedWindowMs,
            onCopy = onCopy,
        )
    }
}

/**
 * Sheet content extracted so previews can render it directly (the
 * ModalBottomSheet show animation doesn't run under previews) and so
 * the activity-result plumbing stays in the wrapper.
 */
@Composable
fun VoiceControlSheetContent(
    state: VoiceCommandState,
    doorState: VoiceDoorState,
    armedWindowMs: Long,
    onMicTap: () -> Unit,
    onSetDoorState: (VoiceDoorState) -> Unit = {},
    onSetArmedWindowMs: (Long) -> Unit = {},
    onCopy: (label: String, value: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    // One shared progress drives the ring AND the countdown text so the
    // two can never disagree. A fresh Animatable per Armed instance;
    // cancel-and-relisten produces a new instance and restarts it.
    val armed = state as? VoiceCommandState.Armed
    val armedProgress = remember(armed) { Animatable(0f) }
    LaunchedEffect(armed) {
        if (armed != null) {
            armedProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = armed.windowMs.toInt(),
                    easing = LinearEasing,
                ),
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.voice_control_sheet_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.voice_control_disclaimer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SimulatedDoorSelector(
            doorState = doorState,
            onSetDoorState = onSetDoorState,
        )
        VoiceCommandButton(
            state = state,
            armedProgress = { armedProgress.value },
            onMicTap = onMicTap,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        VoiceCommandStatus(
            state = state,
            armedSecondsLeft = armed?.let {
                VoiceControlHelpers.secondsLeft(it.windowMs, armedProgress.value)
            },
            onCopy = onCopy,
        )
        CancelWindowStepper(
            armedWindowMs = armedWindowMs,
            onSetArmedWindowMs = onSetArmedWindowMs,
        )
    }
}

// ADR-009: non-Composable helpers live in a named object.
private object VoiceControlHelpers {
    fun secondsLeft(
        windowMs: Long,
        progress: Float,
    ): Int = ceil((1f - progress) * windowMs / 1000f).toInt().coerceAtLeast(1)

    // Door-motion metaphor: up = opening, down = closing.
    fun directionIcon(intent: VoiceIntent): ImageVector =
        if (intent == VoiceIntent.CLOSE) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward
}

@Composable
private fun SimulatedDoorSelector(
    doorState: VoiceDoorState,
    onSetDoorState: (VoiceDoorState) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.voice_control_simulated_door_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val options = listOf(
            VoiceDoorState.CLOSED to stringResource(R.string.voice_door_closed),
            VoiceDoorState.OPEN to stringResource(R.string.voice_door_open),
            VoiceDoorState.MOVING to stringResource(R.string.voice_door_moving),
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (option, label) ->
                SegmentedButton(
                    selected = doorState == option,
                    onClick = { onSetDoorState(option) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = options.size,
                    ),
                ) {
                    Text(text = label)
                }
            }
        }
    }
}

@Composable
private fun VoiceCommandButton(
    state: VoiceCommandState,
    armedProgress: () -> Float,
    onMicTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(104.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            // The "completing circle": fills over the cancel window.
            is VoiceCommandState.Armed -> CircularProgressIndicator(
                progress = armedProgress,
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 4.dp,
            )
            is VoiceCommandState.Sending -> CircularProgressIndicator(
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 4.dp,
            )
            else -> Unit
        }
        FilledTonalIconButton(
            onClick = onMicTap,
            enabled = state !is VoiceCommandState.Sending,
            modifier = Modifier.size(80.dp),
        ) {
            Icon(
                imageVector = state.micIcon(),
                contentDescription = state.micContentDescription(),
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
private fun VoiceCommandStatus(
    state: VoiceCommandState,
    armedSecondsLeft: Int?,
    onCopy: (label: String, value: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            VoiceCommandState.Ready -> StatusText(
                text = stringResource(R.string.voice_control_ready_hint),
            )
            is VoiceCommandState.Listening -> StatusText(
                text = stringResource(R.string.voice_control_listening),
            )
            is VoiceCommandState.Armed -> {
                StatusText(
                    text = stringResource(R.string.voice_control_transcript_quote, state.transcript),
                )
                Text(
                    text = stringResource(
                        if (state.intent == VoiceIntent.CLOSE) {
                            R.string.voice_control_armed_closing
                        } else {
                            R.string.voice_control_armed_opening
                        },
                        armedSecondsLeft ?: VoiceControlHelpers.secondsLeft(state.windowMs, 0f),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
            is VoiceCommandState.Sending -> StatusText(
                text = stringResource(R.string.voice_control_sending),
            )
            is VoiceCommandState.Sent -> Text(
                text = stringResource(R.string.voice_control_sent),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            is VoiceCommandState.Failed -> Text(
                text = stringResource(R.string.voice_control_failed),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            is VoiceCommandState.Ignored -> IgnoredStatus(
                state = state,
                onCopy = onCopy,
            )
        }
    }
}

@Composable
private fun StatusText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

/**
 * Transient explanation for a capture that didn't commit. Tap to copy
 * the structured verdict (same format as the transcription playground)
 * so misses can be pasted into eval discussions.
 */
@Composable
private fun IgnoredStatus(
    state: VoiceCommandState.Ignored,
    onCopy: (label: String, value: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val verdictLabel = stringResource(R.string.voice_copy_label_verdict)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { onCopy(verdictLabel, state.clipboardSummary()) },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = state.reason.displayText(),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
            )
            state.transcript?.let {
                Text(
                    text = stringResource(R.string.voice_control_transcript_quote, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = stringResource(R.string.voice_control_copy_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CancelWindowStepper(
    armedWindowMs: Long,
    onSetArmedWindowMs: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                R.string.voice_control_window_label,
                (armedWindowMs / 1000L).toInt(),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(
            onClick = { onSetArmedWindowMs(armedWindowMs - ARMED_WINDOW_STEP_MS) },
            enabled = armedWindowMs > VoiceCommandController.MIN_ARMED_WINDOW_MS,
        ) {
            Icon(
                imageVector = Icons.Outlined.Remove,
                contentDescription = stringResource(R.string.voice_control_window_decrease),
            )
        }
        IconButton(
            onClick = { onSetArmedWindowMs(armedWindowMs + ARMED_WINDOW_STEP_MS) },
            enabled = armedWindowMs < VoiceCommandController.MAX_ARMED_WINDOW_MS,
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = stringResource(R.string.voice_control_window_increase),
            )
        }
    }
}

private fun VoiceCommandState.micIcon(): ImageVector =
    when (this) {
        VoiceCommandState.Ready, is VoiceCommandState.Listening, is VoiceCommandState.Ignored ->
            Icons.Outlined.Mic
        is VoiceCommandState.Armed -> VoiceControlHelpers.directionIcon(intent)
        is VoiceCommandState.Sending -> VoiceControlHelpers.directionIcon(intent)
        is VoiceCommandState.Sent -> Icons.Outlined.Check
        is VoiceCommandState.Failed -> Icons.Outlined.ErrorOutline
    }

@Composable
private fun VoiceCommandState.micContentDescription(): String =
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
private fun VoiceCommandIgnoreReason.displayText(): String =
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
        VoiceCommandIgnoreReason.DOOR_STATE_UNKNOWN ->
            stringResource(R.string.voice_control_ignored_state_unknown)
        VoiceCommandIgnoreReason.DOOR_STATE_CHANGED ->
            stringResource(R.string.voice_control_ignored_state_changed)
    }

// `private` so `checkPreviewCoverage` exempts them (same rationale as
// NavRailBottomSheet and VoiceInputBottomSheet: developer-gated sheet
// verified on a real device; these are Android Studio references only).
@Preview
@Composable
private fun VoiceControlSheetContentReadyPreview() {
    Surface {
        VoiceControlSheetContent(
            state = VoiceCommandState.Ready,
            doorState = VoiceDoorState.CLOSED,
            armedWindowMs = VoiceCommandController.DEFAULT_ARMED_WINDOW_MS,
            onMicTap = {},
        )
    }
}

@Preview
@Composable
private fun VoiceControlSheetContentArmedPreview() {
    Surface {
        VoiceControlSheetContent(
            state = VoiceCommandState.Armed(
                intent = VoiceIntent.OPEN,
                transcript = "open the garage door",
                windowMs = VoiceCommandController.DEFAULT_ARMED_WINDOW_MS,
            ),
            doorState = VoiceDoorState.CLOSED,
            armedWindowMs = VoiceCommandController.DEFAULT_ARMED_WINDOW_MS,
            onMicTap = {},
        )
    }
}

@Preview
@Composable
private fun VoiceControlSheetContentIgnoredPreview() {
    Surface {
        VoiceControlSheetContent(
            state = VoiceCommandState.Ignored(
                reason = VoiceCommandIgnoreReason.NOT_CONFIDENT,
                transcript = "can you open the garage door",
                classification = VoiceIntentClassification(
                    intent = VoiceIntent.OPEN,
                    confidence = VoiceIntentConfidence.MEDIUM,
                ),
                engineName = "Rules v3",
            ),
            doorState = VoiceDoorState.CLOSED,
            armedWindowMs = VoiceCommandController.DEFAULT_ARMED_WINDOW_MS,
            onMicTap = {},
        )
    }
}

@Preview
@Composable
private fun VoiceControlSheetContentSentPreview() {
    Surface {
        VoiceControlSheetContent(
            state = VoiceCommandState.Sent(intent = VoiceIntent.OPEN),
            doorState = VoiceDoorState.MOVING,
            armedWindowMs = VoiceCommandController.DEFAULT_ARMED_WINDOW_MS,
            onMicTap = {},
        )
    }
}
