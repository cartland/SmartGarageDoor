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

import androidx.activity.compose.ReportDrawn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chriscartland.garage.auth.rememberGoogleSignIn
import com.chriscartland.garage.di.rememberAppComponent
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.SnoozeAction
import com.chriscartland.garage.domain.model.SnoozeDurationUIOption
import com.chriscartland.garage.domain.model.SnoozeState
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.permissions.rememberNotificationPermissionState
import com.chriscartland.garage.usecase.AuthViewModel
import com.chriscartland.garage.usecase.RemoteButtonViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import kotlinx.coroutines.delay
import java.time.Duration

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ProfileContent(
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel? = null,
) {
    val component = rememberAppComponent()
    val resolvedAuthViewModel = authViewModel ?: viewModel { component.authViewModel }
    val buttonViewModel: RemoteButtonViewModel = viewModel { component.remoteButtonViewModel }
    val googleSignIn = rememberGoogleSignIn(
        onTokenReceived = { token -> resolvedAuthViewModel.signInWithGoogle(token) },
    )
    val authState by resolvedAuthViewModel.authState.collectAsState()
    val snoozeState by buttonViewModel.snoozeState.collectAsState()
    val snoozeAction by buttonViewModel.snoozeAction.collectAsState()

    LaunchedEffect(Unit) {
        while (true) {
            buttonViewModel.fetchSnoozeStatus()
            delay(Duration.ofMinutes(1).toMillis())
        }
    }
    val appConfig = component.provideAppConfig()
    ProfileContent(
        user = when (val it = authState) {
            is AuthState.Authenticated -> it.user
            AuthState.Unauthenticated -> null
            AuthState.Unknown -> null
        },
        modifier = modifier,
        signIn = { googleSignIn.launchSignIn() },
        signOut = { resolvedAuthViewModel.signOut() },
        snoozeState = snoozeState,
        snoozeAction = snoozeAction,
        onSnooze = {
            buttonViewModel.snoozeOpenDoorsNotifications(it)
        },
        showSnooze = appConfig.snoozeNotificationsOption,
        showLogSummary = appConfig.logSummary,
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ProfileContent(
    user: User?,
    modifier: Modifier = Modifier,
    signIn: () -> Unit,
    signOut: () -> Unit,
    snoozeState: SnoozeState = SnoozeState.Loading,
    snoozeAction: SnoozeAction = SnoozeAction.Idle,
    onSnooze: (snooze: SnoozeDurationUIOption) -> Unit = {},
    showSnooze: Boolean = true,
    showLogSummary: Boolean = true,
    notificationPermissionState: PermissionState = rememberNotificationPermissionState(),
) {
    val cardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        disabledContentColor = MaterialTheme.colorScheme.onSurface,
    )
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showSnooze && notificationPermissionState.status.isGranted) {
            item {
                SnoozeNotificationCard(
                    snoozeState = snoozeState,
                    snoozeAction = snoozeAction,
                    onSnooze = onSnooze,
                    colors = cardColors,
                )
            }
        }
        item {
            UserInfoCard(
                user,
                modifier = Modifier
                    .fillMaxWidth(),
                signIn = signIn,
                signOut = signOut,
                colors = cardColors,
            )
        }
        item {
            AndroidAppInfoCard(
                colors = cardColors,
            )
        }
        if (showLogSummary) {
            item {
                LogSummaryCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = cardColors,
                )
            }
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
    ReportDrawn()
}

@Preview(showBackground = true)
@Composable
fun ProfileContentPreview() {
    ProfileContent()
}
