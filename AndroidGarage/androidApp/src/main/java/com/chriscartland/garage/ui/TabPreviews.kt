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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.LoadingResult
import com.chriscartland.garage.domain.model.RemoteButtonState
import com.chriscartland.garage.presentation.demoDoorEvents
import com.chriscartland.garage.ui.home.DeviceCheckIn
import com.chriscartland.garage.ui.home.HomeAuthState
import com.chriscartland.garage.ui.home.HomeMapper
import com.chriscartland.garage.ui.settings.AccountRowState
import com.chriscartland.garage.ui.settings.SettingsContent
import com.chriscartland.garage.ui.settings.SnoozeRowState
import com.chriscartland.garage.usecase.ButtonHealthDisplay
import java.time.Instant
import java.time.ZoneOffset
import com.chriscartland.garage.ui.home.HomeContent as HomeStatelessContent

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

@Preview(showBackground = true)
@Composable
fun HomeTabPreview() {
    // Stateless full-screen Home preview using the new mapper-driven UI.
    // Fixed `now` (matches the in-Composable `LocalInspectionMode` value)
    // and UTC zone keep the screenshot deterministic.
    //
    // The two timestamps are independent at runtime and so are here:
    //   - `lastChangeTimeSeconds` (from the demo event) is days old when
    //     the door hasn't moved — drives the "Since X · Y" line.
    //   - `lastCheckInTimeSeconds` is a healthy device heartbeat. The ESP32
    //     checks in every ~10 min, so on average the pill reads ~5 min ago.
    //     Pick 5 min for the README-framed shot — representative typical
    //     case, comfortably under the 11-min staleness threshold.
    val event = demoDoorEvents.firstOrNull()
    val now = Instant.parse("2026-04-29T12:00:00Z")
    val status = HomeMapper.toHomeStatusDisplay(LoadingResult.Complete(event), now, ZoneOffset.UTC)
    val deviceCheckIn = DeviceCheckIn.format(
        lastCheckInSeconds = now.epochSecond - (5 * 60),
        nowSeconds = now.epochSecond,
    )
    TabPreviewScaffold(selectedScreen = Screen.Home) { modifier ->
        HomeStatelessContent(
            status = status,
            authState = HomeAuthState.SignedIn,
            modifier = modifier,
            remoteButtonState = RemoteButtonState.Ready,
            deviceCheckIn = deviceCheckIn,
            // Happy state for the README — pill reads "Available" with the
            // Sensors icon. Shows users what the Home tab looks like in the
            // typical case (signed in, device reachable).
            buttonHealthDisplay = ButtonHealthDisplay.Online,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HomeTabStalePillPreview() {
    // Same as `HomeTabPreview` but with the device heartbeat set to 23 minutes
    // ago — past the 11-min staleness threshold — so the pill flips to the
    // red `errorContainer` variant. The door's `lastChangeTimeSeconds` stays
    // unchanged: a healthy door can have a stale device heartbeat (e.g. wifi
    // dropped). This documents the pill's error state in the framed-tab
    // context. Not used by the README; the README-framed Home shot stays on
    // `HomeTabPreview` (typical case).
    val event = demoDoorEvents.firstOrNull()
    val now = Instant.parse("2026-04-29T12:00:00Z")
    val status = HomeMapper.toHomeStatusDisplay(LoadingResult.Complete(event), now, ZoneOffset.UTC)
    val deviceCheckIn = DeviceCheckIn.format(
        lastCheckInSeconds = now.epochSecond - (23 * 60),
        nowSeconds = now.epochSecond,
    )
    TabPreviewScaffold(selectedScreen = Screen.Home) { modifier ->
        HomeStatelessContent(
            status = status,
            authState = HomeAuthState.SignedIn,
            modifier = modifier,
            remoteButtonState = RemoteButtonState.Ready,
            deviceCheckIn = deviceCheckIn,
            buttonHealthDisplay = ButtonHealthDisplay.Loading,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HistoryTabPreview() {
    // Curated events spanning multiple days so the framed README shot
    // shows enough rows to clearly extend past the visible viewport
    // (newest at top, older days scroll into view). Mix of normal
    // open/close pairs and a couple of warning/error tags. Preview
    // uses fixed `now` + UTC for determinism. Order is ASCENDING by
    // timestamp — `HistoryMapper` reverses to display newest first.
    val events = listOf(
        // 2026-04-26 — quiet morning, single open/close round trip
        DoorEvent(
            doorPosition = DoorPosition.OPENING,
            lastChangeTimeSeconds = Instant.parse("2026-04-26T08:15:00Z").epochSecond,
        ),
        DoorEvent(
            doorPosition = DoorPosition.OPEN,
            lastChangeTimeSeconds = Instant.parse("2026-04-26T08:15:09Z").epochSecond,
        ),
        DoorEvent(
            doorPosition = DoorPosition.CLOSING,
            lastChangeTimeSeconds = Instant.parse("2026-04-26T08:18:00Z").epochSecond,
        ),
        DoorEvent(
            doorPosition = DoorPosition.CLOSED,
            lastChangeTimeSeconds = Instant.parse("2026-04-26T08:18:07Z").epochSecond,
        ),
        // 2026-04-27 — another quiet day, evening trip
        DoorEvent(
            doorPosition = DoorPosition.OPENING,
            lastChangeTimeSeconds = Instant.parse("2026-04-27T17:42:00Z").epochSecond,
        ),
        DoorEvent(
            doorPosition = DoorPosition.OPEN,
            lastChangeTimeSeconds = Instant.parse("2026-04-27T17:42:08Z").epochSecond,
        ),
        DoorEvent(
            doorPosition = DoorPosition.CLOSING,
            lastChangeTimeSeconds = Instant.parse("2026-04-27T22:11:00Z").epochSecond,
        ),
        DoorEvent(
            doorPosition = DoorPosition.CLOSED,
            lastChangeTimeSeconds = Instant.parse("2026-04-27T22:11:06Z").epochSecond,
        ),
        // 2026-04-28 — active day with a warning + sensor anomaly
        DoorEvent(
            doorPosition = DoorPosition.OPENING,
            lastChangeTimeSeconds = Instant.parse("2026-04-28T18:30:00Z").epochSecond,
        ),
        DoorEvent(
            doorPosition = DoorPosition.OPEN,
            lastChangeTimeSeconds = Instant.parse("2026-04-28T18:30:08Z").epochSecond,
        ),
        DoorEvent(
            doorPosition = DoorPosition.CLOSING,
            lastChangeTimeSeconds = Instant.parse("2026-04-28T20:30:00Z").epochSecond,
        ),
        DoorEvent(
            doorPosition = DoorPosition.CLOSED,
            lastChangeTimeSeconds = Instant.parse("2026-04-28T20:30:06Z").epochSecond,
        ),
        DoorEvent(
            doorPosition = DoorPosition.ERROR_SENSOR_CONFLICT,
            lastChangeTimeSeconds = Instant.parse("2026-04-28T23:42:00Z").epochSecond,
        ),
        // 2026-04-29 — current day, includes the long-open warning
        DoorEvent(
            doorPosition = DoorPosition.OPENING_TOO_LONG,
            lastChangeTimeSeconds = Instant.parse("2026-04-29T09:43:00Z").epochSecond,
        ),
        DoorEvent(
            doorPosition = DoorPosition.OPEN,
            lastChangeTimeSeconds = Instant.parse("2026-04-29T09:47:00Z").epochSecond,
        ),
        DoorEvent(
            doorPosition = DoorPosition.CLOSING,
            lastChangeTimeSeconds = Instant.parse("2026-04-29T09:53:00Z").epochSecond,
        ),
        DoorEvent(
            doorPosition = DoorPosition.CLOSED,
            lastChangeTimeSeconds = Instant.parse("2026-04-29T09:53:06Z").epochSecond,
        ),
        DoorEvent(
            doorPosition = DoorPosition.OPENING,
            lastChangeTimeSeconds = Instant.parse("2026-04-29T10:15:00Z").epochSecond,
        ),
        DoorEvent(
            doorPosition = DoorPosition.OPEN,
            lastChangeTimeSeconds = Instant.parse("2026-04-29T10:15:08Z").epochSecond,
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
            showDeveloperSection = true,
            versionName = "2.7.1",
            versionCode = "184",
        )
    }
}

/**
 * Wide-screen scaffold mirroring `Main.kt`'s rail-mode rendering: no
 * `bottomBar`, with the route content + a `NavigationRailLeft` arranged
 * in a Row inside the Scaffold body. Same inset coordination — the rail
 * owns the start safe-drawing inset via its `windowInsets` parameter,
 * the content sibling declares `consumeWindowInsets(start)` so route
 * wrappers' `safeDrawing.only(Horizontal)` reads transparently shrink
 * to "end side only".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WideTabPreviewScaffold(
    selectedScreen: Screen,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Garage") })
        },
    ) { innerPadding ->
        Row(modifier = Modifier.padding(innerPadding)) {
            NavigationRailLeft(
                currentScreen = selectedScreen,
                onTabSelected = {},
                mode = AppLayoutMode.Wide,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)),
            ) {
                // Mirrors the runtime alignment from `Main.kt` — see
                // [NavigationRailContentTopAlignment].
                content(
                    Modifier
                        .padding(top = NavigationRailContentTopAlignment)
                        .padding(horizontal = 16.dp)
                        .fillMaxSize(),
                )
            }
        }
    }
}

// Wide-screen Home preview with NavigationRailLeft on the left edge instead
// of a bottom bar. Activates AppLayoutMode.Wide rendering. 700dp width
// chosen to be unambiguously inside the Wide range (≥600dp Medium boundary,
// <1200dp Expanded threshold).
@Preview(showBackground = true, widthDp = 700, heightDp = 800)
@Composable
fun HomeRailPreview700dp() {
    val event = demoDoorEvents.firstOrNull()
    val now = Instant.parse("2026-04-29T12:00:00Z")
    val status = HomeMapper.toHomeStatusDisplay(LoadingResult.Complete(event), now, ZoneOffset.UTC)
    val deviceCheckIn = DeviceCheckIn.format(
        lastCheckInSeconds = now.epochSecond - (5 * 60),
        nowSeconds = now.epochSecond,
    )
    WideTabPreviewScaffold(selectedScreen = Screen.Home) { modifier ->
        HomeStatelessContent(
            status = status,
            authState = HomeAuthState.SignedIn,
            modifier = modifier,
            remoteButtonState = RemoteButtonState.Ready,
            deviceCheckIn = deviceCheckIn,
            buttonHealthDisplay = ButtonHealthDisplay.Online,
        )
    }
}
