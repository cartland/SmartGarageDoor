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
 * Wide-screen sibling of [RouteContent]. Caps the dashboard width at
 * `2 * ContentWidth.Standard + Spacing.Screen` (`1296dp`) and centers
 * within the canvas.
 *
 * Use ONLY for the wide-screen Home dashboard route. Single-pane routes
 * (Profile, Diagnostics, FunctionList, narrow-screen Home/History)
 * continue to use [RouteContent] with its `ContentWidth.Standard` cap.
 *
 * Modifier order matters here for the same reason as [RouteContent]:
 * `widthIn(max = ...)` MUST come before `fillMaxSize`. Reversed, the
 * cap would be a no-op because Compose's `SizeModifier` coerces
 * `maxWidth >= minWidth` once `fillMaxSize` sets `minWidth = parent.maxWidth`.
 */
@Composable
fun DashboardRouteContent(content: @Composable (Modifier) -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        // Cap = both panes' caps + the gap between them.
        // 640 + 16 + 640 = 1296.dp.
        val cap = ContentWidth.Standard + Spacing.Screen + ContentWidth.Standard
        content(
            Modifier
                .widthIn(max = cap)
                .fillMaxSize(),
        )
    }
}
