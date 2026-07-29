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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chriscartland.garage.R
import com.chriscartland.garage.domain.model.RemoteButtonState
import com.chriscartland.garage.ui.theme.PreviewComponentSurface
import com.chriscartland.garage.ui.theme.cautionContainer
import com.chriscartland.garage.ui.theme.onCautionContainer

/**
 * Material3 button for the garage door remote.
 *
 * - Ready: "Tap to open or close" (tonal, tappable)
 * - AwaitingConfirmation: "Door will move." + "Tap again to confirm" (amber, tappable)
 * - All other states: disabled with status text; the two in-flight states also
 *   show a spinner, per the in-flight button pattern (icon swapped for a 20 dp
 *   indicator while the action is disabled).
 *
 * The labels are instructions, not nouns: "Tap to open or close" says what the
 * button does, where "Garage Door Button" only repeated where the user already
 * knew they were. The two failure states are named separately — the shared
 * [RemoteButtonState] distinguishes "the server refused" from "the door never
 * moved", and those call for different reactions, so collapsing both to
 * "Failed" throws away the only information the user has. Copy is shared with
 * iOS, which arrived at these first.
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
                    text = stringResource(R.string.remote_button_ready),
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
                        text = stringResource(R.string.remote_button_confirm_title),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.remote_button_confirm_subtitle),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Only the two states that are genuinely waiting on the
                    // network spin. Cancelled / Succeeded / Failed are outcomes,
                    // and a spinner on an outcome reads as "still working".
                    if (state == RemoteButtonState.SendingToServer ||
                        state == RemoteButtonState.SendingToDoor
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                    Text(
                        text = state.disabledLabel(),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun RemoteButtonState.disabledLabel(): String =
    when (this) {
        RemoteButtonState.Preparing -> stringResource(R.string.remote_button_preparing)
        RemoteButtonState.Cancelled -> stringResource(R.string.remote_button_cancelled)
        RemoteButtonState.SendingToServer -> stringResource(R.string.remote_button_sending)
        RemoteButtonState.SendingToDoor -> stringResource(R.string.remote_button_waiting)
        RemoteButtonState.Succeeded -> stringResource(R.string.remote_button_succeeded)
        RemoteButtonState.ServerFailed -> stringResource(R.string.remote_button_server_failed)
        RemoteButtonState.DoorFailed -> stringResource(R.string.remote_button_door_failed)
        // Ready and AwaitingConfirmation are handled above, not here.
        RemoteButtonState.Ready,
        RemoteButtonState.AwaitingConfirmation,
        -> ""
    }

// region Previews

@Preview
@Composable
fun GarageDoorButtonReadyPreview() {
    PreviewComponentSurface {
        GarageDoorButton(state = RemoteButtonState.Ready, onTap = {})
    }
}

@Preview
@Composable
fun GarageDoorButtonPreparingPreview() {
    PreviewComponentSurface {
        GarageDoorButton(state = RemoteButtonState.Preparing, onTap = {})
    }
}

@Preview
@Composable
fun GarageDoorButtonAwaitingConfirmationPreview() {
    PreviewComponentSurface {
        GarageDoorButton(state = RemoteButtonState.AwaitingConfirmation, onTap = {})
    }
}

@Preview
@Composable
fun GarageDoorButtonCancelledPreview() {
    PreviewComponentSurface {
        GarageDoorButton(state = RemoteButtonState.Cancelled, onTap = {})
    }
}

@Preview
@Composable
fun GarageDoorButtonSendingToServerPreview() {
    PreviewComponentSurface {
        GarageDoorButton(state = RemoteButtonState.SendingToServer, onTap = {})
    }
}

@Preview
@Composable
fun GarageDoorButtonSendingToDoorPreview() {
    PreviewComponentSurface {
        GarageDoorButton(state = RemoteButtonState.SendingToDoor, onTap = {})
    }
}

@Preview
@Composable
fun GarageDoorButtonSucceededPreview() {
    PreviewComponentSurface {
        GarageDoorButton(state = RemoteButtonState.Succeeded, onTap = {})
    }
}

@Preview
@Composable
fun GarageDoorButtonServerFailedPreview() {
    PreviewComponentSurface {
        GarageDoorButton(state = RemoteButtonState.ServerFailed, onTap = {})
    }
}

@Preview
@Composable
fun GarageDoorButtonDoorFailedPreview() {
    PreviewComponentSurface {
        GarageDoorButton(state = RemoteButtonState.DoorFailed, onTap = {})
    }
}

// endregion
