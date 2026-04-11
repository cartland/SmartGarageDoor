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

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.chriscartland.garage.domain.model.DisplayName
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.FirebaseIdToken
import com.chriscartland.garage.domain.model.LoadingResult
import com.chriscartland.garage.domain.model.RemoteButtonState
import com.chriscartland.garage.domain.model.SnoozeState
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.presentation.demoDoorEvents
import com.chriscartland.garage.ui.theme.AppTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus

/**
 * Full-screen preview composables for screenshot tests.
 *
 * Each preview renders a complete app screen: TopAppBar + content + BottomNavigationBar.
 * Uses demo data — no ViewModel, AppComponent, or runtime state.
 */

@Suppress("EmptyFunctionBlock")
private val demoPermissionStateDenied =
    @OptIn(ExperimentalPermissionsApi::class)
    object : PermissionState {
        override val permission = "android.permission.POST_NOTIFICATIONS"
        override val status = PermissionStatus.Denied(false)

        override fun launchPermissionRequest() {}
    }

@Suppress("EmptyFunctionBlock")
private val demoPermissionStateGranted =
    @OptIn(ExperimentalPermissionsApi::class)
    object : PermissionState {
        override val permission = "android.permission.POST_NOTIFICATIONS"
        override val status = PermissionStatus.Granted

        override fun launchPermissionRequest() {}
    }

// region Home Tab

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Preview(showBackground = true, widthDp = 400, heightDp = 800)
@Composable
fun HomeTabPreview() {
    AppTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Garage") }) },
            bottomBar = { BottomNavigationBar(currentScreen = Screen.Home, onTabSelected = {}) },
        ) { padding ->
            HomeContent(
                currentDoorEvent = LoadingResult.Complete(demoDoorEvents.firstOrNull()),
                modifier = Modifier.padding(padding),
                remoteButtonState = RemoteButtonState.Ready,
                notificationPermissionState = demoPermissionStateDenied,
            )
        }
    }
}

// endregion

// region History Tab

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, widthDp = 400, heightDp = 800)
@Composable
fun HistoryTabPreview() {
    AppTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Garage") }) },
            bottomBar = {
                BottomNavigationBar(currentScreen = Screen.History, onTabSelected = {})
            },
        ) { padding ->
            DoorHistoryContent(
                modifier = Modifier.padding(padding),
                recentDoorEvents = LoadingResult.Complete(demoDoorEvents),
            )
        }
    }
}

// endregion

// region Settings Tab

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Preview(showBackground = true, widthDp = 400, heightDp = 800)
@Composable
fun SettingsTabPreview() {
    AppTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Garage") }) },
            bottomBar = {
                BottomNavigationBar(currentScreen = Screen.Profile, onTabSelected = {})
            },
        ) { padding ->
            ProfileContent(
                user = User(
                    name = DisplayName("Jane Doe"),
                    email = Email("jane@example.com"),
                    idToken = FirebaseIdToken(idToken = "preview", exp = Long.MAX_VALUE),
                ),
                modifier = Modifier.padding(padding),
                signIn = {},
                signOut = {},
                snoozeState = SnoozeState.NotSnoozing,
                showLogSummary = false,
                notificationPermissionState = demoPermissionStateGranted,
            )
        }
    }
}

// endregion
