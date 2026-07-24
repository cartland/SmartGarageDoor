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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chriscartland.garage.R
import com.chriscartland.garage.viewmodel.VoiceExperimentState

/**
 * Experimental voice-input playground (Settings → Developer → Voice
 * input). One tap on Speak launches the system speech prompt
 * (`RecognizerIntent` — the launch lives in `ProfileContent`, which
 * owns the activity-result plumbing); the transcript renders here and
 * nowhere else. Deliberately NOT wired to any door action, and the
 * state is ViewModel-memory only — leaving the screen discards it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceInputBottomSheet(
    state: VoiceExperimentState,
    onSpeakTap: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        VoiceInputSheetContent(
            state = state,
            onSpeakTap = onSpeakTap,
        )
    }
}

/**
 * Sheet content extracted so previews can render it directly (the
 * ModalBottomSheet show animation doesn't run under previews).
 */
@Composable
fun VoiceInputSheetContent(
    state: VoiceExperimentState,
    onSpeakTap: () -> Unit,
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
            text = stringResource(R.string.voice_experiment_sheet_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.voice_experiment_disclaimer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton(
            onClick = onSpeakTap,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Icon(
                imageVector = Icons.Outlined.Mic,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.voice_experiment_speak_button),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        when (state) {
            VoiceExperimentState.Idle -> Text(
                text = stringResource(R.string.voice_experiment_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is VoiceExperimentState.Transcript -> Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = state.text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp),
                )
            }
            VoiceExperimentState.NoSpeech -> Text(
                text = stringResource(R.string.voice_experiment_no_speech),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            VoiceExperimentState.Unavailable -> Text(
                text = stringResource(R.string.voice_experiment_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

// `private` so `checkPreviewCoverage` exempts them (same rationale as
// NavRailBottomSheet: developer-gated sheet verified on a real device;
// these are Android Studio references only).
@Preview
@Composable
private fun VoiceInputSheetContentIdlePreview() {
    Surface {
        VoiceInputSheetContent(
            state = VoiceExperimentState.Idle,
            onSpeakTap = {},
        )
    }
}

@Preview
@Composable
private fun VoiceInputSheetContentTranscriptPreview() {
    Surface {
        VoiceInputSheetContent(
            state = VoiceExperimentState.Transcript("open the garage door"),
            onSpeakTap = {},
        )
    }
}

@Preview
@Composable
private fun VoiceInputSheetContentNoSpeechPreview() {
    Surface {
        VoiceInputSheetContent(
            state = VoiceExperimentState.NoSpeech,
            onSpeakTap = {},
        )
    }
}
