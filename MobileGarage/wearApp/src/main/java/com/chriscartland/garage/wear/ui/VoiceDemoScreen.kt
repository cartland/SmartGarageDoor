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

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import com.chriscartland.garage.domain.model.VoiceIntent
import com.chriscartland.garage.usecase.VoiceCommandIgnoreReason
import com.chriscartland.garage.usecase.VoiceCommandState
import com.chriscartland.garage.usecase.VoiceDoorState
import com.chriscartland.garage.wear.R

/**
 * Stateful voice demo screen: owns the speech-recognizer plumbing and the
 * lifecycle hook, and delegates rendering to [VoiceDemoContent].
 *
 * Capture prefers an in-app [WearSpeechCapture] so that speaking is **one
 * tap**. `RecognizerIntent` on Wear resolves to Gboard's text-ENTRY activity,
 * which transcribes and then asks you to confirm before returning — fine for
 * filling a text field, needless friction for a two-word command. It remains
 * the fallback for watches with no `RecognitionService` and for a user who
 * declines the microphone, so the demo always works; it is just slower.
 *
 * Launch is driven by the controller's `Listening` state rather than by the
 * tap, so "tap while ready" and "tap to cancel, which re-listens" share one
 * path regardless of which capture backend is in play.
 */
@Composable
fun VoiceDemoScreen(
    viewModel: WearVoiceViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val demoDoorState by viewModel.demoDoorState.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.partialTranscript.collectAsStateWithLifecycle()

    val view = LocalView.current
    LaunchedEffect(view, viewModel) {
        viewModel.hapticCues.collect { cue ->
            view.performHapticFeedback(WearHaptics.constantFor(cue))
        }
    }

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
        viewModel.onTranscript(transcript)
    }
    val prompt = stringResource(R.string.voice_demo_prompt)

    // Preferred path: capture in-app, so speaking is ONE tap. The system
    // fallback below exists because RecognizerIntent on Wear resolves to
    // Gboard's text-ENTRY activity, which makes you confirm the transcript
    // before it returns (WearSpeechCapture has the full why).
    val inAppCapture = rememberWearSpeechCapture(
        onTranscript = viewModel::onTranscript,
        onPartial = viewModel::onPartialTranscript,
    )
    val context = LocalContext.current
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val launchSystemCapture = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            ).putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            // Only the top-1 result is ever parsed: acting on alternative N
            // is acting on something the recognizer thinks you probably
            // did not say.
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        try {
            voiceLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            // Watches with no speech input at all. Caught rather than
            // pre-checked with resolveActivity, which Android 11+ package
            // visibility would answer with a misleading null.
            viewModel.onCaptureUnavailable()
        }
    }

    // Denying the microphone degrades to the system flow rather than breaking
    // the demo: more taps, but it still works.
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        micGranted = granted
        if (granted) inAppCapture?.start() else launchSystemCapture()
    }

    val listeningAttempt = (state as? VoiceCommandState.Listening)?.attempt
    LaunchedEffect(listeningAttempt) {
        if (listeningAttempt == null) return@LaunchedEffect
        when {
            inAppCapture == null -> launchSystemCapture()
            micGranted -> inAppCapture.start()
            else -> micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.onBackgrounded()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    VoiceDemoContent(
        state = state,
        demoDoorState = demoDoorState,
        partialTranscript = partialTranscript,
        onMicTap = viewModel::onMicTap,
        modifier = modifier,
    )
}

/**
 * Stateless voice demo layout (previewable).
 *
 * Everything lives in one vertically-centred column constrained to
 * [CONTENT_WIDTH_FRACTION] of the screen, because that is where a round
 * screen's chord is widest — the same lesson the hero screen's bottom text
 * learned the hard way (an unconstrained line near an edge is clipped at both
 * ends rather than wrapping).
 *
 * Three independent signals say "this is not real", because one is easy to
 * miss on a glance:
 *  1. A persistent "Simulated" marker at the top of the column.
 *  2. Conditional wording throughout — "Would open the door", never "Opening"
 *     — and a terminal state that says outright that nothing was sent.
 *  3. The door line is labelled "Demo door", so the thing visibly reacting is
 *     never mistaken for the garage.
 */
@Composable
fun VoiceDemoContent(
    state: VoiceCommandState,
    demoDoorState: VoiceDoorState,
    partialTranscript: String?,
    onMicTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // One Animatable per Armed instance drives the countdown ring. A fresh
    // instance per state means a cancel-and-re-arm restarts from zero.
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

    ScreenScaffold(modifier = modifier) {
        Box(
            // The WHOLE screen is the button, not just the mic. A 52dp target
            // is a poor thing to have to hit to stop something that is already
            // counting down, and the controller's model is "a tap always means
            // listen to me now" — so the tap area may as well be everything.
            //
            // No ripple (a full-screen one reads as a glitch); the state change
            // is the feedback. Disabled while Sending because a press cannot be
            // unsent. The mic button is a child, so its own click wins where
            // they overlap, and Compose's gesture disambiguation still lets a
            // horizontal drag reach SwipeToDismissBox for swipe-to-go-back.
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = state !is VoiceCommandState.Sending,
                    onClickLabel = stringResource(R.string.cd_voice_demo_screen),
                    onClick = onMicTap,
                ),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(CONTENT_WIDTH_FRACTION),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ITEM_SPACING_DP.dp),
            ) {
                Text(
                    text = stringResource(R.string.voice_demo_simulated),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    textAlign = TextAlign.Center,
                )
                FilledTonalIconButton(
                    onClick = onMicTap,
                    // A press cannot be unsent, so the real feature disables the
                    // button while sending. Mirrored here so the demo teaches
                    // the same affordance.
                    enabled = state !is VoiceCommandState.Sending,
                    modifier = Modifier.size(MIC_BUTTON_SIZE_DP.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mic_24),
                        contentDescription = stringResource(R.string.cd_voice_demo_mic),
                        modifier = Modifier.size(MIC_ICON_SIZE_DP.dp),
                    )
                }
                Text(
                    text = stringResource(VoiceDemoMappers.primaryLine(state)),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = VoiceDemoMappers.secondaryLine(state, partialTranscript),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    minLines = 1,
                )
                Text(
                    text = stringResource(
                        R.string.voice_demo_door,
                        stringResource(VoiceDemoMappers.demoDoorLabel(demoDoorState)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    textAlign = TextAlign.Center,
                )
            }
            // Countdown ring at the bezel, matching the hero screen's language
            // for "something is counting toward committing". Drawn last so it
            // is on top; it takes no input so it can never block the mic.
            CountdownRing(
                progress = if (armed != null) armedProgress.value else 0f,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(RING_PADDING_DP.dp),
            )
        }
    }
}

/**
 * The cancel-window indicator, drawn at the bounds it is given so it is
 * concentric with the bezel. Deliberately a different colour from the hero
 * screen's hold ring: that one is counting toward a real garage press, this
 * one is not.
 */
@Composable
private fun CountdownRing(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val ringColor = MaterialTheme.colorScheme.tertiary
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier) {
        if (progress <= 0f) return@Canvas
        val stroke = RING_STROKE_DP.dp.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(
            color = trackColor,
            startAngle = ARC_START_ANGLE,
            sweepAngle = FULL_SWEEP,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            alpha = TRACK_ALPHA,
            style = Stroke(width = stroke),
        )
        drawArc(
            color = ringColor,
            startAngle = ARC_START_ANGLE,
            sweepAngle = FULL_SWEEP * progress,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/** String mappers for the voice demo. */
internal object VoiceDemoMappers {
    /**
     * The headline. Every wording that describes an action is conditional
     * ("Would open the door"), and the terminal state states plainly that
     * nothing was sent — the demo's whole job is to communicate the action it
     * would take without ever taking it.
     */
    @StringRes
    fun primaryLine(state: VoiceCommandState): Int =
        when (state) {
            VoiceCommandState.Ready -> R.string.voice_demo_ready
            is VoiceCommandState.Listening -> R.string.voice_demo_listening
            is VoiceCommandState.Armed ->
                if (state.intent == VoiceIntent.CLOSE) {
                    R.string.voice_demo_would_close
                } else {
                    R.string.voice_demo_would_open
                }
            is VoiceCommandState.Sending -> R.string.voice_demo_committing
            is VoiceCommandState.Sent -> R.string.voice_demo_committed
            is VoiceCommandState.Failed -> R.string.voice_demo_failed
            is VoiceCommandState.Ignored -> ignoredLine(state.reason)
        }

    /**
     * The supporting line. Armed offers the way out; refusals quote what was
     * actually heard, which is the single most useful thing to see when the
     * classifier says no.
     */
    @Composable
    fun secondaryLine(
        state: VoiceCommandState,
        partialTranscript: String?,
    ): String =
        when (state) {
            // Live text while the in-app recognizer is hearing it. The system
            // fallback never reports partials, so it keeps the static hint.
            is VoiceCommandState.Listening ->
                partialTranscript?.let { stringResource(R.string.voice_demo_transcript, it) }
                    ?: stringResource(R.string.voice_demo_hint)
            is VoiceCommandState.Armed -> stringResource(R.string.voice_demo_cancel_hint)
            is VoiceCommandState.Ignored ->
                state.transcript?.let { stringResource(R.string.voice_demo_transcript, it) }
                    ?: stringResource(R.string.voice_demo_hint)
            is VoiceCommandState.Sent -> stringResource(R.string.voice_demo_committed_hint)
            else -> stringResource(R.string.voice_demo_hint)
        }

    @StringRes
    fun ignoredLine(reason: VoiceCommandIgnoreReason): Int =
        when (reason) {
            VoiceCommandIgnoreReason.NO_SPEECH -> R.string.voice_demo_ignored_no_speech
            VoiceCommandIgnoreReason.RECOGNIZER_UNAVAILABLE -> R.string.voice_demo_ignored_unavailable
            VoiceCommandIgnoreReason.NOT_A_COMMAND -> R.string.voice_demo_ignored_not_a_command
            VoiceCommandIgnoreReason.NOT_CONFIDENT -> R.string.voice_demo_ignored_not_confident
            VoiceCommandIgnoreReason.DOOR_ALREADY_OPEN -> R.string.voice_demo_ignored_already_open
            VoiceCommandIgnoreReason.DOOR_ALREADY_CLOSED -> R.string.voice_demo_ignored_already_closed
            VoiceCommandIgnoreReason.DOOR_MOVING -> R.string.voice_demo_ignored_moving
            VoiceCommandIgnoreReason.DOOR_STATE_UNKNOWN -> R.string.voice_demo_ignored_state_unknown
            VoiceCommandIgnoreReason.DOOR_STATE_CHANGED -> R.string.voice_demo_ignored_state_changed
        }

    @StringRes
    fun demoDoorLabel(state: VoiceDoorState): Int =
        when (state) {
            VoiceDoorState.CLOSED -> R.string.door_state_closed
            VoiceDoorState.OPEN -> R.string.door_state_open
            VoiceDoorState.MOVING -> R.string.voice_demo_door_moving
            VoiceDoorState.UNKNOWN -> R.string.door_state_unknown
        }
}

/** At rest: what to say, and the demo door it will be judged against. */
@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
private fun VoiceDemoReadyPreview() {
    MaterialTheme {
        VoiceDemoContent(
            state = VoiceCommandState.Ready,
            demoDoorState = VoiceDoorState.CLOSED,
            partialTranscript = null,
            onMicTap = {},
        )
    }
}

/** Counting down: the action is named conditionally, and is still cancellable. */
@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
private fun VoiceDemoArmedPreview() {
    MaterialTheme {
        VoiceDemoContent(
            state = VoiceCommandState.Armed(
                intent = VoiceIntent.OPEN,
                transcript = "open the garage door",
                windowMs = WearVoiceViewModel.ARMED_WINDOW_MILLIS,
            ),
            demoDoorState = VoiceDoorState.CLOSED,
            partialTranscript = null,
            onMicTap = {},
        )
    }
}

/** The punchline: the window elapsed and nothing was sent. */
@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
private fun VoiceDemoSentPreview() {
    MaterialTheme {
        VoiceDemoContent(
            state = VoiceCommandState.Sent(intent = VoiceIntent.OPEN),
            demoDoorState = VoiceDoorState.MOVING,
            partialTranscript = null,
            onMicTap = {},
        )
    }
}

/** A gate refusal, quoting what was heard. */
@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
private fun VoiceDemoIgnoredPreview() {
    MaterialTheme {
        VoiceDemoContent(
            state = VoiceCommandState.Ignored(
                reason = VoiceCommandIgnoreReason.DOOR_ALREADY_OPEN,
                transcript = "open the garage door",
                classification = null,
                engineName = "Rules v3",
            ),
            demoDoorState = VoiceDoorState.OPEN,
            partialTranscript = null,
            onMicTap = {},
        )
    }
}

private const val CONTENT_WIDTH_FRACTION = 0.72f
private const val ITEM_SPACING_DP = 4
private const val MIC_BUTTON_SIZE_DP = 52
private const val MIC_ICON_SIZE_DP = 26
private const val RING_PADDING_DP = 2
private const val RING_STROKE_DP = 5
private const val ARC_START_ANGLE = -90f
private const val FULL_SWEEP = 360f
private const val TRACK_ALPHA = 0.25f
