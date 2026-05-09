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
 * Wide-screen Home destination — Home + History side-by-side.
 *
 * **Stateless and slot-based.** The two pane bodies are passed as
 * `@Composable` slots so the dashboard composition itself has no
 * dependency on the production stateful wrappers (`HomeContent` /
 * `DoorHistoryContent`) — that keeps it preview-friendly and allows
 * production wiring to sit one level up.
 *
 * **Layout contract:**
 *  - Row of two equal-weight columns separated by [Spacing.Screen] (16dp).
 *  - Each column caps its width at [ContentWidth.Standard] (640dp) — the
 *    same per-pane cap a single-pane phone screen has today.
 *  - The Row itself has no width cap — wider windows than 1280dp leave
 *    extra space distributed by the parent's `Arrangement` (today,
 *    `Arrangement.Center` puts the gutter on the outsides; the parent
 *    `RouteContent`-equivalent wrapper for wide screens supplies that).
 *  - Horizontal screen padding ([Spacing.Screen]) is the parent's job —
 *    same convention as single-pane screens.
 *
 * **Why slots, not direct calls to `HomeContent` / `DoorHistoryContent`:**
 * production-stateful versions resolve `viewModel { ... }` and crash in
 * Layoutlib (no `LocalActivity`). The slot pattern lets screenshot tests
 * pass stateless inner Composables (`HomeContentInternal`,
 * `HistoryContent`) directly with deterministic data. Production wiring
 * passes the stateful wrappers.
 */
@Composable
fun HomeDashboardContent(
    homePane: @Composable (Modifier) -> Unit,
    historyPane: @Composable (Modifier) -> Unit,
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
    }
}
