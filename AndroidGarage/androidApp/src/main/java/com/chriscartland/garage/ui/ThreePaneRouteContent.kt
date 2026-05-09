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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.chriscartland.garage.ui.theme.ContentWidth
import com.chriscartland.garage.ui.theme.Spacing

/**
 * Expanded-width sibling of [RouteContent] / [DashboardRouteContent].
 * Caps the 3-pane dashboard width at `3 * ContentWidth.Standard +
 * 2 * Spacing.Screen` (`1952dp`) and centers within the canvas.
 *
 * Use ONLY for the Expanded-width Home dashboard route.
 *
 * Modifier order matters here for the same reason as [RouteContent]:
 * `widthIn(max = ...)` MUST come before `fillMaxSize`. Reversed, the
 * cap would be a no-op because Compose's `SizeModifier` coerces
 * `maxWidth >= minWidth` once `fillMaxSize` sets `minWidth = parent.maxWidth`.
 */
@Composable
fun ThreePaneRouteContent(content: @Composable (Modifier) -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        // Cap = three pane caps + the two gaps between them.
        // 640 + 16 + 640 + 16 + 640 = 1952.dp.
        val cap = (ContentWidth.Standard * 3) + (Spacing.Screen * 2)
        content(
            Modifier
                .widthIn(max = cap)
                .fillMaxSize(),
        )
    }
}
