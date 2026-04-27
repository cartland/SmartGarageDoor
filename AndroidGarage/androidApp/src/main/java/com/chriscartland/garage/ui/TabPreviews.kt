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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chriscartland.garage.domain.model.AppVersion
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.DisplayName
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.FirebaseIdToken
import com.chriscartland.garage.domain.model.LoadingResult
import com.chriscartland.garage.domain.model.SnoozeState
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.presentation.demoDoorEvents
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus

/**
 * Full-screen tab previews with Scaffold, TopAppBar, and BottomNavigationBar.
 * Used for screenshot tests to show what the app looks like as a whole.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabPreviewScaffold(
    selectedScreen: Screen,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Garage") })
        },
        bottomBar = {
            BottomNavigationBar(
                currentScreen = selectedScreen,
                onTabSelected = {},
            )
        },
    ) { innerPadding ->
        content(
            Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .fillMaxSize(),
        )
    }
}

/**
 * Full-screen scaffold for detail (non-tab) screens reached by pushing onto the
 * back stack. Mirrors the runtime [TopAppBar] from `Main.kt` — title customized
 * per screen, back arrow as the navigation icon, bottom nav still visible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScreenPreviewScaffold(
    title: String,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
        bottomBar = {
            BottomNavigationBar(
                currentScreen = null,
                onTabSelected = {},
            )
        },
    ) { innerPadding ->
        content(
            Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .fillMaxSize(),
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Preview(showBackground = true)
@Composable
fun HomeTabPreview() {
    TabPreviewScaffold(selectedScreen = Screen.Home) { modifier ->
        HomeContent(
            currentDoorEvent = LoadingResult.Complete(demoDoorEvents.firstOrNull()),
            modifier = modifier,
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
}

@Preview(showBackground = true)
@Composable
fun HistoryTabPreview() {
    TabPreviewScaffold(selectedScreen = Screen.History) { modifier ->
        DoorHistoryContent(
            recentDoorEvents = LoadingResult.Complete(demoDoorEvents),
            modifier = modifier,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun FunctionListScreenPreview() {
    // Pass explicit lambdas so Kotlin picks the stateless inner overload —
    // the DI wrapper above this calls rememberAppComponent(), which crashes
    // Layoutlib in screenshot tests.
    DetailScreenPreviewScaffold(title = "Function list") { modifier ->
        FunctionListContent(
            modifier = modifier,
            onOpenOrCloseDoor = {},
            onRefreshDoorStatus = {},
            onRefreshDoorHistory = {},
            onSnoozeOneHour = {},
            onSignIn = {},
            onSignOut = {},
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Preview(showBackground = true)
@Composable
fun SettingsTabPreview() {
    TabPreviewScaffold(selectedScreen = Screen.Profile) { modifier ->
        ProfileContent(
            user = User(
                name = DisplayName("Chris Cartland"),
                email = Email("chris@example.com"),
                idToken = FirebaseIdToken(idToken = "preview", exp = 0),
            ),
            modifier = modifier,
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
        )
    }
}
