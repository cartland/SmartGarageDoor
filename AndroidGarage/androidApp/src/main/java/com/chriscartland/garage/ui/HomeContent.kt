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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.touchlab.kermit.Logger
import com.chriscartland.garage.auth.rememberGoogleSignIn
import com.chriscartland.garage.di.rememberAppComponent
import com.chriscartland.garage.domain.model.AppLoggerKeys
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.DisplayName
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.FirebaseIdToken
import com.chriscartland.garage.domain.model.LoadingResult
import com.chriscartland.garage.domain.model.RemoteButtonState
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.permissions.notificationJustificationText
import com.chriscartland.garage.permissions.rememberNotificationPermissionState
import com.chriscartland.garage.presentation.demoDoorEvents
import com.chriscartland.garage.usecase.AppLoggerViewModel
import com.chriscartland.garage.usecase.AuthViewModel
import com.chriscartland.garage.usecase.DoorViewModel
import com.chriscartland.garage.usecase.RemoteButtonViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.isGranted
import java.time.Instant

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeContent(
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel? = null,
    doorViewModel: DoorViewModel? = null,
    appLoggerViewModel: AppLoggerViewModel? = null,
) {
    val component = rememberAppComponent()
    val resolvedAuthViewModel = authViewModel ?: viewModel { component.authViewModel }
    val resolvedDoorViewModel = doorViewModel ?: viewModel { component.doorViewModel }
    val buttonViewModel: RemoteButtonViewModel = viewModel { component.remoteButtonViewModel }
    val resolvedAppLoggerViewModel = appLoggerViewModel ?: viewModel { component.appLoggerViewModel }
    val googleSignIn = rememberGoogleSignIn(
        onTokenReceived = { token -> resolvedAuthViewModel.signInWithGoogle(token) },
    )
    val currentDoorEvent by resolvedDoorViewModel.currentDoorEvent.collectAsState()
    val buttonState by buttonViewModel.buttonState.collectAsState()
    val authState by resolvedAuthViewModel.authState.collectAsState()
    HomeContent(
        currentDoorEvent = currentDoorEvent,
        remoteButtonState = buttonState,
        modifier = modifier,
        onFetchCurrentDoorEvent = {
            resolvedAppLoggerViewModel.log(AppLoggerKeys.USER_FETCH_CURRENT_DOOR)
            resolvedDoorViewModel.fetchCurrentDoorEvent()
        },
        onRemoteButtonTap = {
            when (authState) {
                is AuthState.Authenticated -> {
                    Logger.d { "Remote button tapped. AuthViewModel authState $authState" }
                    buttonViewModel.onButtonTap()
                }

                AuthState.Unauthenticated -> {
                    googleSignIn.launchSignIn()
                }

                AuthState.Unknown -> {
                    googleSignIn.launchSignIn()
                }
            }
        },
        authState = authState,
        onSignIn = { googleSignIn.launchSignIn() },
        onResetFcm = {
            resolvedDoorViewModel.deregisterFcm()
        },
        onLogNotificationPermissionRequested = {
            resolvedAppLoggerViewModel.log(AppLoggerKeys.USER_REQUESTED_NOTIFICATION_PERMISSION)
        },
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeContent(
    currentDoorEvent: LoadingResult<DoorEvent?>,
    modifier: Modifier = Modifier,
    remoteButtonState: RemoteButtonState = RemoteButtonState.Ready,
    onFetchCurrentDoorEvent: () -> Unit = {},
    onRemoteButtonTap: () -> Unit = {},
    authState: AuthState = AuthState.Unauthenticated,
    onSignIn: () -> Unit = {},
    notificationPermissionState: PermissionState = rememberNotificationPermissionState(),
    onResetFcm: () -> Unit = {},
    onLogNotificationPermissionRequested: () -> Unit = {},
) {
    var permissionRequestCount by remember { mutableIntStateOf(0) }
    // Show the current door event.
    val doorEvent = currentDoorEvent.data

    val lastCheckInTime = doorEvent?.lastCheckInTimeSeconds
    DurationSince(lastCheckInTime?.let { Instant.ofEpochSecond(it) }) { duration ->
        val isOld = lastCheckInTime != null && duration > OLD_DURATION_FOR_DOOR_CHECK_IN
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isOld) {
                OldLastCheckInBanner(
                    action = {
                        Logger.e { "Trying to fix outdated info. Resetting FCM, and fetching data." }
                        onResetFcm()
                        onFetchCurrentDoorEvent()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
                        .clickable { onFetchCurrentDoorEvent() },
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
                            modifier = Modifier
                                .fillMaxSize(),
                            state = remoteButtonState,
                            onTap = onRemoteButtonTap,
                        )
                    }
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
        modifier = Modifier.height(600.dp),
        authState = AuthState.Authenticated(
            User(
                name = DisplayName("Chris"),
                email = Email("chris@example.com"),
                idToken = FirebaseIdToken(idToken = "preview", exp = 0),
            ),
        ),
        notificationPermissionState = object : PermissionState {
            override val permission = "android.permission.POST_NOTIFICATIONS"
            override val status = PermissionStatus.Granted

            override fun launchPermissionRequest() {
                // No-op for preview.
            }
        },
    )
}
