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

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.chriscartland.garage.ui.home.rememberSinceLine
import com.chriscartland.garage.ui.theme.PreviewWithSimulatedInsets
import com.chriscartland.garage.ui.theme.Spacing
import com.chriscartland.garage.ui.theme.safeListContentPadding
import com.chriscartland.garage.usecase.ButtonHealthDisplay
import java.time.Instant
import java.time.ZoneOffset
import com.chriscartland.garage.ui.home.HomeContent as HomeStatelessContent

/**
 * Wide-screen Home dashboard preview at multiple viewport widths.
 *
 * The wide-screen UX merges Home + History into a single dashboard
 * destination called "Home". Bottom nav collapses to two tabs (Home,
 * Settings) on wide windows; History becomes a column of the Home
 * destination instead of its own tab.
 *
 * Each preview targets a distinct adaptive boundary:
 *   - 600dp — start of `WindowWidthSizeClass.Medium`. Tightest case the
 *     two-pane layout supports under the proposed activation rule.
 *   - 840dp — common foldable in tablet mode; upper Medium.
 *   - 1024dp — typical tablet landscape.
 *   - 1280dp — large tablet / ChromeOS / desktop windowed instance.
 *     First viewport at which both 640dp pane caps fully fit without
 *     compression.
 *
 * **Activation rule under review:** `WindowWidthSizeClass.Medium`
 * (≥600dp). Below that, single-pane Home (today's behavior).
 */
private object DashboardDemoData {
    val now: Instant = Instant.parse("2026-05-09T12:00:00Z")
    val zone: ZoneOffset = ZoneOffset.UTC

    /**
     * Same demo events the History tab preview uses — gives reviewers
     * a familiar visual reference when comparing the wide-screen
     * History column to the phone-only History tab screenshot.
     */
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
        DoorEvent(
            doorPosition = DoorPosition.OPENING,
            lastChangeTimeSeconds = Instant.parse("2026-05-08T18:30:00Z").epochSecond,
        ),
        DoorEvent(
            doorPosition = DoorPosition.OPEN,
            lastChangeTimeSeconds = Instant.parse("2026-05-08T18:30:08Z").epochSecond,
        ),
        DoorEvent(
            doorPosition = DoorPosition.CLOSING,
            lastChangeTimeSeconds = Instant.parse("2026-05-08T20:30:00Z").epochSecond,
        ),
        DoorEvent(
            doorPosition = DoorPosition.CLOSED,
            lastChangeTimeSeconds = Instant.parse("2026-05-08T20:30:06Z").epochSecond,
        ),
    )
}

/**
 * Wide-screen Scaffold + TopAppBar + collapsed-to-2-items NavigationBar.
 * Mirrors the runtime shell from `Main.kt` but with the wide-screen
 * variant of the bottom nav (no History tab — History is a column of
 * the Home destination). Stateless / interactionless.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WideHomeScaffold(content: @Composable (Modifier) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Garage") })
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    selected = true,
                    onClick = {},
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Person, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = false,
                    onClick = {},
                )
            }
        },
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
 * Stateless body factory — both panes built from deterministic demo data.
 * Reused across the four viewport-width previews below.
 */
@Composable
private fun WideHomeBody(modifier: Modifier = Modifier) {
    val event = demoDoorEvents.firstOrNull()
    val status = HomeMapper.toHomeStatusDisplay(
        currentDoorEvent = LoadingResult.Complete(event),
    )
    val sinceLine = rememberSinceLine(
        lastChangeTimeSeconds = status.lastChangeTimeSeconds,
        now = DashboardDemoData.now,
        zone = DashboardDemoData.zone,
    )
    val deviceCheckIn = DeviceCheckIn.format(
        lastCheckInSeconds = DashboardDemoData.now.epochSecond - (5 * 60),
        nowSeconds = DashboardDemoData.now.epochSecond,
    )

    HomeDashboardContent(
        modifier = modifier,
        homePane = { paneModifier ->
            HomeStatelessContent(
                status = status,
                sinceLine = sinceLine,
                authState = HomeAuthState.SignedIn,
                modifier = paneModifier,
                remoteButtonState = RemoteButtonState.Ready,
                deviceCheckIn = deviceCheckIn,
                buttonHealthDisplay = ButtonHealthDisplay.Loading,
            )
        },
        historyPane = { paneModifier ->
            DoorHistoryContent(
                recentDoorEvents = LoadingResult.Complete(DashboardDemoData.historyEvents),
                now = DashboardDemoData.now,
                zone = DashboardDemoData.zone,
                modifier = paneModifier,
            )
        },
    )
}

@Preview(name = "Home dashboard — 600dp (Medium boundary)", widthDp = 600, heightDp = 800, showBackground = true)
@Composable
fun HomeDashboardPreview600dp() {
    WideHomeScaffold { modifier -> WideHomeBody(modifier) }
}

@Preview(name = "Home dashboard — 840dp (foldable)", widthDp = 840, heightDp = 800, showBackground = true)
@Composable
fun HomeDashboardPreview840dp() {
    WideHomeScaffold { modifier -> WideHomeBody(modifier) }
}

@Preview(name = "Home dashboard — 1024dp (tablet landscape)", widthDp = 1024, heightDp = 800, showBackground = true)
@Composable
fun HomeDashboardPreview1024dp() {
    WideHomeScaffold { modifier -> WideHomeBody(modifier) }
}

@Preview(name = "Home dashboard — 1280dp (full Expanded)", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
fun HomeDashboardPreview1280dp() {
    WideHomeScaffold { modifier -> WideHomeBody(modifier) }
}

/**
 * Wide-screen Scaffold with a [NavigationRailLeft] on the start edge —
 * mirrors the runtime shell from `Main.kt` for [AppLayoutMode.Wide].
 *
 * Same inset coordination as production: rail owns the start
 * safe-drawing inset via its `windowInsets` parameter; content sibling
 * declares `consumeWindowInsets(start)` so the route wrapper's existing
 * `safeDrawing.only(Horizontal)` reading transparently shrinks to "end
 * side only". No bottom bar — Wide uses the rail instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WideRailScaffold(content: @Composable (Modifier) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Garage") })
        },
    ) { innerPadding ->
        Row(modifier = Modifier.padding(innerPadding)) {
            NavigationRailLeft(
                currentScreen = Screen.Home,
                onTabSelected = {},
                mode = AppLayoutMode.Wide,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)),
            ) {
                content(
                    Modifier
                        .padding(horizontal = Spacing.Screen)
                        .fillMaxSize(),
                )
            }
        }
    }
}

// Rail + 2-pane dashboard at 700dp — the lower bound of Wide where
// horizontal real-estate is tightest. After ~80dp of rail and ~16dp
// of side gutters, each pane has ~292dp.
@Preview(
    name = "Home dashboard rail — 700dp (Wide low-bound)",
    widthDp = 700,
    heightDp = 800,
    showBackground = true,
)
@Composable
fun HomeDashboardRailPreview700dp() {
    WideRailScaffold { modifier -> WideHomeBody(modifier) }
}

// Rail + 2-pane dashboard at 916dp — Pixel 9 Pro in landscape, the
// widest landscape phone we expect to hit Wide. Confirms the rail
// looks right at the upper end of Wide before [AppLayoutMode.Expanded]
// kicks in at 1200dp.
@Preview(
    name = "Home dashboard rail — 916dp (Pixel 9 Pro landscape)",
    widthDp = 916,
    heightDp = 411,
    showBackground = true,
)
@Composable
fun HomeDashboardRailPreview916dp() {
    WideRailScaffold { modifier -> WideHomeBody(modifier) }
}

// Inset canary — verifies the `LocalContentEdgeInsets` bridge by
// directly visualizing what `safeListContentPadding()` returns.
// Two side-by-side Box columns: left has NO inset injected (default
// LocalContentEdgeInsets = WindowInsets(0)); right is wrapped in
// `PreviewWithSimulatedInsets(bottomInset = 48.dp)`. Each Box paints a
// red border, then pads by `safeListContentPadding()`, then paints a
// green interior. The thickness of the red bottom band IS the value
// of `safeListContentPadding().bottom` — 24dp on the left (legacy
// chrome clearance only) vs 72dp on the right (24 + 48 injected).
// The diff between these two columns in a single PNG is the visible
// proof the bridge is intact. If both columns render identically, the
// `LocalContentEdgeInsets` propagation broke — caught at PR review
// time before a real-device regression ships.
//
// Why a synthetic side-by-side fixture and not a real screen: a real
// screen-level LazyColumn renders items at the top by default; the
// bottom contentPadding only affects what's visible if content
// overflows AND the scroll position moves the bottom into view.
// Static screenshots in Layoutlib don't reliably reach that state, so
// real-screen canaries silently became identical to baselines (the
// first attempt at this canary failed for exactly this reason). A
// synthetic colored-Box fixture makes the helper's output visible
// regardless of layout state.
@Preview(
    name = "Inset canary — safeListContentPadding bottom: 24dp (no inset) vs 72dp (48dp injected)",
    widthDp = 600,
    heightDp = 400,
    showBackground = true,
)
@Composable
fun SafeListContentPaddingCanaryPreview() {
    Row(modifier = Modifier.fillMaxSize()) {
        // Left: default LocalContentEdgeInsets = WindowInsets(0).
        // safeListContentPadding() bottom = 24dp.
        InsetCanaryColumn(label = "no inset", modifier = Modifier.weight(1f))
        // Right: PreviewWithSimulatedInsets injects bottom = 48dp.
        // safeListContentPadding() bottom = 24 + 48 = 72dp.
        PreviewWithSimulatedInsets(bottomInset = 48.dp) {
            InsetCanaryColumn(label = "48dp injected", modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun InsetCanaryColumn(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(Color.Red)
            .padding(safeListContentPadding()),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Green),
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}
