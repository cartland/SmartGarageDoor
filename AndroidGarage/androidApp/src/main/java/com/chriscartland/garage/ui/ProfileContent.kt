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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chriscartland.garage.auth.rememberGoogleSignIn
import com.chriscartland.garage.di.rememberAppComponent
import com.chriscartland.garage.domain.model.AppVersion
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.DisplayName
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.FirebaseIdToken
import com.chriscartland.garage.domain.model.SnoozeAction
import com.chriscartland.garage.domain.model.SnoozeDurationUIOption
import com.chriscartland.garage.domain.model.SnoozeState
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.permissions.rememberNotificationPermissionState
import com.chriscartland.garage.usecase.AppSettingsViewModel
import com.chriscartland.garage.usecase.AuthViewModel
import com.chriscartland.garage.usecase.RemoteButtonViewModel
import com.chriscartland.garage.version.AppVersion
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.isGranted
import kotlinx.coroutines.delay
import java.time.Duration

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ProfileContent(
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel? = null,
    onNavigateToFunctionList: () -> Unit = {},
) {
    val component = rememberAppComponent()
    val resolvedAuthViewModel = authViewModel ?: viewModel { component.authViewModel }
    val buttonViewModel: RemoteButtonViewModel = viewModel { component.remoteButtonViewModel }
    val settingsViewModel: AppSettingsViewModel = viewModel { component.appSettingsViewModel }
    val googleSignIn = rememberGoogleSignIn(
        onTokenReceived = { token -> resolvedAuthViewModel.signInWithGoogle(token) },
    )
    val authState by resolvedAuthViewModel.authState.collectAsState()
    val snoozeState by buttonViewModel.snoozeState.collectAsState()
    val snoozeAction by buttonViewModel.snoozeAction.collectAsState()
    val userCardExpanded by settingsViewModel.profileUserCardExpanded.collectAsState()
    val appCardExpanded by settingsViewModel.profileAppCardExpanded.collectAsState()
    val functionListAccess by settingsViewModel.functionListAccess.collectAsState()
    val appVersion = LocalContext.current.AppVersion()

    LaunchedEffect(Unit) {
        while (true) {
            buttonViewModel.fetchSnoozeStatus()
            delay(Duration.ofMinutes(1).toMillis())
        }
    }
    val appConfig = component.appConfig
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
        userCardExpanded = userCardExpanded,
        onUserCardExpandedChange = { settingsViewModel.setProfileUserCardExpanded(it) },
        appVersion = appVersion,
        appCardExpanded = appCardExpanded,
        onAppCardExpandedChange = { settingsViewModel.setProfileAppCardExpanded(it) },
        onNavigateToFunctionList = onNavigateToFunctionList,
        functionListAccess = functionListAccess,
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
    userCardExpanded: Boolean? = true,
    onUserCardExpandedChange: (Boolean) -> Unit = {},
    appVersion: AppVersion? = null,
    appCardExpanded: Boolean? = true,
    onAppCardExpandedChange: (Boolean) -> Unit = {},
    onNavigateToFunctionList: () -> Unit = {},
    functionListAccess: Boolean? = null,
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
        if (userCardExpanded != null) {
            item {
                UserInfoCard(
                    user = user,
                    modifier = Modifier
                        .fillMaxWidth(),
                    signIn = signIn,
                    signOut = signOut,
                    startExpanded = userCardExpanded,
                    onExpandedChange = onUserCardExpandedChange,
                    colors = cardColors,
                )
            }
        }
        if (appVersion != null && appCardExpanded != null) {
            item {
                AndroidAppInfoCard(
                    appVersion = appVersion,
                    startExpanded = appCardExpanded,
                    onExpandedChange = onAppCardExpandedChange,
                    colors = cardColors,
                )
            }
        }
        if (showLogSummary) {
            item {
                LogSummaryCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = cardColors,
                )
            }
        }
        // Gate on `== true` only — `null` (loading/signed-out/error) and
        // `false` (server denies) both deny. See docs/FEATURE_FLAGS.md.
        if (functionListAccess == true) {
            item {
                Button(
                    onClick = onNavigateToFunctionList,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Function list")
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
    ReportDrawn()
}

@OptIn(ExperimentalPermissionsApi::class)
@Preview(showBackground = true)
@Composable
fun ProfileContentPreview() {
    Surface(modifier = Modifier.fillMaxSize()) {
        ProfileContent(
            user = User(
                name = DisplayName("Chris Cartland"),
                email = Email("chris@example.com"),
                idToken = FirebaseIdToken(idToken = "preview", exp = 0),
            ),
            signIn = {},
            signOut = {},
            snoozeState = SnoozeState.NotSnoozing,
            onSnooze = {},
            showSnooze = true,
            showLogSummary = false,
            appVersion = AppVersion(
                packageName = "com.chriscartland.garage",
                versionCode = 1L,
                versionName = "preview",
                buildTimestamp = "preview",
            ),
            notificationPermissionState = object : PermissionState {
                override val permission = "android.permission.POST_NOTIFICATIONS"
                override val status = PermissionStatus.Granted

                override fun launchPermissionRequest() {
                    // No-op for preview.
                }
            },
            functionListAccess = true,
        )
    }
}
