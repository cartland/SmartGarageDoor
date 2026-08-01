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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
 * Stateful voice screen: owns the speech-recognizer plumbing and the lifecycle
 * hook, and delegates rendering to [VoiceContent].
 *
 * ONE screen serves both worlds. [WearLiveVoiceViewModel] presses the real
 * garage button; [WearSimulatedVoiceViewModel] presses a pretend one. Which is
 * in play is [mode], and it changes only the wording and the colour — see
 * [VoiceSurfaceMode] for the four independent ways the simulation says so.
 *
 * Capture prefers an in-app [WearSpeechCapture] so that speaking is **one
 * tap**. `RecognizerIntent` on Wear resolves to Gboard's text-ENTRY activity,
 * which transcribes and then asks you to confirm before returning — fine for
 * filling a text field, needless friction for a two-word command. It remains
 * the fallback for watches with no `RecognitionService` and for a user who
 * declines the microphone, so the demo always works; it is just slower.
 *
 * Launch is driven by the controller's `Listening` state rather than by the
 * tap, so every route into a capture shares one path regardless of which
 * backend is in play.
 *
 * Tap rule, one sentence: **a tap starts what is not running and stops what
 * is.** `Listening` and `Armed` are the running states, so a tap there cancels
 * back to `Ready`; anywhere else it begins a new capture. `Sending` is inert
 * because a press cannot be unsent. This is deliberately not the phone's
 * cancel-and-re-listen rule — see [WearVoiceViewModel.onCancel].
 *
 * **Opening the screen counts as that first tap**, so speaking really is one
 * gesture from the door and not two. The `Ready` state is still reachable and
 * still says "Tap to speak" — it is simply where a finished command lands
 * rather than where a new one starts.
 */
@Composable
fun VoiceScreen(
    viewModel: WearVoiceViewModel,
    mode: VoiceSurfaceMode,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val doorState by viewModel.doorState.collectAsStateWithLifecycle()
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
    val prompt = stringResource(R.string.voice_prompt)

    // Mic level lives in the UI, not the ViewModel: it is continuous
    // presentation data (~10 updates/second) with no decision hanging off it,
    // and routing it through a StateFlow would recompose the world for an
    // animation. The VM still owns everything meaningful — the state and the
    // transcript.
    var listeningLevel by remember { mutableFloatStateOf(0f) }

    // Preferred path: capture in-app, so speaking is ONE tap. The system
    // fallback below exists because RecognizerIntent on Wear resolves to
    // Gboard's text-ENTRY activity, which makes you confirm the transcript
    // before it returns (WearSpeechCapture has the full why).
    val inAppCapture = rememberWearSpeechCapture(
        onTranscript = viewModel::onTranscript,
        onPartial = viewModel::onPartialTranscript,
        onLevel = { listeningLevel = VoiceLevel.smooth(listeningLevel, it) },
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

    // Arriving here IS the tap. Reaching this screen takes a deliberate press
    // of the mic chip on the hero screen, and that press already said "I want
    // to speak" — so landing on a "Tap to speak" button asked for the same
    // intent a second time. On a wrist, where the whole point is to say two
    // words and put your arm down, a mandatory second tap was most of the
    // interaction.
    //
    // Deliberately unconditional rather than guarded by a "did we already do
    // this" latch: this composable exists only while the demo is the current
    // destination, so it runs exactly once per visit, and leaving disposes it
    // (see onScreenLeft below, which stops the recognizer). Re-entering SHOULD
    // listen again — that is what tapping the mic means.
    //
    // Only entry is automatic. Once a command completes the screen returns to
    // Ready and stays there, because a demo that re-opened the microphone every
    // time it finished would be a live mic nobody asked to keep open.
    LaunchedEffect(Unit) { viewModel.onMicTap() }

    val listeningAttempt = (state as? VoiceCommandState.Listening)?.attempt
    LaunchedEffect(listeningAttempt) {
        // Each attempt starts silent, so a previous utterance's level cannot
        // leak in and make the rings look alive before anyone has spoken.
        listeningLevel = 0f
        if (listeningAttempt == null) return@LaunchedEffect
        when {
            inAppCapture == null -> launchSystemCapture()
            micGranted -> inAppCapture.start()
            else -> micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Leaving Listening for ANY reason — cancelled, or the recognizer finished
    // — must also stop the recognizer itself. Cancelling only the controller
    // would leave the microphone live behind a screen that says it is not
    // listening, which is both a privacy problem and a battery one. Harmless
    // when the recognizer already completed on its own.
    val isListening = state is VoiceCommandState.Listening
    LaunchedEffect(isListening) {
        if (!isListening) inAppCapture?.cancel()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.onBackgrounded()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Swiping back ends the session. This is NOT covered by the lifecycle
    // observer above: popping this screen leaves the app perfectly foreground,
    // so ON_STOP never fires and nothing else would tell the controller to
    // stop. Without it, walking away mid-countdown left the demo running
    // behind the hero screen — committing off-screen, buzzing for a command
    // the user had already abandoned, and still showing that outcome on the
    // way back in.
    DisposableEffect(viewModel) {
        onDispose { viewModel.onScreenLeft() }
    }

    VoiceContent(
        state = state,
        mode = mode,
        doorState = doorState,
        partialTranscript = partialTranscript,
        listeningLevel = listeningLevel,
        onMicTap = viewModel::onMicTap,
        onCancel = viewModel::onCancel,
        modifier = modifier,
    )
}

/**
 * Stateless voice demo layout (previewable).
 *
 * ## One skeleton, every state
 *
 * Three anchors, and they never move: a **header** pinned to the top, the
 * **mic** on the screen's exact centre, and a **text block** pinned to the
 * bottom. Listening changes what those slots contain — rings appear, the mic
 * grows, the text becomes a live transcript — but not where any of them is.
 *
 * This replaced a vertically-centred column, whose height was a shared
 * resource: any line-count change moved everything else in it. Measured on a
 * 454px round watch, the mic and the "Simulated" marker sat 18px higher on
 * "Demo door is already open" (two lines) than on "Tap to speak" (one), and
 * moved again on the way to "Nothing was sent" — three shifts per utterance,
 * on exactly the states the user is reading. Reserving the tall slots would
 * have pinned the height too, but at the cost of blank gaps in the common
 * case, and it could not fix the larger problem: the takeover already used
 * absolute anchors, so the two modes disagreed about where the mic lived and
 * entering one jumped.
 *
 * Only the two variable text lines can now move, they move only when their own
 * text changes, and because the block is bottom-anchored they grow upward into
 * empty space rather than pushing anything.
 *
 * Widths are per-anchor: the header sits where a round screen's chord is wide,
 * so it gets [CONTENT_WIDTH_FRACTION]; the bottom block is near the mask, so it
 * gets the narrower [BOTTOM_TEXT_WIDTH_FRACTION] — an unconstrained line down
 * there is clipped at both ends rather than wrapping, the lesson the hero
 * screen learned the hard way.
 *
 * ## Three independent signals say "this is not real"
 *
 * Because one is easy to miss on a glance:
 *  1. A persistent "Simulated" marker, now in the header and therefore present
 *     in *every* state including the listening takeover.
 *  2. Conditional wording throughout — "Would open the door", never "Opening"
 *     — and a terminal state that says outright that nothing was sent.
 *  3. The door line is labelled "Demo door", so the thing visibly reacting is
 *     never mistaken for the garage.
 */
@Composable
fun VoiceContent(
    state: VoiceCommandState,
    mode: VoiceSurfaceMode,
    doorState: VoiceDoorState,
    partialTranscript: String?,
    listeningLevel: Float,
    onMicTap: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The SAME ring the real garage button draws, driven by the same hook —
    // see ConfirmRing. Voice's whole job on the simulated side is to rehearse
    // the real interaction, so the countdown and the commit have to speak the
    // hero screen's vocabulary rather than a dialect of it.
    val armed = state as? VoiceCommandState.Armed
    val ring = rememberConfirmRingState(
        phase = VoiceRing.phaseFor(state),
        sweepDurationMillis = (armed?.windowMs ?: WearVoiceViewModel.ARMED_WINDOW_MILLIS).toInt(),
        // Only the live surface has something genuinely outstanding while it
        // commits — a real request the server has not answered. The simulation
        // must not draw the rotating ring, which is precisely the claim it
        // cannot make; see VoiceSurfaceMode.hasRealRoundTrip.
        inFlight = mode.hasRealRoundTrip && state is VoiceCommandState.Sending,
    )

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
            //
            // ONE RULE: a tap starts what is not running and stops what is.
            // Listening and Armed are the two "running" states, so a tap there
            // cancels; everywhere else it starts a new capture.
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = state !is VoiceCommandState.Sending,
                    onClickLabel = stringResource(
                        if (VoiceStrings.isCancellable(state)) {
                            R.string.cd_voice_cancel
                        } else {
                            R.string.cd_voice_screen
                        },
                    ),
                    onClick = { if (VoiceStrings.isCancellable(state)) onCancel() else onMicTap() },
                ),
        ) {
            val listening = state is VoiceCommandState.Listening

            // Rings first so everything else draws over them. Listening gets the
            // whole screen rather than a changed label: at a glance, "is it
            // hearing me?" has to be answerable without reading anything.
            if (listening) {
                PulseRings(
                    level = listeningLevel,
                    // The rings must stop short of whatever the text block
                    // actually occupies, which is one line for the prompt and
                    // two once a transcript is being spoken into it.
                    textLines = VoiceLayout.listeningTextLines(partialTranscript),
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Header: what this is, and the door any command will be judged
            // against. Both are persistent context rather than state, so they
            // belong together and stay put.
            //
            // WHILE LISTENING the door line steps aside. 0.3.4 had kept it on
            // the grounds that the door is exactly what decides whether the
            // sentence you are about to say gets accepted, so the moment before
            // speaking is when it is worth reading — which was true when `Ready`
            // was a separate screen you sat on first. It is a straight cost now:
            // the screen carried four lines of text during listening and the
            // pulse rings swept through all of them. The door line is present in
            // every other state, including the refusal that would explain itself
            // with it.
            //
            // The SIMULATION marker is the one thing that never steps aside. It
            // is the safety signal, so it has to survive the takeover that hides
            // everything else — that state is exactly when someone is looking at
            // the animation rather than reading the screen.
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(CONTENT_WIDTH_FRACTION)
                    .padding(top = HEADER_TOP_DP.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (mode == VoiceSurfaceMode.Simulated) {
                    Text(
                        text = stringResource(R.string.voice_sim_marker),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        textAlign = TextAlign.Center,
                    )
                }
                if (!listening) {
                    Text(
                        text = stringResource(
                            VoiceStrings.doorLine(mode),
                            stringResource(VoiceStrings.doorLabel(doorState)),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            MicTarget(
                listening = listening,
                mode = mode,
                level = listeningLevel,
                enabled = state !is VoiceCommandState.Sending,
                // Same rule as the whole-screen target: while something is
                // running the mic is a stop button, not a restart one.
                onClick = { if (VoiceStrings.isCancellable(state)) onCancel() else onMicTap() },
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(BOTTOM_TEXT_WIDTH_FRACTION)
                    // Listening sits lower, because it is one line rather than
                    // two and every dp it gives back is a dp the pulse rings can
                    // travel before they reach it.
                    .padding(
                        bottom = if (listening) {
                            VoiceLayout.LISTENING_TEXT_PADDING_DP.dp
                        } else {
                            BOTTOM_TEXT_PADDING_DP.dp
                        },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (listening) {
                    ListeningLine(partialTranscript = partialTranscript)
                } else {
                    Text(
                        text = stringResource(VoiceStrings.primaryLine(state, mode)),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = VoiceStrings.secondaryLine(state, mode, partialTranscript),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        minLines = 1,
                    )
                }
            }
            // Literally the hero screen's ring, not a lookalike. Drawn last so
            // it is on top; it takes no input so it can never block the mic.
            ConfirmRing(
                ring = ring,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(HeroLayout.RING_EDGE_PADDING_DP.dp),
                // The loudest of the simulation's four signals: hue is what
                // reads while the ring is moving and the words are not being
                // read.
                colors = mode.ringColors,
            )
        }
    }
}

/**
 * The mic, on the exact centre of the screen in every state.
 *
 * Its position is the layout's fixed point: concentric with the pulse rings by
 * construction, and identical whether the demo is at rest or listening, so
 * entering the listening state is a grow rather than a jump. Only its size and
 * its skin change.
 *
 * At rest it is a real button — the affordance that invites the first tap. While
 * listening it is a plain surface: there is nothing left to invite, the whole
 * screen is already the stop target, and the accessible label on that target
 * says so.
 */
@Composable
private fun BoxScope.MicTarget(
    listening: Boolean,
    mode: VoiceSurfaceMode,
    level: Float,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    if (listening) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                // Grows with your voice. Small gain on purpose: this reads
                // as responsiveness, and anything larger reads as jitter.
                .scale(1f + level * MIC_SCALE_GAIN)
                .size(MIC_LISTENING_SIZE_DP.dp)
                // The listening mic is the screen's largest object, so it is
                // the natural place for the second glanceable signal after the
                // ring: `primary` when this is really listening for the garage,
                // `tertiary` when it is a rehearsal.
                .background(
                    when (mode) {
                        VoiceSurfaceMode.Live -> MaterialTheme.colorScheme.primary
                        VoiceSurfaceMode.Simulated -> MaterialTheme.colorScheme.tertiary
                    },
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_mic_24),
                contentDescription = stringResource(R.string.cd_voice_listening),
                tint = when (mode) {
                    VoiceSurfaceMode.Live -> MaterialTheme.colorScheme.onPrimary
                    VoiceSurfaceMode.Simulated -> MaterialTheme.colorScheme.onTertiary
                },
                modifier = Modifier.size(MIC_LISTENING_ICON_DP.dp),
            )
        }
    } else {
        FilledTonalIconButton(
            onClick = onClick,
            // A press cannot be unsent, so the real feature disables the button
            // while sending. Mirrored here so the demo teaches the same
            // affordance.
            enabled = enabled,
            modifier = Modifier
                .align(Alignment.Center)
                .size(MIC_BUTTON_SIZE_DP.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_mic_24),
                contentDescription = stringResource(R.string.cd_voice_mic),
                modifier = Modifier.size(MIC_ICON_SIZE_DP.dp),
            )
        }
    }
}

/**
 * The bottom text while listening: **one line**, which changes job partway
 * through.
 *
 * Before speech it is an example command. The classifier accepts a narrow
 * imperative grammar, so the example is load-bearing rather than decorative — it
 * is the difference between a command that works and one that is refused. Once
 * words arrive it becomes the live transcript, because the screen should stop
 * instructing the moment it has something to reflect instead.
 *
 * It used to be TWO lines: the example plus "Tap anywhere to cancel", added in
 * 0.3.4 to fix a real gap (0.3.3 made a tap cancel, and this was the one state
 * that could be escaped but never said so). The hint is dropped here because
 * the listening screen was carrying four lines of text with pulse rings sweeping
 * through them, and of the four this is the one whose absence costs least: the
 * gesture is unchanged, the accessible label on the full-screen target still
 * announces it, and `Armed` — the state that follows, and the one where
 * cancelling actually matters, because it is about to commit — still spells it
 * out. What listening needs to say is what to say.
 */
@Composable
private fun ListeningLine(partialTranscript: String?) {
    Text(
        text = partialTranscript
            ?.let { stringResource(R.string.voice_transcript, it) }
            ?: stringResource(R.string.voice_listening_prompt),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        // As many lines as [VoiceLayout] reserved, never more. The block is
        // bottom-anchored, so the second line grows UPWARD into the annulus the
        // rings were capped out of — which is safe only because both numbers
        // come from listeningTextLines(). Hard-coding either one is how the
        // overlap comes back for long sentences only: the worst kind of
        // regression, since the short test phrase keeps looking right.
        // Two when there is a transcript, one for the prompt — the same
        // decision VoiceLayout reserves space for, taken from the same place
        // so the text and the rings can never disagree about how tall this is.
        maxLines = VoiceLayout.listeningTextLines(partialTranscript),
        // START, not end: this is a live transcript, so the words you want are
        // the ones that just arrived. Ellipsizing the tail would pin the screen
        // to the beginning of the sentence and hide the part still being said.
        overflow = TextOverflow.StartEllipsis,
    )
}

/**
 * Concentric rings expanding from the mic and fading as they go — the common
 * "I am listening" idiom, so it needs no explanation.
 *
 * [RING_COUNT] rings share one looping phase, offset evenly, which produces a
 * continuous procession from a single animation rather than N staggered ones.
 * [level] widens their reach and raises their opacity, so silence still shows
 * life (the app has not frozen) while speech is unmistakably different.
 */
@Composable
private fun PulseRings(
    level: Float,
    textLines: Int,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "listening")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(PULSE_PERIOD_MS, easing = LinearEasing),
        ),
        label = "pulsePhase",
    )
    val color = MaterialTheme.colorScheme.tertiary
    Canvas(modifier = modifier) {
        val start = MIC_LISTENING_SIZE_DP.dp.toPx() / 2f
        // NOT the bezel: the outer band belongs to the confirm ring and the
        // bottom band to the listening line. See VoiceLayout.
        val limit = VoiceLayout
            .pulseMaxRadiusDp(
                diameterDp = size.minDimension.toDp().value,
                micRadiusDp = MIC_LISTENING_SIZE_DP / 2f,
                textLines = textLines,
            ).dp
            .toPx()
        val quiet = start + (limit - start) * IDLE_REACH_FRACTION
        val reach = quiet + (limit - quiet) * level
        val stroke = PULSE_STROKE_DP.dp.toPx()
        repeat(RING_COUNT) { index ->
            val progress = (phase + index.toFloat() / RING_COUNT) % 1f
            val alpha = (1f - progress) * (IDLE_PEAK_ALPHA + (LOUD_PEAK_ALPHA - IDLE_PEAK_ALPHA) * level)
            drawCircle(
                color = color,
                radius = start + (reach - start) * progress,
                alpha = alpha,
                style = Stroke(width = stroke),
            )
        }
    }
}

/**
 * Which [RingPhase] each demo state is, kept pure so the honesty rules are
 * testable — the same treatment `HeroRing` gets, because they are the same
 * rules.
 */
internal object VoiceRing {
    fun phaseFor(state: VoiceCommandState): RingPhase =
        when (state) {
            // Counting down the cancel window, exactly as a hold counts down to
            // a press.
            is VoiceCommandState.Armed -> RingPhase.Sweeping
            // BOTH halves of the commit. `Sending` alone lasts 600ms and the
            // bloom takes ~720ms, so keying off it would cut the celebration
            // off mid-recede. They are one moment anyway: "would press the
            // remote" and "nothing was sent" are the two beats of a single
            // simulated commit, and the ring should not flinch between them.
            is VoiceCommandState.Sending,
            is VoiceCommandState.Sent,
            -> RingPhase.Committed
            // Everything else unwinds. `Failed` is deliberately here and not
            // with the commit: a bloom is the app saying "that worked", so a
            // failed press must never get one — the same rule that keeps the
            // hero screen's bloom reachable only by actually calling the
            // server. A refusal likewise gets the rewind, which is what makes
            // "nothing happened" legible.
            VoiceCommandState.Ready,
            is VoiceCommandState.Listening,
            is VoiceCommandState.Failed,
            is VoiceCommandState.Ignored,
            -> RingPhase.Idle
        }
}

/**
 * Where the listening takeover's pulse rings are allowed to go.
 *
 * A FRACTION of the radius would be wrong here for the reason [HeroLayout]
 * exists: the text band is a fixed dp height, so the fraction it occupies grows
 * as the screen shrinks, and a cap that clears the text on a large round watch
 * is swallowed by it on a small one. Derive it from the band instead.
 */
internal object VoiceLayout {
    /**
     * How far the listening line's TOP edge sits from the bottom of the
     * screen: its padding plus one line of `bodySmall` per line reserved.
     */
    const val LISTENING_TEXT_PADDING_DP: Float = 30f
    private const val LISTENING_TEXT_LINE_DP: Float = 20f

    /**
     * How many lines the listening text needs.
     *
     * The prompt is authored to fit one line and does. A TRANSCRIPT is a
     * sentence somebody said, and real commands do not fit: "open the garage
     * door please" is 27 characters and was rendered `…en the garage door
     * please"`, losing the verb — on the one line whose whole job is to show
     * you what the watch thinks you said.
     *
     * Two lines is the fix, and it is granted only while a transcript is
     * present so the prompt state keeps every pixel of ring travel it has now.
     */
    fun listeningTextLines(partialTranscript: String?): Int = if (partialTranscript == null) 1 else 2

    /** Gap between the outermost ring and that text, so they never touch. */
    private const val PULSE_CLEARANCE_DP: Float = 6f

    /**
     * Outermost radius a pulse ring may reach. Rings used to travel all the way
     * to the bezel, which meant every one of them crossed both the header and
     * the bottom text on its way out — the animation was drawn straight through
     * the words it was supposed to be accompanying.
     */
    fun pulseMaxRadiusDp(
        diameterDp: Float,
        micRadiusDp: Float,
        textLines: Int = 1,
    ): Float =
        (
            diameterDp / 2f - LISTENING_TEXT_PADDING_DP -
                LISTENING_TEXT_LINE_DP * textLines - PULSE_CLEARANCE_DP
        )
            // A watch small enough for the band to swallow the whole annulus
            // gets a halo hugging the mic rather than an inverted radius.
            .coerceAtLeast(micRadiusDp)
}

/**
 * The two surfaces side by side at rest, which is the pair worth reviewing:
 * everything about them is identical except the marker, the wording and the
 * colour, and that is exactly the claim the design makes.
 */
@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
private fun VoiceLiveReadyPreview() {
    MaterialTheme {
        VoiceContent(
            state = VoiceCommandState.Ready,
            mode = VoiceSurfaceMode.Live,
            doorState = VoiceDoorState.CLOSED,
            partialTranscript = null,
            listeningLevel = 0f,
            onMicTap = {},
            onCancel = {},
        )
    }
}

/** The same state on the rehearsal: marked, and named as a demo door. */
@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
private fun VoiceSimulatedReadyPreview() {
    MaterialTheme {
        VoiceContent(
            state = VoiceCommandState.Ready,
            mode = VoiceSurfaceMode.Simulated,
            doorState = VoiceDoorState.CLOSED,
            partialTranscript = null,
            listeningLevel = 0f,
            onMicTap = {},
            onCancel = {},
        )
    }
}

/**
 * Counting down a REAL press: the neutral ring, and an action stated as fact.
 * This is the frame in which a garage door is three seconds from moving, so it
 * is the one that has to be unambiguous.
 */
@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
private fun VoiceLiveArmedPreview() {
    MaterialTheme {
        VoiceContent(
            state = VoiceCommandState.Armed(
                intent = VoiceIntent.OPEN,
                transcript = "open the garage door",
                windowMs = WearVoiceViewModel.ARMED_WINDOW_MILLIS,
            ),
            mode = VoiceSurfaceMode.Live,
            doorState = VoiceDoorState.CLOSED,
            partialTranscript = null,
            listeningLevel = 0f,
            onMicTap = {},
            onCancel = {},
        )
    }
}

/** The same countdown, rehearsed: azure ring, conditional wording. */
@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
private fun VoiceSimulatedArmedPreview() {
    MaterialTheme {
        VoiceContent(
            state = VoiceCommandState.Armed(
                intent = VoiceIntent.OPEN,
                transcript = "open the garage door",
                windowMs = WearVoiceViewModel.ARMED_WINDOW_MILLIS,
            ),
            mode = VoiceSurfaceMode.Simulated,
            doorState = VoiceDoorState.CLOSED,
            partialTranscript = null,
            listeningLevel = 0f,
            onMicTap = {},
            onCancel = {},
        )
    }
}

/** The commit instant: the ring completes and holds instead of vanishing. */
@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
private fun VoiceSimulatedCommittingPreview() {
    MaterialTheme {
        VoiceContent(
            state = VoiceCommandState.Sending(intent = VoiceIntent.OPEN),
            mode = VoiceSurfaceMode.Simulated,
            doorState = VoiceDoorState.CLOSED,
            partialTranscript = null,
            listeningLevel = 0f,
            onMicTap = {},
            onCancel = {},
        )
    }
}

/** The live receipt: the press really went to the server. */
@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
private fun VoiceLiveSentPreview() {
    MaterialTheme {
        VoiceContent(
            state = VoiceCommandState.Sent(intent = VoiceIntent.OPEN),
            mode = VoiceSurfaceMode.Live,
            doorState = VoiceDoorState.MOVING,
            partialTranscript = null,
            listeningLevel = 0f,
            onMicTap = {},
            onCancel = {},
        )
    }
}

/** The rehearsal's punchline: the window elapsed and nothing was sent. */
@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
private fun VoiceSimulatedSentPreview() {
    MaterialTheme {
        VoiceContent(
            state = VoiceCommandState.Sent(intent = VoiceIntent.OPEN),
            mode = VoiceSurfaceMode.Simulated,
            doorState = VoiceDoorState.MOVING,
            partialTranscript = null,
            listeningLevel = 0f,
            onMicTap = {},
            onCancel = {},
        )
    }
}

/** A gate refusal against the REAL door, quoting what was heard. */
@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
private fun VoiceLiveIgnoredPreview() {
    MaterialTheme {
        VoiceContent(
            state = VoiceCommandState.Ignored(
                reason = VoiceCommandIgnoreReason.DOOR_ALREADY_OPEN,
                transcript = "open the garage door",
                classification = null,
                engineName = "Rules v3",
            ),
            mode = VoiceSurfaceMode.Live,
            doorState = VoiceDoorState.OPEN,
            partialTranscript = null,
            listeningLevel = 0f,
            onMicTap = {},
            onCancel = {},
        )
    }
}

/** The same refusal, rehearsed — reachable here without cycling a real door. */
@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
private fun VoiceSimulatedIgnoredPreview() {
    MaterialTheme {
        VoiceContent(
            state = VoiceCommandState.Ignored(
                reason = VoiceCommandIgnoreReason.DOOR_ALREADY_OPEN,
                transcript = "open the garage door",
                classification = null,
                engineName = "Rules v3",
            ),
            mode = VoiceSurfaceMode.Simulated,
            doorState = VoiceDoorState.OPEN,
            partialTranscript = null,
            listeningLevel = 0f,
            onMicTap = {},
            onCancel = {},
        )
    }
}

/**
 * Width of the header, which sits high enough that the round screen's chord is
 * still generous.
 */
private const val CONTENT_WIDTH_FRACTION = 0.72f

/**
 * Width of the bottom text block, and how far its last line stays clear of the
 * bottom edge. The two are one decision: on a round screen the usable chord
 * shrinks fast as you approach the edge, so a wider block has to sit higher.
 *
 * Sized together against the deepest row this block can reach. Going narrow
 * instead (the hero screen's 0.56, which works there because it carries at most
 * two short lines) left this block wrapping almost every string and orphaning
 * single words, which is worse than the clipping it avoids. Raising it buys
 * back the width, at the cost of some empty screen below — which the round mask
 * mostly eats anyway.
 */
private const val BOTTOM_TEXT_WIDTH_FRACTION = 0.72f
private const val MIC_BUTTON_SIZE_DP = 52
private const val MIC_ICON_SIZE_DP = 26

/** Pulse rings only — the confirm ring's own stroke lives in [HeroLayout]. */
private const val PULSE_STROKE_DP = 5

/**
 * The mic while listening: bigger than the resting button, and wearing the
 * simulation's colour.
 *
 * Trimmed from 84dp when the pulse rings were capped. The rings now have to fit
 * between this circle and the reserved text band, and at 84dp that annulus was
 * thin enough on a small round watch to read as a static halo rather than
 * motion. 68dp is still a third larger than the 52dp resting button, so
 * entering the listening state still visibly grows it.
 */
private const val MIC_LISTENING_SIZE_DP = 68
private const val MIC_LISTENING_ICON_DP = 34

/**
 * Clear of `TimeText`, which owns the top arc. At 18dp the marker sat directly
 * under the clock with no gap and read as one crowded block; the screen has the
 * vertical room to spare, so it buys the separation.
 */
private const val HEADER_TOP_DP = 30

/**
 * Paired with [BOTTOM_TEXT_WIDTH_FRACTION] — see its note. Measured, not
 * guessed: at 34dp the widest single line the block has to render came out
 * ~10px over the available chord and wrapped, orphaning one word.
 */
private const val BOTTOM_TEXT_PADDING_DP = 38

/** Small on purpose: responsiveness, not jitter. */
private const val MIC_SCALE_GAIN = 0.10f

/** One full ring journey, mic edge to its reach. */
private const val PULSE_PERIOD_MS = 1800
private const val RING_COUNT = 3

/** How far rings travel toward the bezel when nothing is being said. */
private const val IDLE_REACH_FRACTION = 0.45f
private const val IDLE_PEAK_ALPHA = 0.30f
private const val LOUD_PEAK_ALPHA = 0.75f
