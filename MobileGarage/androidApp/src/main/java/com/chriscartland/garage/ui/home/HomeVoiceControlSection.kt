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

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chriscartland.garage.R
import com.chriscartland.garage.domain.model.VoiceIntent
import com.chriscartland.garage.ui.VoiceCommandUi
import com.chriscartland.garage.ui.displayText
import com.chriscartland.garage.ui.micContentDescription
import com.chriscartland.garage.ui.micIcon
import com.chriscartland.garage.ui.theme.CardPadding
import com.chriscartland.garage.ui.theme.PreviewComponentSurface
import com.chriscartland.garage.usecase.VoiceCommandIgnoreReason
import com.chriscartland.garage.usecase.VoiceCommandState

/**
 * Home-tab voice control section (developer-flag-gated, SHADOW MODE —
 * the gate reads the real door state, the press is a no-op; a
 * "Simulated" pill in the header keeps that honest). The mic button is
 * the whole interface: tap to speak, tap during the countdown ring to
 * cancel and immediately re-listen. Layout is a compact card row —
 * mic + ring on the left, a stable two-line status column on the right
 * — matching the Home section language (see [HomeSection]).
 *
 * Owns the recognizer plumbing: launch is driven by the
 * [VoiceCommandState.Listening] state (not the tap), so "tap in Ready"
 * and "tap cancels Armed" share one path; lifecycle ON_STOP cancels a
 * pending command (nothing commits off-screen).
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
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) onBackgrounded()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
        trailing = { SimulatedPill() },
        modifier = modifier,
    ) {
        HomeVoiceCardBody(state = state, onMicTap = onMicTap)
    }
}

/** Small header pill making shadow mode unmistakable. */
@Composable
private fun SimulatedPill(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.home_voice_simulated_pill),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun HomeVoiceCardBody(
    state: VoiceCommandState,
    onMicTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // One shared progress drives the ring AND the countdown text so the
    // two can never disagree; a fresh Animatable per Armed instance.
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(CardPadding.Standard),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(64.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                is VoiceCommandState.Armed -> CircularProgressIndicator(
                    progress = { armedProgress.value },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 3.dp,
                )
                is VoiceCommandState.Sending -> CircularProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 3.dp,
                )
                else -> Unit
            }
            FilledTonalIconButton(
                onClick = onMicTap,
                enabled = state !is VoiceCommandState.Sending,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    imageVector = state.micIcon(),
                    contentDescription = state.micContentDescription(),
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        HomeVoiceStatusColumn(
            state = state,
            armedSecondsLeft = armed?.let {
                VoiceCommandUi.secondsLeft(it.windowMs, armedProgress.value)
            },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Stable two-line status: primary line (what is happening) + secondary
 * line (transcript or guidance). Every state fills both lines so the
 * card height never jumps mid-flow.
 */
@Composable
private fun HomeVoiceStatusColumn(
    state: VoiceCommandState,
    armedSecondsLeft: Int?,
    modifier: Modifier = Modifier,
) {
    val hint = stringResource(R.string.home_voice_hint)
    val (primary, secondary) = when (state) {
        VoiceCommandState.Ready ->
            stringResource(R.string.home_voice_ready_title) to hint
        is VoiceCommandState.Listening ->
            stringResource(R.string.voice_control_listening) to hint
        is VoiceCommandState.Armed ->
            stringResource(
                if (state.intent == VoiceIntent.CLOSE) {
                    R.string.voice_control_armed_closing
                } else {
                    R.string.voice_control_armed_opening
                },
                armedSecondsLeft ?: VoiceCommandUi.secondsLeft(state.windowMs, 0f),
            ) to stringResource(R.string.voice_control_transcript_quote, state.transcript)
        is VoiceCommandState.Sending ->
            stringResource(R.string.voice_control_sending) to
                stringResource(R.string.home_voice_shadow_subtitle)
        is VoiceCommandState.Sent ->
            stringResource(R.string.voice_control_sent) to
                stringResource(R.string.home_voice_shadow_subtitle)
        is VoiceCommandState.Failed ->
            stringResource(R.string.voice_control_failed) to
                stringResource(R.string.home_voice_failed_subtitle)
        is VoiceCommandState.Ignored ->
            state.reason.displayText() to (
                state.transcript?.let {
                    stringResource(R.string.voice_control_transcript_quote, it)
                } ?: hint
            )
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = primary,
            style = MaterialTheme.typography.titleMedium,
            color = if (state is VoiceCommandState.Failed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Text(
            text = secondary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// `private` so `checkPreviewCoverage` exempts them (developer-flag-gated
// surface verified on a real device; Android Studio references only —
// same rationale as the voice playground sheet).
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
