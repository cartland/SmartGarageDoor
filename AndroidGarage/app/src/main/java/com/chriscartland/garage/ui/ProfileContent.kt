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

import androidx.activity.ComponentActivity
import androidx.activity.compose.ReportDrawn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chriscartland.garage.auth.AuthState
import com.chriscartland.garage.auth.AuthViewModelImpl
import com.chriscartland.garage.auth.User
import com.chriscartland.garage.config.APP_CONFIG
import com.chriscartland.garage.permissions.rememberNotificationPermissionState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ProfileContent(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModelImpl = hiltViewModel(),
) {
    val context = LocalContext.current as ComponentActivity
    val authState by viewModel.authState.collectAsState()

    ProfileContent(
        user = when (val it = authState) {
            is AuthState.Authenticated -> it.user
            AuthState.Unauthenticated -> null
            AuthState.Unknown -> null
        },
        modifier = modifier,
        signIn = { viewModel.signInWithGoogle(context) },
        signOut = { viewModel.signOut() },
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ProfileContent(
    user: User?,
    modifier: Modifier = Modifier,
    signIn: () -> Unit,
    signOut: () -> Unit,
    notificationPermissionState: PermissionState = rememberNotificationPermissionState(),
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            UserInfoCard(
                user,
                modifier = Modifier
                    .fillMaxWidth(),
                signIn = signIn,
                signOut = signOut,
            )
        }
        if (APP_CONFIG.snoozeNotificationsOption && notificationPermissionState.status.isGranted) {
            item {
                SnoozeNotificationCard(
                    text = "Snooze notification",
                    snoozeText = "Snooze",
                    saveText = "Save",
                )
            }
        }
        if (APP_CONFIG.logSummary) {
            item {
                LogSummaryCard(
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            AndroidAppInfoCard()
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
