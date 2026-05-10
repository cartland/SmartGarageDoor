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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.chriscartland.garage.ui.theme.ContentWidth

/**
 * Single source of horizontal layout for screen-level route content.
 *
 * Wraps each `NavDisplay` route entry in a top-centered Box that caps the
 * content width to [ContentWidth.Standard]. On phones (<640dp) the cap is a
 * no-op and layout matches the legacy behavior. On tablets, foldables, and
 * landscape phones, content centers within the cap and excess width becomes
 * margin.
 *
 * Also consumes the **horizontal** [WindowInsets.safeDrawing] inset (display
 * cutout, side-mounted system bars in landscape, IME-side insets) so content
 * never draws under a side camera cutout. `Scaffold` only consumes vertical
 * insets via its `topBar`/`bottomBar` slots — the body content is the route
 * wrapper's responsibility, and since the wrapper owns horizontal padding it
 * also owns horizontal inset consumption. Vertical safe-drawing insets are
 * already covered by Scaffold's TopAppBar + NavigationBar slots, so this
 * wrapper deliberately scopes inset consumption to `Horizontal`.
 *
 * Forward-compatible with the future two-pane experiment: when two-pane
 * mode lands behind a runtime toggle, it consumes the same `ContentWidth`
 * tokens — the list pane in two-pane mode reuses these widths rather than
 * introducing a new set. The single-pane branch (this Composable) remains
 * the canonical fallback.
 *
 * Use ONCE per route entry in `Main.kt`. Child screens must not re-apply
 * the width cap, the centering wrapper, or horizontal cutout consumption.
 *
 * @param content the screen-level Composable. The provided [Modifier] is
 *   the one [content] should attach: it has the width cap and `fillMaxSize`
 *   pre-applied. Any horizontal padding the content needs (e.g.
 *   `Spacing.Screen`) is the content's responsibility — `RouteContent`
 *   does not opine on padding, only on width, centering, and inset consumption.
 */
@Composable
fun RouteContent(content: @Composable (Modifier) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        contentAlignment = Alignment.TopCenter,
    ) {
        // Modifier order matters: widthIn(max=...) MUST come before
        // fillMaxSize. fillMaxSize first sets minWidth=maxWidth=parent.maxWidth,
        // and the subsequent widthIn becomes a no-op because Compose's
        // SizeModifier coerces maxWidth >= minWidth (i.e. it can't shrink
        // the maxWidth below the existing minWidth). With widthIn first,
        // maxWidth is capped to ContentWidth.Standard, then fillMaxSize
        // sets minWidth=maxWidth=640dp. Result: child renders at 640dp
        // and the Box's TopCenter alignment centers it within the canvas.
        content(
            Modifier
                .widthIn(max = ContentWidth.Standard)
                .fillMaxSize(),
        )
    }
}
