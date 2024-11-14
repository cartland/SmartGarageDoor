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

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chriscartland.garage.applogger.AppLoggerViewModel
import com.chriscartland.garage.applogger.AppLoggerViewModelImpl
import com.chriscartland.garage.auth.AuthState
import com.chriscartland.garage.auth.AuthViewModel
import com.chriscartland.garage.auth.AuthViewModelImpl
import com.chriscartland.garage.config.AppLoggerKeys
import com.chriscartland.garage.door.DoorEvent
import com.chriscartland.garage.door.DoorViewModel
import com.chriscartland.garage.door.DoorViewModelImpl
import com.chriscartland.garage.door.LoadingResult
import com.chriscartland.garage.permissions.notificationJustificationText
import com.chriscartland.garage.permissions.rememberNotificationPermissionState
import com.chriscartland.garage.remotebutton.RemoteButtonViewModel
import com.chriscartland.garage.remotebutton.RemoteButtonViewModelImpl
import com.chriscartland.garage.remotebutton.RequestStatus
import com.chriscartland.garage.ui.theme.LocalDoorStatusColorScheme
import com.chriscartland.garage.ui.theme.doorCardColors
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.isGranted
import java.time.Instant

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeContent(
    modifier: Modifier = Modifier,
    viewModel: DoorViewModel = hiltViewModel<DoorViewModelImpl>(),
    authViewModel: AuthViewModel = hiltViewModel<AuthViewModelImpl>(),
    buttonViewModel: RemoteButtonViewModel = hiltViewModel<RemoteButtonViewModelImpl>(),
    appLoggerViewModel: AppLoggerViewModel = hiltViewModel<AppLoggerViewModelImpl>(),
    onOldCheckInChanged: (Boolean) -> Unit = {},
) {
    val activity = LocalContext.current as ComponentActivity
    val currentDoorEvent by viewModel.currentDoorEvent.collectAsState()
    val buttonRequestStatus by buttonViewModel.requestStatus.collectAsState()
    val authState by authViewModel.authState.collectAsState()
    HomeContent(
        currentDoorEvent = currentDoorEvent,
        remoteRequestStatus = buttonRequestStatus,
        modifier = modifier,
        onFetchCurrentDoorEvent = {
            appLoggerViewModel.log(AppLoggerKeys.USER_FETCH_CURRENT_DOOR)
            viewModel.fetchCurrentDoorEvent()
        },
        onRemoteButtonClick = {
            when (authState) {
                is AuthState.Authenticated -> {
                    Log.d(
                        TAG, "Remote button clicked. " +
                                "AuthViewModel authState $authState"
                    )
                    buttonViewModel.pushRemoteButton(authViewModel.authRepository)
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
        onSignIn = {
            authViewModel.signInWithGoogle(activity)
        },
        onResetFcm = {
            viewModel.deregisterFcm(activity)
        },
        onOldCheckInChanged = onOldCheckInChanged,
        onLogNotificationPermissionRequested = {
            appLoggerViewModel.log(AppLoggerKeys.USER_REQUESTED_NOTIFICATION_PERMISSION)
        }
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
    onResetFcm: () -> Unit = {},
    onOldCheckInChanged: (Boolean) -> Unit = {},
    onLogNotificationPermissionRequested: () -> Unit = {},
) {
    var permissionRequestCount by remember { mutableIntStateOf(0) }
    // Show the current door event.
    val doorEvent = currentDoorEvent.data

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val lastCheckInTime = doorEvent?.lastCheckInTimeSeconds
        OldLastCheckInBanner(
            lastCheckIn = lastCheckInTime?.let { Instant.ofEpochSecond(it) },
            action = {
                Log.e(TAG, "Trying to fix outdated info. Resetting FCM, and fetching data.")
                onResetFcm()
                onFetchCurrentDoorEvent()
            },
            modifier = Modifier.fillMaxWidth(),
            onOldCheckInChanged = onOldCheckInChanged,
        )
        // Add a card at the top if the notification permission is not granted.
        if (!notificationPermissionState.status.isGranted) {
            ErrorCard(
                text = notificationJustificationText(permissionRequestCount),
                buttonText = "Allow",
                onClick = {
                    permissionRequestCount++
                    notificationPermissionState.launchPermissionRequest()
                    onLogNotificationPermissionRequested()
                },
            )
        }

        // If the current event had an error, show an error card.
        if (currentDoorEvent is LoadingResult.Error) {
            ErrorCard(
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
    ReportDrawnWhen { currentDoorEvent is LoadingResult.Complete }
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
            override fun launchPermissionRequest() { /* Do nothing */
            }
        }
    )
}

private const val TAG = "HomeContent"
