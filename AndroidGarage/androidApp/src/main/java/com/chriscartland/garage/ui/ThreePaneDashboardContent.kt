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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.chriscartland.garage.ui.theme.ContentWidth
import com.chriscartland.garage.ui.theme.Spacing

/**
 * Expanded-width dashboard — Home + History + Settings side-by-side.
 *
 * Sister to [HomeDashboardContent] (the 2-pane Wide layout). Same slot
 * pattern, one extra column. Activates at `WindowWidthSizeClass.Expanded`
 * (≥840dp) — see [com.chriscartland.garage.ui.AppLayoutMode] for the
 * dispatch.
 *
 * **Stateless and slot-based.** The three pane bodies are passed as
 * `@Composable` slots so the dashboard composition itself has no
 * dependency on production stateful wrappers (`HomeContent` /
 * `DoorHistoryContent` / `ProfileContent`) — preview-friendly; production
 * wiring sits one level up.
 *
 * **Layout contract:**
 *  - Row of three equal-weight columns separated by [Spacing.Screen] (16dp).
 *  - Each column caps its width at [ContentWidth.Standard] (640dp) —
 *    matches the per-pane cap of [HomeDashboardContent] and the
 *    single-pane phone screen cap. Total ceiling: 3 × 640 + 2 × 16 = 1952dp.
 *  - The Row itself has no width cap — wider windows than 1952dp leave
 *    extra space distributed by the parent's `Arrangement`.
 *  - Horizontal screen padding ([Spacing.Screen]) is the parent's job —
 *    same convention as single-pane and 2-pane.
 *
 * **Threshold note (M3 standard).** Activation is at the M3
 * `WindowWidthSizeClass.Expanded` boundary (≥840dp). This includes phones
 * in landscape (~916dp on a Pixel 9 Pro), which means each pane is ~280dp
 * after gaps — narrower than ideal. If landscape phones look cramped in
 * production, the fallback is to raise the activation threshold to a
 * custom 1200dp via a one-line change in
 * `AppLayoutMode.Companion.fromWidthSizeClass`. That keeps phones in
 * landscape on the 2-pane layout (~458dp/pane) while tablets in landscape
 * (~1280dp+) and ChromeOS still get 3-pane (~415dp/pane).
 *
 * **Sub-screen overlay (Diagnostics, FunctionList).** When a Settings
 * sub-screen is on the back stack in Expanded mode, the production
 * dispatch should pass that sub-screen's body as the `settingsPane`
 * slot — the dashboard composition itself doesn't know about overlay
 * vs. normal Settings. Home and History panes stay live regardless of
 * what's in the Settings slot.
 */
@Composable
fun ThreePaneDashboardContent(
    homePane: @Composable (Modifier) -> Unit,
    historyPane: @Composable (Modifier) -> Unit,
    settingsPane: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Screen),
    ) {
        homePane(
            Modifier
                .weight(1f)
                .widthIn(max = ContentWidth.Standard)
                .fillMaxHeight(),
        )
        historyPane(
            Modifier
                .weight(1f)
                .widthIn(max = ContentWidth.Standard)
                .fillMaxHeight(),
        )
        settingsPane(
            Modifier
                .weight(1f)
                .widthIn(max = ContentWidth.Standard)
                .fillMaxHeight(),
        )
    }
}
