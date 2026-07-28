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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chriscartland.garage.R
import com.chriscartland.garage.domain.model.VoiceIntent
import com.chriscartland.garage.domain.model.VoiceIntentClassification
import com.chriscartland.garage.domain.model.VoiceIntentConfidence
import com.chriscartland.garage.ui.voice.VoiceControlCard
import com.chriscartland.garage.ui.voice.VoiceRecognizerEffects
import com.chriscartland.garage.usecase.VoiceCommandIgnoreReason
import com.chriscartland.garage.usecase.VoiceCommandState
import com.chriscartland.garage.usecase.VoiceDoorState
import com.chriscartland.garage.viewmodel.VoiceVerdict

/**
 * Settings → Developer → Simulated voice: the Home tab's voice control,
 * rehearsed against a pretend door.
 *
 * It is the same feature, not an imitation of it — the same
 * [com.chriscartland.garage.usecase.VoiceCommandController], the same
 * classifier, the same two-stage door gate, and literally the same
 * [VoiceControlCard] composable that Home renders. Exactly one thing
 * differs: the ViewModel hands the controller a
 * [com.chriscartland.garage.usecase.SimulatedVoiceCommandEnvironment]
 * instead of the remote-button one, so a committed command presses a
 * pretend button and moves a pretend door. The real garage door cannot
 * be reached from this sheet — `DefaultProfileViewModel` has no access
 * to `PushRemoteButtonUseCase` at all, which
 * `ProfileViewModelTest.theSimulatedVoiceSurfaceCannotReachTheRealButton`
 * pins.
 *
 * The pretend door reacts to commands (open it, and asking again is
 * refused as already-open; speak mid-transit and the gate refuses that
 * too), which is how the refusal paths are exercised now that the
 * playground's door-placement control is gone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulatedVoiceBottomSheet(
    state: VoiceCommandState,
    doorState: VoiceDoorState,
    lastVerdict: VoiceVerdict?,
    onMicTap: () -> Unit,
    onTranscript: (String?) -> Unit,
    onCaptureUnavailable: () -> Unit,
    onBackgrounded: () -> Unit,
    onCopy: (label: String, value: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    VoiceRecognizerEffects(
        state = state,
        onTranscript = onTranscript,
        onCaptureUnavailable = onCaptureUnavailable,
        onBackgrounded = onBackgrounded,
    )
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = {
            // Dismiss = leaving the rehearsal: cancel any pending command
            // so it cannot complete behind a closed sheet.
            onBackgrounded()
            onDismiss()
        },
        sheetState = sheetState,
        modifier = modifier,
    ) {
        SimulatedVoiceSheetContent(
            state = state,
            doorState = doorState,
            lastVerdict = lastVerdict,
            onMicTap = onMicTap,
            onCopy = onCopy,
        )
    }
}

/**
 * Sheet content extracted so previews can render it directly (the
 * ModalBottomSheet show animation doesn't run under previews) and so
 * the recognizer plumbing stays in the wrapper.
 */
@Composable
fun SimulatedVoiceSheetContent(
    state: VoiceCommandState,
    doorState: VoiceDoorState,
    lastVerdict: VoiceVerdict?,
    onMicTap: () -> Unit,
    onCopy: (label: String, value: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.simulated_voice_sheet_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.simulated_voice_disclaimer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Read-only, not a control: the gate's refusals are unreadable
        // without seeing what it is judging against, but placing the
        // door by hand is not something the Home feature can do.
        Text(
            text = stringResource(
                R.string.simulated_voice_door_label,
                stringResource(doorState.labelRes()),
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Same card treatment as the Home section it rehearses.
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large),
        ) {
            VoiceControlCard(state = state, onMicTap = onMicTap)
        }
        // Developer tool, deliberately BELOW the rehearsal and outside
        // the card: it is not part of the feature being rehearsed, and
        // it must never appear on the live Home card.
        if (lastVerdict != null) {
            VerdictPanel(verdict = lastVerdict, onCopy = onCopy)
        }
    }
}

/**
 * The classifier's verdict on the last capture, tap-to-copy for the
 * eval corpus (`MobileGarage/docs/VOICE_COMMANDS.md`).
 *
 * Reads from the latched [VoiceVerdict] rather than the live command
 * state so it outlives the ~4s refusal flash — deciding a transcript is
 * worth keeping takes longer than the flash lasts.
 */
@Composable
private fun VerdictPanel(
    verdict: VoiceVerdict,
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
            .clickable { onCopy(verdictLabel, verdict.clipboardSummary()) },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.voice_control_transcript_quote,
                    verdict.transcript,
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(
                    R.string.voice_verdict_classification,
                    verdict.classification.intent.displayName(),
                    verdict.classification.confidence.displayName(),
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.voice_verdict_engine, verdict.engineName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.voice_verdict_copy_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// User-visible names for the classification result. UI-local mapping —
// the domain enums stay display-free.
@Composable
private fun VoiceIntent.displayName(): String =
    when (this) {
        VoiceIntent.OPEN -> stringResource(R.string.voice_intent_open)
        VoiceIntent.CLOSE -> stringResource(R.string.voice_intent_close)
        VoiceIntent.UNKNOWN -> stringResource(R.string.voice_intent_unknown)
    }

@Composable
private fun VoiceIntentConfidence.displayName(): String =
    when (this) {
        VoiceIntentConfidence.HIGH -> stringResource(R.string.voice_confidence_high)
        VoiceIntentConfidence.MEDIUM -> stringResource(R.string.voice_confidence_medium)
        VoiceIntentConfidence.NONE -> stringResource(R.string.voice_confidence_none)
    }

private fun VoiceDoorState.labelRes(): Int =
    when (this) {
        VoiceDoorState.CLOSED -> R.string.voice_door_closed
        VoiceDoorState.OPEN -> R.string.voice_door_open
        VoiceDoorState.MOVING -> R.string.voice_door_moving
        VoiceDoorState.UNKNOWN -> R.string.voice_door_unknown
    }

// `private` so `checkPreviewCoverage` exempts them (same rationale as
// NavRailBottomSheet and the Home voice card: a developer-gated surface
// verified on a real device; these are Android Studio references only).
@Preview
@Composable
private fun SimulatedVoiceSheetContentReadyPreview() {
    Surface {
        SimulatedVoiceSheetContent(
            state = VoiceCommandState.Ready,
            doorState = VoiceDoorState.CLOSED,
            lastVerdict = null,
            onMicTap = {},
        )
    }
}

@Preview
@Composable
private fun SimulatedVoiceSheetContentArmedPreview() {
    Surface {
        SimulatedVoiceSheetContent(
            state = VoiceCommandState.Armed(
                intent = VoiceIntent.OPEN,
                transcript = "open the garage door",
                windowMs = 3_000L,
            ),
            doorState = VoiceDoorState.CLOSED,
            lastVerdict = VoiceVerdict(
                transcript = "open the garage door",
                classification = VoiceIntentClassification(
                    intent = VoiceIntent.OPEN,
                    confidence = VoiceIntentConfidence.HIGH,
                ),
                engineName = "Rules v3",
                ignoreReason = null,
            ),
            onMicTap = {},
        )
    }
}

/**
 * The case the verdict panel exists for: a capture that was refused,
 * still readable and copyable after the refusal flash has passed
 * (hence Ready above a non-null verdict).
 */
@Preview
@Composable
private fun SimulatedVoiceSheetContentVerdictAfterRefusalPreview() {
    Surface {
        SimulatedVoiceSheetContent(
            state = VoiceCommandState.Ready,
            doorState = VoiceDoorState.CLOSED,
            lastVerdict = VoiceVerdict(
                transcript = "can you open the garage door",
                classification = VoiceIntentClassification(
                    intent = VoiceIntent.OPEN,
                    confidence = VoiceIntentConfidence.MEDIUM,
                ),
                engineName = "Rules v3",
                ignoreReason = VoiceCommandIgnoreReason.NOT_CONFIDENT,
            ),
            onMicTap = {},
        )
    }
}

@Preview
@Composable
private fun SimulatedVoiceSheetContentIgnoredPreview() {
    Surface {
        SimulatedVoiceSheetContent(
            state = VoiceCommandState.Ignored(
                reason = VoiceCommandIgnoreReason.DOOR_ALREADY_OPEN,
                transcript = "open the garage door",
                classification = null,
                engineName = "Rules v3",
            ),
            doorState = VoiceDoorState.OPEN,
            lastVerdict = VoiceVerdict(
                transcript = "open the garage door",
                classification = VoiceIntentClassification(
                    intent = VoiceIntent.OPEN,
                    confidence = VoiceIntentConfidence.HIGH,
                ),
                engineName = "Rules v3",
                ignoreReason = VoiceCommandIgnoreReason.DOOR_ALREADY_OPEN,
            ),
            onMicTap = {},
        )
    }
}

@Preview
@Composable
private fun SimulatedVoiceSheetContentSentPreview() {
    Surface {
        SimulatedVoiceSheetContent(
            state = VoiceCommandState.Sent(intent = VoiceIntent.OPEN),
            doorState = VoiceDoorState.MOVING,
            lastVerdict = null,
            onMicTap = {},
        )
    }
}
