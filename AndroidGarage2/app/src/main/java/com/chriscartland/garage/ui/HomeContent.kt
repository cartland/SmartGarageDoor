package com.chriscartland.garage.ui

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chriscartland.garage.model.DoorEvent
import com.chriscartland.garage.model.Result
import com.chriscartland.garage.model.dataOrNull
import com.chriscartland.garage.ui.theme.LocalDoorStatusColorScheme
import com.chriscartland.garage.ui.theme.doorButtonColors
import com.chriscartland.garage.ui.theme.doorCardColors
import com.chriscartland.garage.viewmodel.DoorViewModel
import com.chriscartland.garage.viewmodel.RemoteButtonRequestStatus
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted

@Composable
fun HomeContent(
    modifier: Modifier = Modifier,
    viewModel: DoorViewModel = hiltViewModel(),
) {
    val context = LocalContext.current as ComponentActivity
    val currentDoorEvent = viewModel.currentDoorEvent.collectAsState()
    val remoteButtonRequestStatus = viewModel.remoteButtonRequestStatus.collectAsState()
    HomeContent(
        currentDoorEvent = currentDoorEvent.value,
        remoteButtonRequestStatus = remoteButtonRequestStatus.value,
        modifier = modifier,
        onFetchCurrentDoorEvent = { viewModel.fetchCurrentDoorEvent() },
        onRemoteButtonClick = { viewModel.pushRemoteButton(context) },
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeContent(
    currentDoorEvent: Result<DoorEvent?>,
    modifier: Modifier = Modifier,
    remoteButtonRequestStatus: RemoteButtonRequestStatus = RemoteButtonRequestStatus.NONE,
    onFetchCurrentDoorEvent: () -> Unit = {},
    onRemoteButtonClick: () -> Unit = {},
) {
    // Manage permission state.
    val notificationPermissionState = rememberNotificationPermissionState()
    var permissionRequestCount by remember { mutableIntStateOf(0) }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Add a card at the top if the notification permission is not granted.
        if (!notificationPermissionState.status.isGranted) {
            item {
                ErrorRequestCard(
                    text = notificationJustificationText(permissionRequestCount),
                    buttonText = "Allow",
                    onClick = {
                        permissionRequestCount++
                        notificationPermissionState.launchPermissionRequest()
                    },
                )
            }
        }

        // If the current event is loading, show a loading indicator.
        if (currentDoorEvent is Result.Loading) {
            item {
                Text(text = "Loading...")
            }
        }
        // If the current event had an error, show an error card.
        if (currentDoorEvent is Result.Error) {
            item {
                ErrorRequestCard(
                    text = "Error fetching current door event: " +
                            currentDoorEvent.exception.toString().take(500),
                    buttonText = "Retry",
                    onClick = { onFetchCurrentDoorEvent() },
                )
            }
        }

        // Show the current door event.
        val doorEvent = currentDoorEvent.dataOrNull()
        item {
            DoorStatusCard(
                doorEvent = doorEvent,
                modifier = Modifier
                    .clickable { onFetchCurrentDoorEvent() }, // Fetch on click.
                cardColors = doorCardColors(LocalDoorStatusColorScheme.current, doorEvent),
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
        item {
            RemoteButtonContent(
                onSubmit = {
                    Log.d("HomeContent", "Remote button clicked")
                    onRemoteButtonClick()
                },
                buttonColors = doorButtonColors(LocalDoorStatusColorScheme.current, doorEvent),
            )
        }
        item {
            val inProgress = "⏺\uFE0F"
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(when (remoteButtonRequestStatus) {
                        RemoteButtonRequestStatus.NONE -> "Send"
                        RemoteButtonRequestStatus.SENDING -> "Sending"
                        RemoteButtonRequestStatus.SENT -> "Sent"
                        RemoteButtonRequestStatus.RECEIVED -> "Sent"
                        RemoteButtonRequestStatus.SENDING_TIMEOUT -> "Sending"
                        RemoteButtonRequestStatus.SENT_TIMEOUT -> "Sending"
                    })
                    Text(when (remoteButtonRequestStatus) {
                        RemoteButtonRequestStatus.NONE -> "⬜"
                        RemoteButtonRequestStatus.SENDING -> inProgress
                        RemoteButtonRequestStatus.SENT -> "✅"
                        RemoteButtonRequestStatus.RECEIVED -> "✅"
                        RemoteButtonRequestStatus.SENDING_TIMEOUT -> "❌"
                        RemoteButtonRequestStatus.SENT_TIMEOUT -> "✅"
                    })
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(when (remoteButtonRequestStatus) {
                        RemoteButtonRequestStatus.NONE -> "Receive"
                        RemoteButtonRequestStatus.SENDING -> "Receive"
                        RemoteButtonRequestStatus.SENT -> "Receiving"
                        RemoteButtonRequestStatus.RECEIVED -> "Received"
                        RemoteButtonRequestStatus.SENDING_TIMEOUT -> "Receive"
                        RemoteButtonRequestStatus.SENT_TIMEOUT -> "Receiving"
                    })
                    Text(when (remoteButtonRequestStatus) {
                        RemoteButtonRequestStatus.NONE -> "⬜"
                        RemoteButtonRequestStatus.SENDING -> "⬜"
                        RemoteButtonRequestStatus.SENT -> inProgress
                        RemoteButtonRequestStatus.RECEIVED -> "✅"
                        RemoteButtonRequestStatus.SENDING_TIMEOUT -> "⬜"
                        RemoteButtonRequestStatus.SENT_TIMEOUT -> "❌"
                    })
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeContentPreview() {
    HomeContent(
        currentDoorEvent = Result.Complete(demoDoorEvents.firstOrNull()),
    )
}
