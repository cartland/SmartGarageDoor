package com.chriscartland.garage.ui

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
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
    val user = viewModel.user.collectAsState()
    HomeContent(
        currentDoorEvent = currentDoorEvent.value,
        remoteButtonRequestStatus = remoteButtonRequestStatus.value,
        modifier = modifier,
        onFetchCurrentDoorEvent = { viewModel.fetchCurrentDoorEvent() },
        onRemoteButtonClick = { viewModel.pushRemoteButton(context) },
        onClearRemote = { viewModel.clearRemoteButton() },
        isSignedIn = user.value != null,
        onSignIn = { viewModel.signInSeamlessly(context) },
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
    onClearRemote: () -> Unit = {},
    isSignedIn: Boolean = false,
    onSignIn: () -> Unit = {},
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
        if (isSignedIn) {
            item {
                RemoteButtonContent(
                    onSubmit = {
                        Log.d("HomeContent", "Remote button clicked")
                        onRemoteButtonClick()
                    },
                    onArming = {
                        onClearRemote()
                    },
                    buttonColors = doorButtonColors(LocalDoorStatusColorScheme.current, doorEvent),
                )
            }
            item {
                ButtonRequestIndicator(remoteButtonRequestStatus = remoteButtonRequestStatus)
            }
        } else {
            item {
                Button(onClick = { onSignIn() }) {
                    Text("Sign to access garage remote button")
                }
            }
        }
    }
}

@Composable
fun ButtonRequestIndicator(
    modifier: Modifier = Modifier,
    remoteButtonRequestStatus: RemoteButtonRequestStatus = RemoteButtonRequestStatus.NONE,
) {
    val phone = "📱"
    val cloud = "☁️"
    val house = "🏠"
    val hyphen = "➖"
    val rightArrow = "➡️"
    val x = "❌"

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val boxModifier = Modifier
            .weight(1f)
            .padding(0.dp)
            .border(1.dp, Color.Gray)
        val spacerModifier = Modifier.width(1.dp)
        val colorEmpty = MaterialTheme.colorScheme.secondaryContainer
        val colorActive = MaterialTheme.colorScheme.tertiaryContainer
        val colorComplete = LocalDoorStatusColorScheme.current.doorClosedContainerFresh
        val colorError = MaterialTheme.colorScheme.errorContainer
        // Phone.
        Box(
            modifier = boxModifier.background(when (remoteButtonRequestStatus) {
                RemoteButtonRequestStatus.NONE -> colorEmpty
                RemoteButtonRequestStatus.SENDING -> colorComplete
                RemoteButtonRequestStatus.SENDING_TIMEOUT -> colorComplete
                RemoteButtonRequestStatus.SENT -> colorComplete
                RemoteButtonRequestStatus.SENT_TIMEOUT -> colorComplete
                RemoteButtonRequestStatus.RECEIVED -> colorComplete
            })
        ) {
            Text(
                phone,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            )
        }
        Spacer(modifier = spacerModifier)
        // Phone -> Cloud.
        Box(
            modifier = boxModifier.background(when (remoteButtonRequestStatus) {
                RemoteButtonRequestStatus.NONE -> colorEmpty
                RemoteButtonRequestStatus.SENDING -> colorActive
                RemoteButtonRequestStatus.SENDING_TIMEOUT -> colorError
                RemoteButtonRequestStatus.SENT -> colorComplete
                RemoteButtonRequestStatus.SENT_TIMEOUT -> colorComplete
                RemoteButtonRequestStatus.RECEIVED -> colorComplete
            })
        ) {
            Text(
                rightArrow,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            )
        }
        Spacer(modifier = spacerModifier)
        // Cloud.
        Box(
            modifier = boxModifier.background(when (remoteButtonRequestStatus) {
                RemoteButtonRequestStatus.NONE -> colorEmpty
                RemoteButtonRequestStatus.SENDING -> colorEmpty
                RemoteButtonRequestStatus.SENDING_TIMEOUT -> colorEmpty
                RemoteButtonRequestStatus.SENT -> colorComplete
                RemoteButtonRequestStatus.SENT_TIMEOUT -> colorComplete
                RemoteButtonRequestStatus.RECEIVED -> colorComplete
            })
        ) {
            Text(cloud, textAlign = TextAlign.Center, modifier = Modifier
                .fillMaxSize()
                .padding(8.dp))
        }
        Spacer(modifier = spacerModifier)
        // Cloud -> Home.
        Box(
            modifier = boxModifier.background(when (remoteButtonRequestStatus) {
                RemoteButtonRequestStatus.NONE -> colorEmpty
                RemoteButtonRequestStatus.SENDING -> colorEmpty
                RemoteButtonRequestStatus.SENDING_TIMEOUT -> colorEmpty
                RemoteButtonRequestStatus.SENT -> colorActive
                RemoteButtonRequestStatus.SENT_TIMEOUT -> colorError
                RemoteButtonRequestStatus.RECEIVED -> colorComplete
            })
        ) {
            Text(
                rightArrow,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            )
        }
        Spacer(modifier = spacerModifier)
        // Home.
        Box(
            modifier = boxModifier.background(when (remoteButtonRequestStatus) {
                RemoteButtonRequestStatus.NONE -> colorEmpty
                RemoteButtonRequestStatus.SENDING -> colorEmpty
                RemoteButtonRequestStatus.SENDING_TIMEOUT -> colorEmpty
                RemoteButtonRequestStatus.SENT -> colorEmpty
                RemoteButtonRequestStatus.SENT_TIMEOUT -> colorEmpty
                RemoteButtonRequestStatus.RECEIVED -> colorComplete
            })
        ) {
            Text(house, textAlign = TextAlign.Center, modifier = Modifier
                .fillMaxSize()
                .padding(8.dp))
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
