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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chriscartland.garage.domain.model.RemoteButtonState
import com.chriscartland.garage.ui.theme.AppTheme
import com.chriscartland.garage.ui.theme.cautionContainer
import com.chriscartland.garage.ui.theme.onCautionContainer

/**
 * Material3 button for the garage door remote.
 *
 * - Ready: "Garage Door Button" (tonal, tappable)
 * - AwaitingConfirmation: "Door will move." + "Confirm?" (amber, tappable)
 * - All other states: disabled with status text
 *
 * The parent controls width via [modifier]. The button fills that width
 * in all states for visual stability.
 */
@Composable
fun GarageDoorButton(
    state: RemoteButtonState,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        RemoteButtonState.Ready -> {
            FilledTonalButton(
                onClick = onTap,
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
            ) {
                Text(
                    text = "Garage Door Button",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
        RemoteButtonState.AwaitingConfirmation -> {
            Button(
                onClick = onTap,
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = cautionContainer,
                    contentColor = onCautionContainer,
                ),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Door will move.",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Confirm?",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        else -> {
            FilledTonalButton(
                onClick = {},
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
                enabled = false,
                colors = ButtonDefaults.filledTonalButtonColors(
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                ),
            ) {
                Text(
                    text = state.disabledLabel(),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun RemoteButtonState.disabledLabel(): String =
    when (this) {
        RemoteButtonState.Preparing -> "Garage Door Button"
        RemoteButtonState.Cancelled -> "Cancelled"
        RemoteButtonState.SendingToServer -> "Sending..."
        RemoteButtonState.SendingToDoor -> "Waiting..."
        RemoteButtonState.Succeeded -> "Done!"
        RemoteButtonState.ServerFailed -> "Failed"
        RemoteButtonState.DoorFailed -> "Failed"
        // Ready and AwaitingConfirmation are handled above, not here.
        RemoteButtonState.Ready,
        RemoteButtonState.AwaitingConfirmation,
        -> ""
    }

// region Previews

@Preview(showBackground = true)
@Composable
fun GarageDoorButtonReadyPreview() {
    AppTheme {
        GarageDoorButton(state = RemoteButtonState.Ready, onTap = {})
    }
}

@Preview(showBackground = true)
@Composable
fun GarageDoorButtonPreparingPreview() {
    AppTheme {
        GarageDoorButton(state = RemoteButtonState.Preparing, onTap = {})
    }
}

@Preview(showBackground = true)
@Composable
fun GarageDoorButtonAwaitingConfirmationPreview() {
    AppTheme {
        GarageDoorButton(state = RemoteButtonState.AwaitingConfirmation, onTap = {})
    }
}

@Preview(showBackground = true)
@Composable
fun GarageDoorButtonCancelledPreview() {
    AppTheme {
        GarageDoorButton(state = RemoteButtonState.Cancelled, onTap = {})
    }
}

@Preview(showBackground = true)
@Composable
fun GarageDoorButtonSendingToServerPreview() {
    AppTheme {
        GarageDoorButton(state = RemoteButtonState.SendingToServer, onTap = {})
    }
}

@Preview(showBackground = true)
@Composable
fun GarageDoorButtonSendingToDoorPreview() {
    AppTheme {
        GarageDoorButton(state = RemoteButtonState.SendingToDoor, onTap = {})
    }
}

@Preview(showBackground = true)
@Composable
fun GarageDoorButtonSucceededPreview() {
    AppTheme {
        GarageDoorButton(state = RemoteButtonState.Succeeded, onTap = {})
    }
}

@Preview(showBackground = true)
@Composable
fun GarageDoorButtonServerFailedPreview() {
    AppTheme {
        GarageDoorButton(state = RemoteButtonState.ServerFailed, onTap = {})
    }
}

@Preview(showBackground = true)
@Composable
fun GarageDoorButtonDoorFailedPreview() {
    AppTheme {
        GarageDoorButton(state = RemoteButtonState.DoorFailed, onTap = {})
    }
}

// endregion
