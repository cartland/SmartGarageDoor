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

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.PointerEventPass
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
        authState = authState,
        buttonState = buttonState,
        isHolding = isHolding,
        onDoorTap = viewModel::onDoorTap,
        onHoldStart = viewModel::onHoldStart,
        onHoldEnd = viewModel::onHoldEnd,
        onAnyTouch = viewModel::onScreenTouch,
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
 * sign-in chip. [onAnyTouch] fires for every pointer down AND up anywhere
 * on the screen (observed on the Initial pass, never consumed) — the
 * ViewModel uses it to keep the armed window alive while the user keeps
 * interacting.
 *
 * Geometry: the hold ring is centered on the PHYSICAL screen and hugs the
 * bezel (like the platform's own progress rings), and in the signed-in
 * layout the door is centered on the screen too, with the state label and
 * hint anchored near the bottom edge. The signed-out/unknown layout keeps a
 * centered column (smaller door + sign-in chip + reserved caption slot —
 * the 0.1.2 overflow fix); the ring never shows there because arming
 * requires authentication.
 */
@Composable
fun HeroScreenContent(
    doorPosition: DoorPosition,
    lastChangeTimeSeconds: Long?,
    authState: AuthState,
    buttonState: RemoteButtonState,
    isHolding: Boolean,
    signInError: Boolean,
    onDoorTap: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    onAnyTouch: () -> Unit,
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

    val currentOnAnyTouch by rememberUpdatedState(onAnyTouch)
    ScreenScaffold(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Observe (never consume) every gesture's down and up on
                    // the Initial pass, so touches anywhere on the screen —
                    // including ones handled by the door's own tap detector —
                    // reach the ViewModel and restart the armed window.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        currentOnAnyTouch()
                        var anyPressed = true
                        while (anyPressed) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            anyPressed = event.changes.any { it.pressed }
                        }
                        // Release counts too: the quiet period runs from the
                        // LAST touch, so it starts at finger-up.
                        currentOnAnyTouch()
                    }
                },
        ) {
            if (authState is AuthState.Authenticated) {
                GarageDoorTarget(
                    doorPosition = doorPosition,
                    lastChangeTimeSeconds = lastChangeTimeSeconds,
                    animationMemory = animationMemory,
                    onDoorTap = onDoorTap,
                    onHoldStart = onHoldStart,
                    onHoldEnd = onHoldEnd,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(DOOR_WIDTH_FRACTION),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = BOTTOM_TEXT_PADDING_DP.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = HeroScreenMappers.doorStateLabel(doorPosition),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    val hint = HeroScreenMappers.buttonHint(buttonState)
                    if (hint != null) {
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
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
                        onDoorTap = onDoorTap,
                        onHoldStart = onHoldStart,
                        onHoldEnd = onHoldEnd,
                        modifier = Modifier.fillMaxWidth(DOOR_WIDTH_FRACTION_SIGNED_OUT),
                    )
                    Text(
                        text = HeroScreenMappers.doorStateLabel(doorPosition),
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
                armed = buttonState is RemoteButtonState.AwaitingConfirmation,
                holdProgress = holdProgress,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(RING_PADDING_DP.dp),
            )
        }
    }
}

/**
 * The radial hold-to-confirm indicator, drawn at the bounds of whatever box
 * it's given — the hero screen gives it the full physical screen so the ring
 * is concentric with the bezel. Callers place it as the topmost layer.
 */
@Composable
private fun HoldRing(
    armed: Boolean,
    holdProgress: Float,
    modifier: Modifier = Modifier,
) {
    val ringColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier) {
        val stroke = RING_STROKE_DP.dp.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        if (armed) {
            // Faint full track while armed: "this is holdable".
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
        if (holdProgress > 0f) {
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

/** The tappable/holdable door: gestures land exactly on the door's box. */
@Composable
private fun GarageDoorTarget(
    doorPosition: DoorPosition,
    lastChangeTimeSeconds: Long?,
    animationMemory: DoorAnimationMemory,
    onDoorTap: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val doorDescription = stringResource(R.string.cd_garage_door)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .semantics { contentDescription = doorDescription }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onDoorTap() },
                    onPress = {
                        onHoldStart()
                        tryAwaitRelease()
                        onHoldEnd()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        WearGarageIcon(
            doorPosition = doorPosition,
            animationMemory = animationMemory,
            lastChangeTimeSeconds = lastChangeTimeSeconds,
            color = WearDoorColors.forPosition(doorPosition),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** String/label mappers for the hero screen. */
private object HeroScreenMappers {
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

    @Composable
    fun buttonHint(buttonState: RemoteButtonState): String? =
        when (buttonState) {
            RemoteButtonState.Ready -> stringResource(R.string.button_hint_tap_to_arm)
            RemoteButtonState.Preparing -> stringResource(R.string.button_hint_arming)
            RemoteButtonState.AwaitingConfirmation -> stringResource(R.string.button_hint_hold_to_press)
            RemoteButtonState.Cancelled -> stringResource(R.string.button_hint_cancelled)
            RemoteButtonState.SendingToServer -> stringResource(R.string.button_hint_sending)
            RemoteButtonState.SendingToDoor -> stringResource(R.string.button_hint_signaling_door)
            RemoteButtonState.Succeeded -> stringResource(R.string.button_hint_succeeded)
            RemoteButtonState.ServerFailed -> stringResource(R.string.button_hint_server_failed)
            RemoteButtonState.DoorFailed -> stringResource(R.string.button_hint_door_failed)
        }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
private fun HeroScreenContentArmedPreview() {
    MaterialTheme {
        HeroScreenContent(
            doorPosition = DoorPosition.CLOSED,
            lastChangeTimeSeconds = null,
            authState = AuthState.Authenticated(
                User(
                    name = DisplayName("Preview User"),
                    email = Email("preview@example.com"),
                ),
            ),
            buttonState = RemoteButtonState.AwaitingConfirmation,
            isHolding = false,
            signInError = false,
            onDoorTap = {},
            onHoldStart = {},
            onHoldEnd = {},
            onAnyTouch = {},
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
            authState = AuthState.Unauthenticated,
            buttonState = RemoteButtonState.Ready,
            isHolding = false,
            signInError = false,
            onDoorTap = {},
            onHoldStart = {},
            onHoldEnd = {},
            onAnyTouch = {},
            onSignInClick = {},
        )
    }
}

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
