/*
 * Copyright 2024 Chris Cartland. All rights reserved.
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

package com.chriscartland.garage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chriscartland.garage.domain.model.RemoteButtonState

/**
 * Stateless renderer for the remote garage button.
 *
 * All logic (tap-to-confirm, timeouts, network coordination) lives in
 * [com.chriscartland.garage.usecase.ButtonStateMachine]. This composable
 * just renders the [state] and forwards taps via [onTap].
 */
@Composable
fun RemoteButtonContent(
    state: RemoteButtonState,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tappable = state.isTappable()
    val color = if (state == RemoteButtonState.AwaitingConfirmation) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceDim
    }
    val onColor = if (state == RemoteButtonState.AwaitingConfirmation) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    SquareButtonWithProgress(
        modifier = modifier,
        button = {
            GradientButton(
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(4.dp, CircleShape),
                enabled = tappable,
                onClick = onTap,
                shape = CircleShape,
                contentColor = onColor,
                colorStops = arrayOf(
                    0.5f to color,
                    1.0f to blendColors(color, Color.Black, 0.5f),
                ),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    text = state.buttonLabel(),
                    fontSize = MaterialTheme.typography.titleLarge.fontSize,
                    textAlign = TextAlign.Center,
                )
            }
        },
        progress = {
            ButtonProgressIndicator(
                state = state,
                indicatorHeight = 10.dp,
            )
        },
    )
}

/**
 * Custom layout that places a circular [button] above a [progress] indicator,
 * where the button is a perfect square sized to fill the available space and
 * the progress indicator matches the button's width exactly.
 *
 * Layout algorithm:
 * 1. Measure [progress] at its intrinsic height (full available width).
 * 2. Compute button side = min(maxWidth, maxHeight − progressHeight − spacing, maxButtonSize).
 * 3. Re-measure [button] at exactly Constraints.fixed(buttonSide, buttonSide).
 * 4. Re-measure [progress] at width = buttonSide.
 * 5. Stack them vertically, centered horizontally inside the layout.
 *
 * Single measurement pass per child (button measured once, progress measured twice
 * because its height is variable). No subcomposition. No view hopping on first frame.
 * Falls back to [maxButtonSize] when constraints are unbounded (e.g. inside vertical scroll).
 */
@Composable
fun SquareButtonWithProgress(
    button: @Composable () -> Unit,
    progress: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    maxButtonSize: Dp = 192.dp,
) {
    Layout(
        contents = listOf(button, progress),
        modifier = modifier,
    ) { (buttonMeasurables, progressMeasurables), constraints ->
        val spacingPx = spacing.roundToPx()
        val maxButtonPx = maxButtonSize.roundToPx()

        // Compute the candidate button side based on incoming constraints, before
        // measuring anything. This bounds the progress measurement to avoid passing
        // unbounded width into a child Column (which would collapse rendering).
        val widthBound = if (constraints.hasBoundedWidth) constraints.maxWidth else maxButtonPx
        val candidateButtonSide = minOf(widthBound, maxButtonPx).coerceAtLeast(0)

        // Step 1: measure progress at the candidate width to learn its natural height.
        val tempProgress = progressMeasurables.first().measure(
            Constraints(
                minWidth = candidateButtonSide,
                maxWidth = candidateButtonSide,
                minHeight = 0,
                maxHeight = Constraints.Infinity,
            ),
        )
        val progressHeight = tempProgress.height

        // Step 2: refine button side using the now-known progress height.
        val maxFromHeight = if (constraints.hasBoundedHeight) {
            constraints.maxHeight - progressHeight - spacingPx
        } else {
            maxButtonPx
        }
        val buttonSide = minOf(widthBound, maxFromHeight, maxButtonPx).coerceAtLeast(0)

        // Step 3: measure button as a fixed square.
        val buttonPlaceable = buttonMeasurables.first().measure(
            Constraints.fixed(buttonSide, buttonSide),
        )

        // Step 4: re-measure progress at exactly the button's width (in case the
        // candidate differed from the final button side due to height constraints).
        val progressPlaceable = if (buttonSide == candidateButtonSide) {
            tempProgress
        } else {
            progressMeasurables.first().measure(
                Constraints.fixed(buttonSide, progressHeight),
            )
        }

        val totalHeight = buttonSide + spacingPx + progressPlaceable.height

        // Center horizontally within the available width.
        val parentWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else buttonSide
        val xOffset = ((parentWidth - buttonSide) / 2).coerceAtLeast(0)

        layout(parentWidth, totalHeight) {
            buttonPlaceable.placeRelative(xOffset, 0)
            progressPlaceable.placeRelative(xOffset, buttonSide + spacingPx)
        }
    }
}

private fun RemoteButtonState.isTappable(): Boolean =
    when (this) {
        RemoteButtonState.Ready, RemoteButtonState.AwaitingConfirmation -> true
        else -> false
    }

private fun RemoteButtonState.buttonLabel(): String =
    when (this) {
        RemoteButtonState.Ready -> "Garage\nButton"
        RemoteButtonState.Preparing -> "Preparing..."
        RemoteButtonState.AwaitingConfirmation -> "Tap Again\nTo Confirm"
        RemoteButtonState.Cancelled -> "Not Pressed"
        RemoteButtonState.SendingToServer -> "Sending..."
        RemoteButtonState.SendingToDoor -> "Sent"
        RemoteButtonState.Succeeded -> "Door Moved!"
        RemoteButtonState.ServerFailed -> "Send Timeout"
        RemoteButtonState.DoorFailed -> "No Response"
    }

@Composable
fun GradientButton(
    vararg colorStops: Pair<Float, Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    contentColor: Color? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable () -> Unit,
) {
    val gradient = Brush.verticalGradient(*colorStops)
    Surface(
        onClick = {
            if (enabled) {
                onClick()
            }
        },
        contentColor = contentColor ?: LocalContentColor.current,
        modifier = modifier,
        shape = shape,
        color = Color.Transparent,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .background(gradient)
                .padding(contentPadding),
        ) {
            content()
        }
    }
}

@Preview
@Composable
fun GradientButtonPreview() {
    GradientButton(
        0.0f to Color.Red,
        0.5f to blendColors(Color.Red, Color.Yellow, 0.5f),
        onClick = {},
        modifier = Modifier,
    ) {
        Text("Click me!")
    }
}

private data class ProgressIndicatorData(
    val text: String,
    val complete: Int,
    val failure: Boolean = false,
)

@Composable
fun ButtonProgressIndicator(
    state: RemoteButtonState,
    modifier: Modifier = Modifier,
    indicatorHeight: Dp = 10.dp,
) {
    val data = state.toProgressData()
    val colorComplete: Color = if (data.failure) Color(0xFFFF3333) else Color(0xFF3333FF)
    Column(
        modifier = modifier,
    ) {
        ParallelogramProgressBar(
            modifier = Modifier.fillMaxWidth(),
            max = 5,
            complete = data.complete,
            colorComplete = colorComplete,
            height = indicatorHeight,
        )
        Text(
            text = data.text,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

private fun RemoteButtonState.toProgressData(): ProgressIndicatorData =
    when (this) {
        RemoteButtonState.Ready -> ProgressIndicatorData("Ready", 0)
        RemoteButtonState.Preparing -> ProgressIndicatorData("Arming", 0)
        RemoteButtonState.AwaitingConfirmation -> ProgressIndicatorData("Tap to confirm", 0)
        RemoteButtonState.Cancelled -> ProgressIndicatorData("Not confirmed", 0, failure = true)
        RemoteButtonState.SendingToServer -> ProgressIndicatorData("Sending", 1)
        RemoteButtonState.SendingToDoor -> ProgressIndicatorData("Sent", 3)
        RemoteButtonState.Succeeded -> ProgressIndicatorData("Door moved", 5)
        RemoteButtonState.ServerFailed -> ProgressIndicatorData("Sending failed", 2, failure = true)
        RemoteButtonState.DoorFailed -> ProgressIndicatorData("Command not delivered", 4, failure = true)
    }

@Preview(showBackground = true)
@Composable
fun RemoteButtonContentPreview() {
    RemoteButtonContent(state = RemoteButtonState.Ready, onTap = {})
}

@Preview(showBackground = true)
@Composable
fun RemoteButtonContentPreparingPreview() {
    RemoteButtonContent(state = RemoteButtonState.Preparing, onTap = {})
}

@Preview(showBackground = true)
@Composable
fun RemoteButtonContentAwaitingConfirmationPreview() {
    RemoteButtonContent(state = RemoteButtonState.AwaitingConfirmation, onTap = {})
}

@Preview(showBackground = true)
@Composable
fun RemoteButtonContentCancelledPreview() {
    RemoteButtonContent(state = RemoteButtonState.Cancelled, onTap = {})
}

@Preview(showBackground = true)
@Composable
fun RemoteButtonContentSendingToServerPreview() {
    RemoteButtonContent(state = RemoteButtonState.SendingToServer, onTap = {})
}

@Preview(showBackground = true)
@Composable
fun RemoteButtonContentSendingToDoorPreview() {
    RemoteButtonContent(state = RemoteButtonState.SendingToDoor, onTap = {})
}

@Preview(showBackground = true)
@Composable
fun RemoteButtonContentSucceededPreview() {
    RemoteButtonContent(state = RemoteButtonState.Succeeded, onTap = {})
}

@Preview(showBackground = true)
@Composable
fun RemoteButtonContentServerFailedPreview() {
    RemoteButtonContent(state = RemoteButtonState.ServerFailed, onTap = {})
}

@Preview(showBackground = true)
@Composable
fun RemoteButtonContentDoorFailedPreview() {
    RemoteButtonContent(state = RemoteButtonState.DoorFailed, onTap = {})
}
