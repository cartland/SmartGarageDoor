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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
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
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val tappable = state.isTappable()
        val color = if (state == RemoteButtonState.Armed) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceDim
        }
        val onColor = if (state == RemoteButtonState.Armed) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        }
        GradientButton(
            modifier = Modifier
                .widthIn(max = 192.dp)
                .aspectRatio(1f)
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
        Spacer(modifier = Modifier.height(8.dp))
        ButtonProgressIndicator(
            state = state,
            indicatorHeight = 10.dp,
        )
    }
}

private fun RemoteButtonState.isTappable(): Boolean =
    when (this) {
        RemoteButtonState.Ready, RemoteButtonState.Armed -> true
        else -> false
    }

private fun RemoteButtonState.buttonLabel(): String =
    when (this) {
        RemoteButtonState.Ready -> "Garage\nButton"
        RemoteButtonState.Arming -> "Preparing..."
        RemoteButtonState.Armed -> "Tap Again\nTo Confirm"
        RemoteButtonState.NotConfirmed -> "Not Pressed"
        RemoteButtonState.Sending -> "Sending..."
        RemoteButtonState.Sent -> "Sent"
        RemoteButtonState.Received -> "Door Moved!"
        RemoteButtonState.SendingTimeout -> "Send Timeout"
        RemoteButtonState.SentTimeout -> "No Response"
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
            max = 5,
            complete = data.complete,
            colorComplete = colorComplete,
            height = indicatorHeight,
        )
        Text(text = data.text, textAlign = TextAlign.Center)
    }
}

private fun RemoteButtonState.toProgressData(): ProgressIndicatorData =
    when (this) {
        RemoteButtonState.Ready -> ProgressIndicatorData("Ready", 0)
        RemoteButtonState.Arming -> ProgressIndicatorData("Arming", 0)
        RemoteButtonState.Armed -> ProgressIndicatorData("Tap to confirm", 0)
        RemoteButtonState.NotConfirmed -> ProgressIndicatorData("Not confirmed", 0, failure = true)
        RemoteButtonState.Sending -> ProgressIndicatorData("Sending", 1)
        RemoteButtonState.Sent -> ProgressIndicatorData("Sent", 3)
        RemoteButtonState.Received -> ProgressIndicatorData("Door moved", 5)
        RemoteButtonState.SendingTimeout -> ProgressIndicatorData("Sending failed", 2, failure = true)
        RemoteButtonState.SentTimeout -> ProgressIndicatorData("Command not delivered", 4, failure = true)
    }

@Preview(showBackground = true)
@Composable
fun RemoteButtonContentPreview() {
    RemoteButtonContent(state = RemoteButtonState.Ready, onTap = {})
}

@Preview(showBackground = true)
@Composable
fun RemoteButtonContentArmingPreview() {
    RemoteButtonContent(state = RemoteButtonState.Arming, onTap = {})
}

@Preview(showBackground = true)
@Composable
fun RemoteButtonContentArmedPreview() {
    RemoteButtonContent(state = RemoteButtonState.Armed, onTap = {})
}

@Preview(showBackground = true)
@Composable
fun RemoteButtonContentNotConfirmedPreview() {
    RemoteButtonContent(state = RemoteButtonState.NotConfirmed, onTap = {})
}

@Preview(showBackground = true)
@Composable
fun RemoteButtonContentSendingPreview() {
    RemoteButtonContent(state = RemoteButtonState.Sending, onTap = {})
}

@Preview(showBackground = true)
@Composable
fun RemoteButtonContentSentPreview() {
    RemoteButtonContent(state = RemoteButtonState.Sent, onTap = {})
}

@Preview(showBackground = true)
@Composable
fun RemoteButtonContentReceivedPreview() {
    RemoteButtonContent(state = RemoteButtonState.Received, onTap = {})
}

@Preview(showBackground = true)
@Composable
fun RemoteButtonContentSendingTimeoutPreview() {
    RemoteButtonContent(state = RemoteButtonState.SendingTimeout, onTap = {})
}

@Preview(showBackground = true)
@Composable
fun RemoteButtonContentSentTimeoutPreview() {
    RemoteButtonContent(state = RemoteButtonState.SentTimeout, onTap = {})
}
