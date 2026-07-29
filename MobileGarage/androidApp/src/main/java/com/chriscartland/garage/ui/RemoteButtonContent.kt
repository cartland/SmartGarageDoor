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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chriscartland.garage.R
import com.chriscartland.garage.domain.model.RemoteButtonState
import com.chriscartland.garage.ui.theme.PreviewComponentSurface

private val GARAGE_DIAGRAM_ICONS = listOf(
    Icons.Filled.PhoneAndroid,
    Icons.Filled.Cloud,
    Icons.Filled.Home,
)

/**
 * Stateless renderer for the remote garage button.
 *
 * Combines [GarageDoorButton] (Material3) with [NetworkProgressDiagram]
 * (phone → server → door). All logic (tap-to-confirm, timeouts, network
 * coordination) lives in [com.chriscartland.garage.usecase.ButtonStateMachine].
 * This composable just renders the [state] and forwards taps via [onTap].
 */
@Composable
fun RemoteButtonContent(
    state: RemoteButtonState,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GarageDoorButton(
            state = state,
            onTap = onTap,
            modifier = Modifier.fillMaxWidth(),
        )
        // The diagram is the only thing that says *where* a send is: at the
        // server, at the door, or stalled. Its icons are individually
        // decorative, so without a description on the group a screen-reader
        // user gets the button label and nothing else. Collapsed into one node
        // announcing the phase, mirroring iOS's `RemoteProgressDiagram`.
        val phaseDescription = state.phaseDescription()
        NetworkProgressDiagram(
            state = state.toNetworkDiagramState(),
            icons = GARAGE_DIAGRAM_ICONS,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .then(
                    if (phaseDescription == null) {
                        Modifier
                    } else {
                        Modifier.semantics(mergeDescendants = true) {
                            contentDescription = phaseDescription
                        }
                    },
                ),
        )
    }
}

/**
 * Spoken description of what the diagram is currently showing, or null while
 * the button is idle (nothing has been sent, so there is no progress to
 * report). Wording mirrors iOS's phase labels.
 */
@Composable
private fun RemoteButtonState.phaseDescription(): String? =
    when (this) {
        RemoteButtonState.SendingToServer -> stringResource(R.string.remote_diagram_sending_to_server)
        RemoteButtonState.SendingToDoor -> stringResource(R.string.remote_diagram_sending_to_door)
        RemoteButtonState.Succeeded -> stringResource(R.string.remote_diagram_succeeded)
        RemoteButtonState.ServerFailed -> stringResource(R.string.remote_diagram_server_failed)
        RemoteButtonState.DoorFailed -> stringResource(R.string.remote_diagram_door_failed)
        RemoteButtonState.AwaitingConfirmation -> stringResource(R.string.remote_diagram_armed)
        RemoteButtonState.Ready,
        RemoteButtonState.Preparing,
        RemoteButtonState.Cancelled,
        -> null
    }

// region Previews

@Preview
@Composable
fun RemoteButtonContentPreview() {
    PreviewComponentSurface { RemoteButtonContent(state = RemoteButtonState.Ready, onTap = {}) }
}

@Preview
@Composable
fun RemoteButtonContentPreparingPreview() {
    PreviewComponentSurface { RemoteButtonContent(state = RemoteButtonState.Preparing, onTap = {}) }
}

@Preview
@Composable
fun RemoteButtonContentAwaitingConfirmationPreview() {
    PreviewComponentSurface { RemoteButtonContent(state = RemoteButtonState.AwaitingConfirmation, onTap = {}) }
}

@Preview
@Composable
fun RemoteButtonContentCancelledPreview() {
    PreviewComponentSurface { RemoteButtonContent(state = RemoteButtonState.Cancelled, onTap = {}) }
}

@Preview
@Composable
fun RemoteButtonContentSendingToServerPreview() {
    PreviewComponentSurface { RemoteButtonContent(state = RemoteButtonState.SendingToServer, onTap = {}) }
}

@Preview
@Composable
fun RemoteButtonContentSendingToDoorPreview() {
    PreviewComponentSurface { RemoteButtonContent(state = RemoteButtonState.SendingToDoor, onTap = {}) }
}

@Preview
@Composable
fun RemoteButtonContentSucceededPreview() {
    PreviewComponentSurface { RemoteButtonContent(state = RemoteButtonState.Succeeded, onTap = {}) }
}

@Preview
@Composable
fun RemoteButtonContentServerFailedPreview() {
    PreviewComponentSurface { RemoteButtonContent(state = RemoteButtonState.ServerFailed, onTap = {}) }
}

@Preview
@Composable
fun RemoteButtonContentDoorFailedPreview() {
    PreviewComponentSurface { RemoteButtonContent(state = RemoteButtonState.DoorFailed, onTap = {}) }
}

// endregion
