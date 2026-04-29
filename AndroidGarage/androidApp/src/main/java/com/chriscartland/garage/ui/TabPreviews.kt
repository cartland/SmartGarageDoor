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
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.DisplayName
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.FirebaseIdToken
import com.chriscartland.garage.domain.model.LoadingResult
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.presentation.demoDoorEvents
import com.chriscartland.garage.ui.settings.AccountRowState
import com.chriscartland.garage.ui.settings.SettingsContent
import com.chriscartland.garage.ui.settings.SnoozeRowState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import java.time.Instant
import java.time.ZoneOffset

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
    // Curated events that produce a presentable multi-day framed shot —
    // a current Open, a recent paired Closed/Open with a `_TOO_LONG`
    // warning tag, and a sensor-conflict anomaly. The mapper handles
    // formatting; preview uses fixed `now` + UTC for determinism.
    val events = listOf(
        DoorEvent(
            doorPosition = DoorPosition.OPENING_TOO_LONG,
            lastChangeTimeSeconds = Instant
                .parse("2026-04-29T09:43:00Z")
                .epochSecond,
        ),
        DoorEvent(
            doorPosition = DoorPosition.OPEN,
            lastChangeTimeSeconds = Instant
                .parse("2026-04-29T09:47:00Z")
                .epochSecond,
        ),
        DoorEvent(
            doorPosition = DoorPosition.CLOSING,
            lastChangeTimeSeconds = Instant
                .parse("2026-04-29T09:53:00Z")
                .epochSecond,
        ),
        DoorEvent(
            doorPosition = DoorPosition.CLOSED,
            lastChangeTimeSeconds = Instant
                .parse("2026-04-29T09:53:06Z")
                .epochSecond,
        ),
        DoorEvent(
            doorPosition = DoorPosition.OPENING,
            lastChangeTimeSeconds = Instant
                .parse("2026-04-29T10:15:00Z")
                .epochSecond,
        ),
        DoorEvent(
            doorPosition = DoorPosition.OPEN,
            lastChangeTimeSeconds = Instant
                .parse("2026-04-29T10:15:08Z")
                .epochSecond,
        ),
        DoorEvent(
            doorPosition = DoorPosition.OPENING,
            lastChangeTimeSeconds = Instant
                .parse("2026-04-28T18:30:00Z")
                .epochSecond,
        ),
        DoorEvent(
            doorPosition = DoorPosition.OPEN,
            lastChangeTimeSeconds = Instant
                .parse("2026-04-28T18:30:08Z")
                .epochSecond,
        ),
        DoorEvent(
            doorPosition = DoorPosition.CLOSING,
            lastChangeTimeSeconds = Instant
                .parse("2026-04-28T20:30:00Z")
                .epochSecond,
        ),
        DoorEvent(
            doorPosition = DoorPosition.CLOSED,
            lastChangeTimeSeconds = Instant
                .parse("2026-04-28T20:30:06Z")
                .epochSecond,
        ),
        DoorEvent(
            doorPosition = DoorPosition.ERROR_SENSOR_CONFLICT,
            lastChangeTimeSeconds = Instant
                .parse("2026-04-28T23:42:00Z")
                .epochSecond,
        ),
    )
    TabPreviewScaffold(selectedScreen = Screen.History) { modifier ->
        DoorHistoryContent(
            recentDoorEvents = LoadingResult.Complete(events),
            now = Instant.parse("2026-04-29T10:27:00Z"),
            zone = ZoneOffset.UTC,
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
            accessGranted = true,
            onOpenOrCloseDoor = {},
            onRefreshDoorStatus = {},
            onRefreshDoorHistory = {},
            onSnoozeOneHour = {},
            onSignIn = {},
            onSignOut = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
fun FunctionListScreenDeniedPreview() {
    DetailScreenPreviewScaffold(title = "Function list") { modifier ->
        FunctionListContent(
            modifier = modifier,
            accessGranted = false,
            onOpenOrCloseDoor = {},
            onRefreshDoorStatus = {},
            onRefreshDoorHistory = {},
            onSnoozeOneHour = {},
            onSignIn = {},
            onSignOut = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsTabPreview() {
    TabPreviewScaffold(selectedScreen = Screen.Profile) { modifier ->
        SettingsContent(
            modifier = modifier,
            accountState = AccountRowState.SignedIn(
                displayName = "Chris Cartland",
                email = "chris@example.com",
            ),
            snoozeState = SnoozeRowState.SnoozingUntil("5:30 PM"),
            showSnoozeRow = true,
            showToolsSection = true,
            showDiagnosticsRow = true,
            versionName = "2.7.1",
            versionCode = "184",
        )
    }
}
