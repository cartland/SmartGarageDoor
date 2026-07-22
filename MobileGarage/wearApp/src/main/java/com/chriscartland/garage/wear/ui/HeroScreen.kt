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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
        onSignInClick = {
            scope.launch {
                val token = WearGoogleSignIn.requestGoogleIdToken(
                    context = context,
                    serverClientId = signInConfig.googleServerClientId,
                )
                if (token != null) {
                    viewModel.signIn(token)
                }
            }
        },
        modifier = modifier,
    )
}

/**
 * Stateless hero layout (previewable): the animated door with the radial
 * hold-to-confirm indicator, the door state label, and the button hint or
 * sign-in chip.
 */
@Composable
fun HeroScreenContent(
    doorPosition: DoorPosition,
    lastChangeTimeSeconds: Long?,
    authState: AuthState,
    buttonState: RemoteButtonState,
    isHolding: Boolean,
    onDoorTap: () -> Unit,
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
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            DoorWithHoldRing(
                doorPosition = doorPosition,
                lastChangeTimeSeconds = lastChangeTimeSeconds,
                animationMemory = animationMemory,
                armed = buttonState is RemoteButtonState.AwaitingConfirmation,
                holdProgress = holdProgress,
                onDoorTap = onDoorTap,
                onHoldStart = onHoldStart,
                onHoldEnd = onHoldEnd,
                modifier = Modifier.fillMaxWidth(DOOR_WIDTH_FRACTION),
            )
            Text(
                text = HeroScreenMappers.doorStateLabel(doorPosition),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            when {
                authState is AuthState.Authenticated -> {
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
                authState is AuthState.Unknown -> {
                    Text(
                        text = stringResource(R.string.checking_sign_in),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> {
                    Button(
                        onClick = onSignInClick,
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text(text = stringResource(R.string.sign_in))
                    }
                }
            }
        }
    }
}

@Composable
private fun DoorWithHoldRing(
    doorPosition: DoorPosition,
    lastChangeTimeSeconds: Long?,
    animationMemory: DoorAnimationMemory,
    armed: Boolean,
    holdProgress: Float,
    onDoorTap: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ringColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant
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
        // Radial hold-to-confirm indicator, circumscribing the door.
        Canvas(modifier = Modifier.fillMaxSize()) {
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
        WearGarageIcon(
            doorPosition = doorPosition,
            animationMemory = animationMemory,
            lastChangeTimeSeconds = lastChangeTimeSeconds,
            color = WearDoorColors.forPosition(doorPosition),
            modifier = Modifier.fillMaxSize(DOOR_INSIDE_RING_FRACTION),
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
            onDoorTap = {},
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
            authState = AuthState.Unauthenticated,
            buttonState = RemoteButtonState.Ready,
            isHolding = false,
            onDoorTap = {},
            onHoldStart = {},
            onHoldEnd = {},
            onSignInClick = {},
        )
    }
}

private const val DOOR_WIDTH_FRACTION = 0.62f
private const val DOOR_INSIDE_RING_FRACTION = 0.78f
private const val RING_STROKE_DP = 5
private const val RING_RELEASE_MILLIS = 150
private const val ARC_START_ANGLE = -90f
private const val FULL_SWEEP = 360f
private const val TRACK_ALPHA = 0.25f
