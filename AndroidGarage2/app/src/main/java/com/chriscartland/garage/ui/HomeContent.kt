package com.chriscartland.garage.ui

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
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
import com.chriscartland.garage.auth.AuthState
import com.chriscartland.garage.auth.AuthViewModelImpl
import com.chriscartland.garage.door.DoorEvent
import com.chriscartland.garage.remotebutton.ButtonRequestStatus
import com.chriscartland.garage.remotebutton.RemoteButtonViewModelImpl
import com.chriscartland.garage.ui.theme.LocalDoorStatusColorScheme
import com.chriscartland.garage.ui.theme.doorButtonColors
import com.chriscartland.garage.ui.theme.doorCardColors
import com.chriscartland.garage.door.DoorViewModelImpl
import com.chriscartland.garage.door.LoadingResult
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted

@Composable
fun HomeContent(
    modifier: Modifier = Modifier,
    viewModel: DoorViewModelImpl = hiltViewModel(),
    authViewModel: AuthViewModelImpl = hiltViewModel(),
    buttonViewModel: RemoteButtonViewModelImpl = hiltViewModel(),
) {
    val activity = LocalContext.current as ComponentActivity
    val currentDoorEvent = viewModel.currentDoorEvent.collectAsState()
    val buttonRequestStatus = buttonViewModel.buttonRequestStatus.collectAsState()
    val authState by authViewModel.authState.collectAsState()
    HomeContent(
        currentDoorEvent = currentDoorEvent.value,
        remoteButtonRequestStatus = buttonRequestStatus.value,
        modifier = modifier,
        onFetchCurrentDoorEvent = { viewModel.fetchCurrentDoorEvent() },
        onRemoteButtonClick = {
            when (val it = authState) {
                is AuthState.Authenticated -> {
                    buttonViewModel.pushRemoteButton(it.user)
                }
                AuthState.Unauthenticated -> {
                    authViewModel.signInWithGoogle(activity)
                }
            }
        },
        onResetRemote = { buttonViewModel.resetRemoteButton() },
        isSignedIn = authState is AuthState.Authenticated,
        onSignIn = { authViewModel.signInWithGoogle(activity) },
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeContent(
    currentDoorEvent: LoadingResult<DoorEvent?>,
    modifier: Modifier = Modifier,
    remoteButtonRequestStatus: ButtonRequestStatus = ButtonRequestStatus.NONE,
    onFetchCurrentDoorEvent: () -> Unit = {},
    onRemoteButtonClick: () -> Unit = {},
    onResetRemote: () -> Unit = {},
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
        if (currentDoorEvent is LoadingResult.Loading) {
            item {
                Text(text = "Loading...")
            }
        }
        // If the current event had an error, show an error card.
        if (currentDoorEvent is LoadingResult.Error) {
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
        val doorEvent = currentDoorEvent.data
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
                        onResetRemote()
                    },
                    buttonColors = doorButtonColors(LocalDoorStatusColorScheme.current, doorEvent),
                    remoteButtonRequestStatus = remoteButtonRequestStatus,
                )
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

data class RemoteIndicator(
    val text: String,
    val complete: Int,
    val failure: Boolean = false,
)

@Composable
fun ButtonRequestIndicator(
    modifier: Modifier = Modifier,
    remoteButtonRequestStatus: ButtonRequestStatus = ButtonRequestStatus.NONE,
) {
    val progress = when (remoteButtonRequestStatus) {
        ButtonRequestStatus.NONE -> RemoteIndicator("", 0)
        ButtonRequestStatus.SENDING -> RemoteIndicator("Sending", 1)
        ButtonRequestStatus.SENDING_TIMEOUT -> RemoteIndicator("Sending failed", 2)
        ButtonRequestStatus.SENT -> RemoteIndicator("Sent", 3)
        ButtonRequestStatus.SENT_TIMEOUT -> RemoteIndicator("Command not delivered", 4, failure = true)
        ButtonRequestStatus.RECEIVED -> RemoteIndicator("Complete", 5)
    }
    val colorComplete: Color = if (progress.failure) Color(0xFFFF3333) else Color(0xFF3333FF)
    Column(
        modifier = modifier,
    ) {
        ParallelogramProgressBar(
            max = 5,
            complete = progress.complete,
            colorComplete = colorComplete,
        )
        Text(text = progress.text, textAlign = TextAlign.Center)
    }
}

@Preview(showBackground = true)
@Composable
fun HomeContentPreview() {
    HomeContent(
        currentDoorEvent = LoadingResult.Complete(demoDoorEvents.firstOrNull()),
    )
}
