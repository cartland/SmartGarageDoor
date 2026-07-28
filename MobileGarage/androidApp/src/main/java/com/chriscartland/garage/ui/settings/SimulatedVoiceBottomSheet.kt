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
import com.chriscartland.garage.ui.voice.VoiceControlCard
import com.chriscartland.garage.ui.voice.VoiceRecognizerEffects
import com.chriscartland.garage.usecase.VoiceCommandIgnoreReason
import com.chriscartland.garage.usecase.VoiceCommandState
import com.chriscartland.garage.usecase.VoiceDoorState

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
    onMicTap: () -> Unit,
    onTranscript: (String?) -> Unit,
    onCaptureUnavailable: () -> Unit,
    onBackgrounded: () -> Unit,
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
            onMicTap = onMicTap,
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
    onMicTap: () -> Unit,
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
    }
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
            onMicTap = {},
        )
    }
}
