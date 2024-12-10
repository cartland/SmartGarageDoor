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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chriscartland.garage.remotebutton.RequestStatus
import com.chriscartland.garage.ui.theme.DoorColorSet
import com.chriscartland.garage.ui.theme.LocalDoorStatusColorScheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import java.time.Duration

enum class RemoteButtonState {
    READY, // Default state. Button press moves to ARMING state.
    ARMING, // Force a pause before sending the request. Then go to ARMED.
    ARMED, // One more press will trigger action. No action moves to TIMEOUT state.
    TIMEOUT, // Disable action. Move to READY state after a brief delay.
    COOLDOWN, // After button is pressed. Move to READY state after 10 seconds.
}

@Composable
fun RemoteButtonContent(
    modifier: Modifier = Modifier,
    onSubmit: () -> Unit,
    onArming: () -> Unit = {},
    remoteRequestStatus: RequestStatus = RequestStatus.NONE,
) {
    val colorSet = LocalDoorStatusColorScheme.current.DoorColorSet(isStale = false)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val coroutineScope = rememberCoroutineScope()
        var buttonState by remember { mutableStateOf(RemoteButtonState.READY) }
        var countdown by remember { mutableIntStateOf(10) }
        var job: Job? by remember { mutableStateOf(null) }

        // Track the width of the button so the indicator can be sized correctly.
        var buttonWidth by remember { mutableStateOf(0.dp) }
        val localDensity = LocalDensity.current
        Button(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f)
                .widthIn(max = 256.dp)
                .onSizeChanged { size ->
                    buttonWidth = with(localDensity) { size.width.toDp() }
                }
                .shadow(4.dp, CircleShape)
                .fadeBottom(0.4f, MaterialTheme.colorScheme.background),
            enabled = when (buttonState) {
                RemoteButtonState.READY -> true
                RemoteButtonState.ARMING -> false
                RemoteButtonState.ARMED -> true
                RemoteButtonState.TIMEOUT -> false
                RemoteButtonState.COOLDOWN -> false
            },
            onClick = {
                when (buttonState) {
                    RemoteButtonState.READY -> {
                        // READY -> ARMED
                        buttonState = RemoteButtonState.ARMING
                        onArming()
                        // Wait a few seconds before ARMED.
                        // If the user does not press the button again within a few seconds,
                        // move to TIMEOUT state.
                        // After the timeout, delay a few seconds, then move to READY state.
                        job?.cancel()
                        job = coroutineScope.launch {
                            countdown = 1
                            while (countdown > 0) {
                                delay(Duration.ofMillis(500))
                                countdown--
                            }
                            buttonState = RemoteButtonState.ARMED
                            delay(Duration.ofSeconds(5))
                            buttonState = RemoteButtonState.TIMEOUT
                            countdown = 3
                            while (countdown > 0) {
                                delay(Duration.ofSeconds(1))
                                countdown--
                            }
                            buttonState = RemoteButtonState.READY
                        }
                    }

                    RemoteButtonState.ARMING -> {} // Do nothing.
                    RemoteButtonState.ARMED -> {
                        // ARMED -> COOLDOWN
                        buttonState = RemoteButtonState.COOLDOWN
                        // Submit the button press, wait 10 seconds, then move to READY state.
                        job?.cancel()
                        job = coroutineScope.launch {
                            onSubmit()
                            countdown = 10
                            while (countdown > 0) {
                                delay(Duration.ofSeconds(1))
                                countdown--
                            }
                            buttonState = RemoteButtonState.READY
                        }
                    }

                    RemoteButtonState.TIMEOUT -> {} // Do nothing.
                    RemoteButtonState.COOLDOWN -> {} // Do nothing.
                }
            },
            shape = CircleShape,
            colors = ButtonColors(
                containerColor = colorSet.closedContainer,
                contentColor = colorSet.closedOnContainer,
                disabledContainerColor = colorSet.openContainer,
                disabledContentColor = colorSet.openOnContainer,
            ),
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(
                text = when (buttonState) {
                    RemoteButtonState.READY -> "Garage\nRemote\nButton"
                    RemoteButtonState.ARMING -> "Preparing..."
                    RemoteButtonState.ARMED -> "Tap Again\nTo Send Command"
                    RemoteButtonState.TIMEOUT -> "Button not pressed"
                    RemoteButtonState.COOLDOWN -> "Button pushed!"
                },
                fontSize = MaterialTheme.typography.titleLarge.fontSize,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        ButtonRequestIndicator(
            modifier = Modifier
                .width(buttonWidth)
                .drawWithContent {
                    // Only draw this indicator if the button is armed or we are sending a request.
                    // We are skipping the "draw" phase in Compose so that it still takes space.
                    // We want to avoid the UI jumping around when this becomes visible / invisible.
                    if (buttonState == RemoteButtonState.ARMED || remoteRequestStatus != RequestStatus.NONE) {
                        drawContent()
                    }
                },
            remoteRequestStatus = remoteRequestStatus,
            indicatorHeight = 10.dp,
        )
    }
}

data class RemoteIndicator(
    val text: String,
    val complete: Int,
    val failure: Boolean = false,
)

@Composable
fun ButtonRequestIndicator(
    modifier: Modifier = Modifier,
    remoteRequestStatus: RequestStatus = RequestStatus.NONE,
    indicatorHeight: Dp = 10.dp,
) {
    val progress = when (remoteRequestStatus) {
        RequestStatus.NONE -> RemoteIndicator("Ready", 0)
        RequestStatus.SENDING -> RemoteIndicator("Sending", 1)
        RequestStatus.SENDING_TIMEOUT -> RemoteIndicator("Sending failed", 2)
        RequestStatus.SENT -> RemoteIndicator("Sent", 3)
        RequestStatus.SENT_TIMEOUT -> RemoteIndicator("Command not delivered", 4, failure = true)
        RequestStatus.RECEIVED -> RemoteIndicator("Door moved", 5)
    }
    val colorComplete: Color = if (progress.failure) Color(0xFFFF3333) else Color(0xFF3333FF)
    Column(
        modifier = modifier,
    ) {
        ParallelogramProgressBar(
            max = 5,
            complete = progress.complete,
            colorComplete = colorComplete,
            height = indicatorHeight,
        )
        Text(text = progress.text, textAlign = TextAlign.Center)
    }
}

@Preview(showBackground = true)
@Composable
fun RemoteButtonContentPreview() {
    RemoteButtonContent(
        onSubmit = {},
    )
}
