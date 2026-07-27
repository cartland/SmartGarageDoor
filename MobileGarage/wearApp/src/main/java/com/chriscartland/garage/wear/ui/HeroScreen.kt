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

import android.view.HapticFeedbackConstants
import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.DisplayName
import com.chriscartland.garage.domain.model.DoorAnimationMemory
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.RemoteButtonState
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.wear.R
import com.chriscartland.garage.wear.auth.WearGoogleSignIn
import com.chriscartland.garage.wear.di.WearSignInConfig
import com.chriscartland.garage.wear.ui.theme.WearDoorColors
import kotlinx.coroutines.launch

/**
 * Stateful hero screen: collects the ViewModel flows, owns the
 * visibility-driven refresh loop and the sign-in launcher, and delegates
 * rendering to [HeroScreenContent].
 */
@Composable
fun HeroScreen(
    viewModel: WearHomeViewModel,
    signInConfig: WearSignInConfig,
    modifier: Modifier = Modifier,
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val doorEvent by viewModel.currentDoorEvent.collectAsStateWithLifecycle()
    val buttonState by viewModel.buttonState.collectAsStateWithLifecycle()
    val isHolding by viewModel.isHolding.collectAsStateWithLifecycle()
    val signInError by viewModel.signInError.collectAsStateWithLifecycle()
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()

    // Hold the screen awake only while the ViewModel says something worth
    // watching is happening (press in flight / door moving, 15s cap). The
    // window flag is the irreducible platform write; the decision is the VM's.
    val view = LocalView.current
    LaunchedEffect(view, keepScreenOn) { view.keepScreenOn = keepScreenOn }
    DisposableEffect(view) {
        onDispose { view.keepScreenOn = false }
    }

    // Haptics: the ViewModel decides WHEN and WHAT (testable); this performs
    // the platform write. View.performHapticFeedback needs no VIBRATE
    // permission and respects the watch's own touch-feedback setting — which
    // is why the ring, not the buzz, stays the authoritative channel.
    LaunchedEffect(view, viewModel) {
        viewModel.hapticCues.collect { cue ->
            view.performHapticFeedback(HeroScreenHaptics.constantFor(cue))
        }
    }

    // Foreground-only refresh: poll while the screen is visible, stop when hidden.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onVisible()
                Lifecycle.Event.ON_STOP -> viewModel.onHidden()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onHidden()
        }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    HeroScreenContent(
        doorPosition = doorEvent?.doorPosition ?: DoorPosition.UNKNOWN,
        lastChangeTimeSeconds = doorEvent?.lastChangeTimeSeconds,
        hasDoorData = doorEvent != null,
        authState = authState,
        buttonState = buttonState,
        isHolding = isHolding,
        onHoldStart = viewModel::onHoldStart,
        onHoldEnd = viewModel::onHoldEnd,
        signInError = signInError,
        onSignInClick = {
            viewModel.onSignInStarted()
            scope.launch {
                val token = WearGoogleSignIn.requestGoogleIdToken(
                    context = context,
                    serverClientId = signInConfig.googleServerClientId,
                )
                viewModel.onSignInResult(token)
            }
        },
        modifier = modifier,
    )
}

/**
 * Stateless hero layout (previewable): the animated door with the radial
 * hold-to-confirm indicator, the door state label, and the button hint or
 * sign-in chip.
 *
 * Geometry: the hold ring is centered on the PHYSICAL screen and hugs the
 * bezel (like the platform's own progress rings), and in the signed-in
 * layout the door is centered on the screen too, with the state label and
 * hint anchored near the bottom edge. The signed-out/unknown layout keeps a
 * centered column (smaller door + sign-in chip + reserved caption slot —
 * the 0.1.2 overflow fix); the ring never shows there because holding
 * requires authentication.
 */
@Composable
fun HeroScreenContent(
    doorPosition: DoorPosition,
    lastChangeTimeSeconds: Long?,
    hasDoorData: Boolean,
    authState: AuthState,
    buttonState: RemoteButtonState,
    isHolding: Boolean,
    signInError: Boolean,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animationMemory = remember { DoorAnimationMemory() }

    // Visual sweep of the radial indicator. The ViewModel's countdown is
    // authoritative for firing the press; this animation only mirrors it.
    val holdProgress by animateFloatAsState(
        targetValue = if (isHolding) 1f else 0f,
        animationSpec = if (isHolding) {
            tween(
                durationMillis = WearHomeViewModel.HOLD_TO_CONFIRM_MILLIS.toInt(),
                easing = LinearEasing,
            )
        } else {
            tween(durationMillis = RING_RELEASE_MILLIS)
        },
        label = "holdProgress",
    )

    ScreenScaffold(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (authState is AuthState.Authenticated) {
                GarageDoorTarget(
                    doorPosition = doorPosition,
                    lastChangeTimeSeconds = lastChangeTimeSeconds,
                    animationMemory = animationMemory,
                    suppressWarningOverlay = !hasDoorData,
                    onHoldStart = onHoldStart,
                    onHoldEnd = onHoldEnd,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(DOOR_WIDTH_FRACTION),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        // Near the bottom of a round screen the usable chord is
                        // far narrower than the full width, so an unconstrained
                        // line is clipped by the mask at BOTH ends rather than
                        // wrapping (this is what bit "Hold to press the remote").
                        // Constrain to the safe chord and let long hints wrap.
                        .fillMaxWidth(BOTTOM_TEXT_WIDTH_FRACTION)
                        .padding(bottom = BOTTOM_TEXT_PADDING_DP.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = HeroScreenMappers.doorStateLabel(doorPosition, hasDoorData),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    // Reserved slot: the hint goes empty mid-hold (the ring is
                    // the progress channel there), and an empty line keeps the
                    // state label from jumping when it does.
                    Text(
                        text = HeroScreenMappers
                            .buttonHint(
                                buttonState = buttonState,
                                doorPosition = doorPosition,
                                hasDoorData = hasDoorData,
                            ).orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        minLines = 1,
                    )
                }
            } else {
                // Signed-out/unknown: centered column. The smaller door keeps
                // the door + label + chip + caption inside the round viewport
                // (0.1.2 fix — the caption used to overflow into the screen's
                // clipped bottom edge).
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    GarageDoorTarget(
                        doorPosition = doorPosition,
                        lastChangeTimeSeconds = lastChangeTimeSeconds,
                        animationMemory = animationMemory,
                        suppressWarningOverlay = !hasDoorData,
                        onHoldStart = onHoldStart,
                        onHoldEnd = onHoldEnd,
                        modifier = Modifier.fillMaxWidth(DOOR_WIDTH_FRACTION_SIGNED_OUT),
                    )
                    Text(
                        text = HeroScreenMappers.doorStateLabel(doorPosition, hasDoorData),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    if (authState is AuthState.Unknown) {
                        Text(
                            text = stringResource(R.string.checking_sign_in),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        Button(
                            onClick = onSignInClick,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Text(text = stringResource(R.string.sign_in))
                        }
                        // Reserved caption slot: empty text keeps the height
                        // stable so the transient failure message (auto-cleared
                        // by the ViewModel) never reflows the column.
                        Text(
                            text = if (signInError) stringResource(R.string.sign_in_failed) else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            minLines = 1,
                        )
                    }
                }
            }
            // Hold-to-confirm ring: centered on the physical screen, hugging
            // the bezel — never around the door image, whose own box sits
            // wherever the layout puts it. LAST child on purpose: the ring
            // draws on top of everything (it takes no input, so it can never
            // block the door's gestures).
            HoldRing(
                holding = isHolding,
                inFlight = buttonState is RemoteButtonState.SendingToServer ||
                    buttonState is RemoteButtonState.SendingToDoor,
                holdProgress = holdProgress,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(RING_PADDING_DP.dp),
            )
        }
    }
}

/**
 * The radial indicator, drawn at the bounds of whatever box it's given — the
 * hero screen gives it the full physical screen so the ring is concentric
 * with the bezel. Callers place it as the topmost layer.
 *
 * Two jobs. While [holding] it sweeps to report progress toward the press.
 * While [inFlight] it holds a *complete* ring in a different colour, which is
 * the "your press was submitted" visual: the sweep does not just vanish at
 * the moment of commitment, it completes and changes character, then stays up
 * for as long as the press is actually outstanding.
 *
 * A brief flash at the commit instant was considered and rejected: a state is
 * more useful than a transient here (it pairs with "Sending" / "Waiting for
 * the door"), and unlike a 400ms animation it is deterministically capturable
 * by the screenshot fixture.
 */
@Composable
private fun HoldRing(
    holding: Boolean,
    inFlight: Boolean,
    holdProgress: Float,
    modifier: Modifier = Modifier,
) {
    val ringColor = MaterialTheme.colorScheme.primary
    val sentColor = MaterialTheme.colorScheme.tertiary
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier) {
        val stroke = RING_STROKE_DP.dp.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        if (holding) {
            // Faint full track under the sweep: "here is how far you have to go".
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
        }
        if (inFlight) {
            drawArc(
                color = sentColor,
                startAngle = ARC_START_ANGLE,
                sweepAngle = FULL_SWEEP,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke),
            )
        } else if (holdProgress > 0f) {
            drawArc(
                color = ringColor,
                startAngle = ARC_START_ANGLE,
                sweepAngle = FULL_SWEEP * holdProgress,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

/**
 * The holdable door: gestures land exactly on the door's box.
 *
 * There is deliberately no tap handler — a tap does nothing at all, and only
 * a continuous hold can reach the real garage button. The hold also cancels
 * when the finger drifts more than [HOLD_CANCEL_SLOP_DP], which is what
 * separates a deliberate thumb press (steady) from the sustained accidental
 * contact a sleeve or a wrist against a surface produces (wandering). That
 * drift check is the accidental-press protection that the old separate
 * arming tap used to provide.
 */
@Composable
private fun GarageDoorTarget(
    doorPosition: DoorPosition,
    lastChangeTimeSeconds: Long?,
    animationMemory: DoorAnimationMemory,
    suppressWarningOverlay: Boolean,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val doorDescription = stringResource(R.string.cd_garage_door)
    val currentOnHoldStart by rememberUpdatedState(onHoldStart)
    val currentOnHoldEnd by rememberUpdatedState(onHoldEnd)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .semantics { contentDescription = doorDescription }
            .pointerInput(Unit) {
                val cancelSlopPx = HOLD_CANCEL_SLOP_DP.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    currentOnHoldStart()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        // Finger lifted, or this pointer vanished.
                        if (change == null || !change.pressed) break
                        // Drifted too far to be a deliberate press.
                        if ((change.position - down.position).getDistance() > cancelSlopPx) break
                    }
                    // Fires for release AND drift-cancel; the ViewModel treats
                    // an incomplete hold the same either way. awaitEachGesture
                    // waits for all pointers to lift before restarting, so a
                    // drift-cancelled finger cannot immediately re-arm.
                    currentOnHoldEnd()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        WearGarageIcon(
            doorPosition = doorPosition,
            animationMemory = animationMemory,
            lastChangeTimeSeconds = lastChangeTimeSeconds,
            color = WearDoorColors.forPosition(doorPosition),
            suppressWarningOverlay = suppressWarningOverlay,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Maps a [HapticCue] to the platform constant that expresses it.
 *
 * All of these exist at the app's minSdk (30), so no version guard is
 * needed. The escalation is deliberate: a light tick to acknowledge, a
 * distinct tick at the midpoint, then CONFIRM — which is a two-beat pattern,
 * not merely a stronger tick — so "did it fire, or am I halfway?" is never
 * ambiguous by feel alone.
 */
internal object HeroScreenHaptics {
    fun constantFor(cue: HapticCue): Int =
        when (cue) {
            HapticCue.HoldEngaged -> HapticFeedbackConstants.GESTURE_START
            HapticCue.HoldHalfway -> HapticFeedbackConstants.CLOCK_TICK
            HapticCue.PressCommitted -> HapticFeedbackConstants.CONFIRM
            HapticCue.HoldAborted -> HapticFeedbackConstants.GESTURE_END
            // Lighter than PressCommitted on purpose — this one arrives after
            // the finger has gone, as news rather than gesture feedback.
            HapticCue.PressSucceeded -> HapticFeedbackConstants.CONTEXT_CLICK
            HapticCue.PressFailed -> HapticFeedbackConstants.REJECT
        }
}

/** String/label mappers for the hero screen. */
internal object HeroScreenMappers {
    /**
     * No door event at all (cold start) renders the calm "Connecting…"
     * headline — mirrors the phone Home card. A real server-reported
     * UNKNOWN event (hasDoorData = true) keeps the "Unknown" label.
     */
    @Composable
    fun doorStateLabel(
        doorPosition: DoorPosition,
        hasDoorData: Boolean,
    ): String =
        if (!hasDoorData) {
            stringResource(R.string.door_state_connecting)
        } else {
            doorStateLabel(doorPosition)
        }

    @Composable
    fun doorStateLabel(doorPosition: DoorPosition): String =
        stringResource(
            when (doorPosition) {
                DoorPosition.UNKNOWN -> R.string.door_state_unknown
                DoorPosition.CLOSED -> R.string.door_state_closed
                DoorPosition.OPENING -> R.string.door_state_opening
                DoorPosition.OPENING_TOO_LONG -> R.string.door_state_opening
                DoorPosition.OPEN -> R.string.door_state_open
                DoorPosition.OPEN_MISALIGNED -> R.string.door_state_open
                DoorPosition.CLOSING -> R.string.door_state_closing
                DoorPosition.CLOSING_TOO_LONG -> R.string.door_state_closing
                DoorPosition.ERROR_SENSOR_CONFLICT -> R.string.door_state_sensor_conflict
            },
        )

    /** Null means "render nothing here" — the reserved slot stays empty. */
    @Composable
    fun buttonHint(
        buttonState: RemoteButtonState,
        doorPosition: DoorPosition,
        hasDoorData: Boolean,
    ): String? =
        when (buttonState) {
            // At rest: say what a completed hold will do.
            RemoteButtonState.Ready -> stringResource(holdHint(doorPosition, hasDoorData))
            // Mid-hold. Both of these states now exist ONLY inside a hold
            // (Preparing is the machine's 500ms arming delay, which elapses
            // under the user's finger), and the sweeping ring already reports
            // progress — text would just duplicate it.
            RemoteButtonState.Preparing,
            RemoteButtonState.AwaitingConfirmation,
            -> null
            // Unreachable on Wear: an incomplete hold resets the machine
            // immediately, so the confirmation timeout can never fire. Kept
            // for `when` exhaustiveness.
            RemoteButtonState.Cancelled -> null
            RemoteButtonState.SendingToServer -> stringResource(R.string.button_hint_sending)
            RemoteButtonState.SendingToDoor -> stringResource(R.string.button_hint_waiting_for_door)
            // The door state label directly above already reads Opening/Closing.
            RemoteButtonState.Succeeded -> null
            RemoteButtonState.ServerFailed -> stringResource(R.string.button_hint_server_failed)
            RemoteButtonState.DoorFailed -> stringResource(R.string.button_hint_door_failed)
        }

    /**
     * The resting hint, keyed off what [doorStateLabel] will render rather
     * than off the raw [DoorPosition], so the hint and the label above it can
     * never disagree.
     *
     * Only CLOSED and OPEN rest on an affirmative sensor reading, so only
     * they get to predict the door's response. OPEN_MISALIGNED is grouped
     * with OPEN because the label already renders it as "Open" (it is a
     * confident Open whose sensor dropped out for under 3 seconds, and it is
     * well tested in the field). Everything else — including a cold start
     * with no door event at all — names our own action instead: we send a
     * remote press and the garage decides whether that opens, closes, or
     * pauses the door.
     */
    @StringRes
    fun holdHint(
        doorPosition: DoorPosition,
        hasDoorData: Boolean,
    ): Int =
        when {
            !hasDoorData -> R.string.button_hint_hold_to_press_remote
            doorPosition == DoorPosition.CLOSED -> R.string.button_hint_hold_to_open
            doorPosition == DoorPosition.OPEN ||
                doorPosition == DoorPosition.OPEN_MISALIGNED ->
                R.string.button_hint_hold_to_close
            else -> R.string.button_hint_hold_to_press_remote
        }
}

/** At rest on a confidently-closed door: "Hold to open". */
@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
private fun HeroScreenContentReadyPreview() {
    MaterialTheme {
        HeroScreenContent(
            doorPosition = DoorPosition.CLOSED,
            lastChangeTimeSeconds = null,
            hasDoorData = true,
            authState = PREVIEW_USER,
            buttonState = RemoteButtonState.Ready,
            isHolding = false,
            signInError = false,
            onHoldStart = {},
            onHoldEnd = {},
            onSignInClick = {},
        )
    }
}

/** Mid-hold: ring sweeping, hint slot deliberately empty. */
@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
private fun HeroScreenContentHoldingPreview() {
    MaterialTheme {
        HeroScreenContent(
            doorPosition = DoorPosition.CLOSED,
            lastChangeTimeSeconds = null,
            hasDoorData = true,
            authState = PREVIEW_USER,
            buttonState = RemoteButtonState.AwaitingConfirmation,
            isHolding = true,
            signInError = false,
            onHoldStart = {},
            onHoldEnd = {},
            onSignInClick = {},
        )
    }
}

/** An inferred position: no affirmative sensor, so no prediction. */
@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
private fun HeroScreenContentInferredPositionPreview() {
    MaterialTheme {
        HeroScreenContent(
            doorPosition = DoorPosition.OPENING,
            lastChangeTimeSeconds = null,
            hasDoorData = true,
            authState = PREVIEW_USER,
            buttonState = RemoteButtonState.Ready,
            isHolding = false,
            signInError = false,
            onHoldStart = {},
            onHoldEnd = {},
            onSignInClick = {},
        )
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
private fun HeroScreenContentSignedOutPreview() {
    MaterialTheme {
        HeroScreenContent(
            doorPosition = DoorPosition.OPEN,
            lastChangeTimeSeconds = null,
            hasDoorData = true,
            authState = AuthState.Unauthenticated,
            buttonState = RemoteButtonState.Ready,
            isHolding = false,
            signInError = false,
            onHoldStart = {},
            onHoldEnd = {},
            onSignInClick = {},
        )
    }
}

private val PREVIEW_USER = AuthState.Authenticated(
    User(
        name = DisplayName("Preview User"),
        email = Email("preview@example.com"),
    ),
)

// Door sizes match the pre-centering effective sizes (the old width fraction
// times the old inside-ring fraction) so the door itself reads the same.
private const val DOOR_WIDTH_FRACTION = 0.52f
private const val DOOR_WIDTH_FRACTION_SIGNED_OUT = 0.42f
private const val RING_PADDING_DP = 2
private const val RING_STROKE_DP = 5
private const val RING_RELEASE_MILLIS = 150
private const val ARC_START_ANGLE = -90f
private const val FULL_SWEEP = 360f
private const val TRACK_ALPHA = 0.25f
private const val BOTTOM_TEXT_PADDING_DP = 18

/**
 * Width of the bottom label/hint column, as a fraction of the screen. Sized
 * to the round screen's chord at that height so text wraps instead of being
 * clipped by the mask.
 */
private const val BOTTOM_TEXT_WIDTH_FRACTION = 0.56f

/**
 * How far the finger may drift before an in-progress hold is abandoned.
 * Generous enough that a normal thumb press never trips it, tight enough
 * that a sliding sleeve does.
 */
private const val HOLD_CANCEL_SLOP_DP = 20
