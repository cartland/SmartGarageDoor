package com.chriscartland.garage.ui

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chriscartland.garage.auth.AuthState
import com.chriscartland.garage.auth.AuthViewModelImpl
import com.chriscartland.garage.door.DoorEvent
import com.chriscartland.garage.door.DoorViewModelImpl
import com.chriscartland.garage.door.LoadingResult
import com.chriscartland.garage.permissions.notificationJustificationText
import com.chriscartland.garage.permissions.rememberNotificationPermissionState
import com.chriscartland.garage.remotebutton.RemoteButtonViewModelImpl
import com.chriscartland.garage.remotebutton.RequestStatus
import com.chriscartland.garage.ui.theme.LocalDoorStatusColorScheme
import com.chriscartland.garage.ui.theme.doorCardColors
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.isGranted
import kotlinx.coroutines.delay
import java.time.Duration

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeContent(
    modifier: Modifier = Modifier,
    viewModel: DoorViewModelImpl = hiltViewModel(),
    authViewModel: AuthViewModelImpl = hiltViewModel(),
    buttonViewModel: RemoteButtonViewModelImpl = hiltViewModel(),
) {
    val activity = LocalContext.current as ComponentActivity
    val currentDoorEvent = viewModel.currentDoorEvent.collectAsState()
    val buttonRequestStatus = buttonViewModel.requestStatus.collectAsState()
    val authState by authViewModel.authState.collectAsState()
    HomeContent(
        currentDoorEvent = currentDoorEvent.value,
        remoteRequestStatus = buttonRequestStatus.value,
        modifier = modifier,
        onFetchCurrentDoorEvent = { viewModel.fetchCurrentDoorEvent() },
        onRemoteButtonClick = {
            when (authState) {
                is AuthState.Authenticated -> {
                    buttonViewModel.pushRemoteButton()
                }
                AuthState.Unauthenticated -> {
                    authViewModel.signInWithGoogle(activity)
                }
                AuthState.Unknown -> {
                    authViewModel.signInWithGoogle(activity)
                }
            }
        },
        onResetRemote = { buttonViewModel.resetRemoteButton() },
        authState = authState,
        onSignIn = { authViewModel.signInWithGoogle(activity) },
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeContent(
    currentDoorEvent: LoadingResult<DoorEvent?>,
    modifier: Modifier = Modifier,
    remoteRequestStatus: RequestStatus = RequestStatus.NONE,
    onFetchCurrentDoorEvent: () -> Unit = {},
    onRemoteButtonClick: () -> Unit = {},
    onResetRemote: () -> Unit = {},
    authState: AuthState = AuthState.Unauthenticated,
    onSignIn: () -> Unit = {},
    notificationPermissionState: PermissionState = rememberNotificationPermissionState(),
) {
    var permissionRequestCount by remember { mutableIntStateOf(0) }
    // Show the current door event.
    val doorEvent = currentDoorEvent.data

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Add a card at the top if the notification permission is not granted.
        if (!notificationPermissionState.status.isGranted) {
            ErrorRequestCard(
                text = notificationJustificationText(permissionRequestCount),
                buttonText = "Allow",
                onClick = {
                    permissionRequestCount++
                    notificationPermissionState.launchPermissionRequest()
                },
            )
        }

        // If the current event had an error, show an error card.
        if (currentDoorEvent is LoadingResult.Error) {
            ErrorRequestCard(
                text = "Error fetching current door event: " +
                        currentDoorEvent.exception.toString().take(500),
                buttonText = "Retry",
                onClick = { onFetchCurrentDoorEvent() },
            )
        }

        Box(
            modifier = Modifier.weight(1f),
        ) {
            DoorStatusCard(
                doorEvent = doorEvent,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onFetchCurrentDoorEvent() }, // Fetch on click.
                cardColors = doorCardColors(LocalDoorStatusColorScheme.current, doorEvent),
            )
            // If the current event is loading, show a loading indicator.
            if (currentDoorEvent is LoadingResult.Loading) {
                Text(
                    text = "Loading...",
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        val lastCheckInTime = doorEvent?.lastCheckInTimeSeconds
        // Update time since last check-in every second.
        var checkInDuration by remember { mutableStateOf<Duration?>(null) }
        LaunchedEffect(key1 = lastCheckInTime) {
            while (true) {
                if (lastCheckInTime != null) {
                    checkInDuration = Duration.ofSeconds(
                        System.currentTimeMillis() / 1000 - lastCheckInTime,
                    )
                }
                delay(1000L) // Update every 1 second
            }
        }
        checkInDuration?.let { duration ->
            Text(
                text = ("Time since check-in: " + duration.toFriendlyDuration()) ?: "",
                style = MaterialTheme.typography.labelSmall,
            )
            if (duration > Duration.ofMinutes(15)) {
                Text(
                    text = "Warning: Time since check-in is over 15 minutes",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            when (authState) {
                AuthState.Unknown -> {
                    Text(text = "Checking authentication...")
                }
                AuthState.Unauthenticated -> {
                    Button(onClick = { onSignIn() }) {
                        Text("Sign to access garage remote button")
                    }
                }
                is AuthState.Authenticated -> {
                    RemoteButtonContent(
                        modifier = Modifier.fillMaxSize(),
                        onSubmit = {
                            Log.d("HomeContent", "Remote button clicked")
                            onRemoteButtonClick()
                        },
                        onArming = {
                            onResetRemote()
                        },
                        remoteRequestStatus = remoteRequestStatus,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Preview(showBackground = true)
@Composable
fun HomeContentPreview() {
    HomeContent(
        currentDoorEvent = LoadingResult.Complete(demoDoorEvents.firstOrNull()),
        notificationPermissionState = object : PermissionState {
            override val permission = "android.permission.POST_NOTIFICATIONS"
            override val status = PermissionStatus.Denied(false)
            override fun launchPermissionRequest() { /* Do nothing */ }
        }
    )
}
