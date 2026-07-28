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

package com.chriscartland.garage.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.chriscartland.garage.R
import com.chriscartland.garage.domain.model.VoiceIntent
import com.chriscartland.garage.ui.theme.PreviewComponentSurface
import com.chriscartland.garage.ui.voice.VoiceControlCard
import com.chriscartland.garage.ui.voice.VoiceRecognizerEffects
import com.chriscartland.garage.usecase.VoiceCommandIgnoreReason
import com.chriscartland.garage.usecase.VoiceCommandState

/**
 * Home-tab voice control section (developer-flag-gated, LIVE — the gate
 * reads the real door state and a committed command presses the REAL
 * remote garage button). The mic button is the whole interface: tap to
 * speak, tap during the countdown ring to cancel and immediately
 * re-listen.
 *
 * The card itself and the recognizer plumbing are shared with the
 * simulated rehearsal in Settings → Developer → Simulated voice (see
 * [VoiceControlCard]); this file only supplies the Home section
 * chrome and the live wiring.
 */
@Composable
fun HomeVoiceControlSection(
    state: VoiceCommandState,
    onMicTap: () -> Unit,
    onTranscript: (String?) -> Unit,
    onCaptureUnavailable: () -> Unit,
    onBackgrounded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    VoiceRecognizerEffects(
        state = state,
        onTranscript = onTranscript,
        onCaptureUnavailable = onCaptureUnavailable,
        onBackgrounded = onBackgrounded,
    )
    HomeVoiceControlSectionBody(
        state = state,
        onMicTap = onMicTap,
        modifier = modifier,
    )
}

/**
 * Pure layout (no activity-result plumbing) so previews can render
 * every state directly.
 */
@Composable
fun HomeVoiceControlSectionBody(
    state: VoiceCommandState,
    onMicTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeSection(
        label = stringResource(R.string.home_section_voice),
        modifier = modifier,
    ) {
        VoiceControlCard(state = state, onMicTap = onMicTap)
    }
}

// `private` so `checkPreviewCoverage` exempts them (developer-flag-gated
// surface verified on a real device; Android Studio references only —
// same rationale as the simulated voice sheet).
@Preview
@Composable
private fun HomeVoiceControlReadyPreview() {
    PreviewComponentSurface {
        HomeVoiceControlSectionBody(
            state = VoiceCommandState.Ready,
            onMicTap = {},
        )
    }
}

@Preview
@Composable
private fun HomeVoiceControlArmedPreview() {
    PreviewComponentSurface {
        HomeVoiceControlSectionBody(
            state = VoiceCommandState.Armed(
                intent = VoiceIntent.OPEN,
                transcript = "open the garage door",
                windowMs = 3_000L,
            ),
            onMicTap = {},
        )
    }
}

@Preview
@Composable
private fun HomeVoiceControlIgnoredPreview() {
    PreviewComponentSurface {
        HomeVoiceControlSectionBody(
            state = VoiceCommandState.Ignored(
                reason = VoiceCommandIgnoreReason.DOOR_ALREADY_OPEN,
                transcript = "open the garage door",
                classification = null,
                engineName = "Rules v3",
            ),
            onMicTap = {},
        )
    }
}

@Preview
@Composable
private fun HomeVoiceControlSentPreview() {
    PreviewComponentSurface {
        HomeVoiceControlSectionBody(
            state = VoiceCommandState.Sent(intent = VoiceIntent.OPEN),
            onMicTap = {},
        )
    }
}
