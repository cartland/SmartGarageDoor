/*
 * Copyright 2026 Chris Cartland. All rights reserved.
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
 */

package com.chriscartland.garage.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.LoadingResult
import com.chriscartland.garage.domain.model.RemoteButtonState
import com.chriscartland.garage.presentation.demoDoorEvents
import com.chriscartland.garage.ui.home.DeviceCheckIn
import com.chriscartland.garage.ui.home.HomeAuthState
import com.chriscartland.garage.ui.home.HomeMapper
import com.chriscartland.garage.ui.settings.AccountRowState
import com.chriscartland.garage.ui.settings.DiagnosticsContent
import com.chriscartland.garage.ui.settings.DiagnosticsCounter
import com.chriscartland.garage.ui.settings.SettingsContent
import com.chriscartland.garage.ui.settings.SnoozeRowState
import com.chriscartland.garage.ui.theme.Spacing
import com.chriscartland.garage.usecase.ButtonHealthDisplay
import java.time.Instant
import java.time.ZoneOffset
import com.chriscartland.garage.ui.home.HomeContent as HomeStatelessContent

/**
 * 3-pane Expanded-width dashboard previews.
 *
 * Targets the activation boundaries that matter for review:
 *   - 916dp — typical phone in landscape (Pixel 9 Pro). Tightest case
 *     under the M3 standard activation rule. Each pane = ~280dp after
 *     gaps. **Reviewer question: does this look usable, or should we
 *     bump activation to a custom 1200dp threshold?** See the threshold
 *     note in `ThreePaneDashboardContent.kt`.
 *   - 1024dp — common 10-inch tablet landscape. Each pane = ~330dp.
 *   - 1280dp — large tablet / Chromebook landscape. Each pane = ~415dp.
 *     Comfortable.
 *   - 1280dp — same width but with Diagnostics overlay rendered in the
 *     Settings pane slot. Demonstrates the sub-screen overlay pattern:
 *     Home + History stay live; only the Settings slot's body is
 *     replaced.
 *
 * Each preview hides the bottom NavigationBar entirely (no `bottomBar`
 * passed to Scaffold) — Expanded-width has no tab nav per the design.
 *
 * `bottomBar` slot omission has a real effect: at 800dp height with no
 * bottom bar, content gets the full vertical viewport.
 */
private object ThreePaneDemoData {
    val now: Instant = Instant.parse("2026-05-09T12:00:00Z")
    val zone: ZoneOffset = ZoneOffset.UTC

    val historyEvents: List<DoorEvent> = listOf(
        DoorEvent(
            doorPosition = DoorPosition.OPENING_TOO_LONG,
            lastChangeTimeSeconds = Instant.parse("2026-05-09T09:43:00Z").epochSecond,
        ),
        DoorEvent(
            doorPosition = DoorPosition.OPEN,
            lastChangeTimeSeconds = Instant.parse("2026-05-09T09:47:00Z").epochSecond,
        ),
        DoorEvent(
            doorPosition = DoorPosition.CLOSING,
            lastChangeTimeSeconds = Instant.parse("2026-05-09T09:53:00Z").epochSecond,
        ),
        DoorEvent(
            doorPosition = DoorPosition.CLOSED,
            lastChangeTimeSeconds = Instant.parse("2026-05-09T09:53:06Z").epochSecond,
        ),
        DoorEvent(
            doorPosition = DoorPosition.OPENING,
            lastChangeTimeSeconds = Instant.parse("2026-05-09T10:15:00Z").epochSecond,
        ),
        DoorEvent(
            doorPosition = DoorPosition.OPEN,
            lastChangeTimeSeconds = Instant.parse("2026-05-09T10:15:08Z").epochSecond,
        ),
    )

    val diagnosticsCounters: List<DiagnosticsCounter> = listOf(
        DiagnosticsCounter("App init (current door)", 42),
        DiagnosticsCounter("App init (recent doors)", 17),
        DiagnosticsCounter("Door fetch (current)", 836),
        DiagnosticsCounter("Door fetch (recent)", 412),
        DiagnosticsCounter("FCM subscribe", 8),
        DiagnosticsCounter("FCM received", 1247),
        DiagnosticsCounter("FCM exceeded expected timeout", 3),
        DiagnosticsCounter("FCM in expected range", 1244),
    )
}

/**
 * Expanded-width Scaffold + TopAppBar, no NavigationBar. Mirrors the
 * runtime shell from `Main.kt` but with no bottom-nav slot — the
 * Expanded mode hides the tab bar entirely.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpandedScaffold(content: @Composable (Modifier) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Garage") })
        },
        // bottomBar intentionally omitted — Expanded mode has no tab nav.
    ) { innerPadding ->
        content(
            Modifier
                .padding(innerPadding)
                .padding(horizontal = Spacing.Screen)
                .fillMaxSize(),
        )
    }
}

/**
 * The Home pane body — uses the same demo data as `HomeDashboardPreviews`
 * for visual consistency between the 2-pane and 3-pane comparisons.
 */
@Composable
private fun HomePaneBody(modifier: Modifier) {
    val event = demoDoorEvents.firstOrNull()
    val status = HomeMapper.toHomeStatusDisplay(
        currentDoorEvent = LoadingResult.Complete(event),
        now = ThreePaneDemoData.now,
        zone = ThreePaneDemoData.zone,
    )
    val deviceCheckIn = DeviceCheckIn.format(
        lastCheckInSeconds = ThreePaneDemoData.now.epochSecond - (5 * 60),
        nowSeconds = ThreePaneDemoData.now.epochSecond,
    )
    HomeStatelessContent(
        status = status,
        authState = HomeAuthState.SignedIn,
        modifier = modifier,
        remoteButtonState = RemoteButtonState.Ready,
        deviceCheckIn = deviceCheckIn,
        buttonHealthDisplay = ButtonHealthDisplay.Online,
    )
}

@Composable
private fun HistoryPaneBody(modifier: Modifier) {
    DoorHistoryContent(
        recentDoorEvents = LoadingResult.Complete(ThreePaneDemoData.historyEvents),
        now = ThreePaneDemoData.now,
        zone = ThreePaneDemoData.zone,
        modifier = modifier,
    )
}

@Composable
private fun SettingsPaneBody(modifier: Modifier) {
    SettingsContent(
        accountState = AccountRowState.SignedIn(
            displayName = "Chris Cartland",
            email = "chris@example.com",
        ),
        snoozeState = SnoozeRowState.Off,
        showSnoozeRow = true,
        showDeveloperSection = false,
        showFunctionListRow = false,
        versionName = "2.15.3",
        versionCode = "213",
        layoutDebugEnabled = false,
        modifier = modifier,
    )
}

@Composable
private fun DiagnosticsPaneBody(modifier: Modifier) {
    DiagnosticsContent(
        counters = ThreePaneDemoData.diagnosticsCounters,
        onExportCsv = {},
        onClearAll = {},
        onCopyAuthToken = {},
        modifier = modifier,
    )
}

@Preview(name = "3-pane — 916dp (phone landscape, narrow)", widthDp = 916, heightDp = 411, showBackground = true)
@Composable
fun ThreePaneDashboardPhoneLandscapePreview() {
    ExpandedScaffold { modifier ->
        ThreePaneDashboardContent(
            modifier = modifier,
            homePane = { HomePaneBody(it) },
            historyPane = { HistoryPaneBody(it) },
            settingsPane = { SettingsPaneBody(it) },
        )
    }
}

@Preview(name = "3-pane — 1024dp (tablet landscape, narrow)", widthDp = 1024, heightDp = 768, showBackground = true)
@Composable
fun ThreePaneDashboardTabletNarrowPreview() {
    ExpandedScaffold { modifier ->
        ThreePaneDashboardContent(
            modifier = modifier,
            homePane = { HomePaneBody(it) },
            historyPane = { HistoryPaneBody(it) },
            settingsPane = { SettingsPaneBody(it) },
        )
    }
}

@Preview(name = "3-pane — 1280dp (large tablet)", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
fun ThreePaneDashboardLargeTabletPreview() {
    ExpandedScaffold { modifier ->
        ThreePaneDashboardContent(
            modifier = modifier,
            homePane = { HomePaneBody(it) },
            historyPane = { HistoryPaneBody(it) },
            settingsPane = { SettingsPaneBody(it) },
        )
    }
}

@Preview(
    name = "3-pane — 1280dp + Diagnostics overlay",
    widthDp = 1280,
    heightDp = 800,
    showBackground = true,
)
@Composable
fun ThreePaneDashboardWithDiagnosticsOverlayPreview() {
    // Demonstrates the sub-screen overlay pattern: when Diagnostics is on
    // the back stack in Expanded mode, the production dispatch passes its
    // body as the `settingsPane` slot. Home + History stay live and
    // unchanged. The TopAppBar (omitted from this preview's chrome —
    // would show "Diagnostics" + back arrow in production) signals that
    // the back stack can be popped.
    ExpandedScaffold { modifier ->
        ThreePaneDashboardContent(
            modifier = modifier,
            homePane = { HomePaneBody(it) },
            historyPane = { HistoryPaneBody(it) },
            settingsPane = { DiagnosticsPaneBody(it) },
        )
    }
}
