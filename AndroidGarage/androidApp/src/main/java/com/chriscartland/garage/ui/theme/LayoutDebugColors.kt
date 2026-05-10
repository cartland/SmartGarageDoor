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
 *
 */

package com.chriscartland.garage.ui.theme

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Developer-only debug colors for visualizing layout regions.
 *
 * Toggled by Settings → Developer → "Layout debug colors". When the
 * setting is on, [LocalLayoutDebugEnabled] becomes `true` and the
 * three chrome regions of the app paint with these distinct, opaque
 * non-Material palette entries:
 *
 *   - [TopBar]    — magenta. The Scaffold's [TopAppBar] container.
 *   - [NavChrome] — cyan. The Scaffold's [NavigationBar] (Compact) or
 *                   the sibling [NavigationRail] (Wide).
 *   - [Body]      — pale yellow. The Scaffold body Box behind the
 *                   route content.
 *
 * Picked outside the M3 surface palette so they cannot collide with
 * any production color and they make padding / inset boundaries
 * obvious to the eye. Opaque (no alpha) so the underlying widget
 * boundaries are crisp instead of bleeding.
 *
 * **Not for production users.** The setting defaults to `false` and is
 * gated behind the existing developer-allowlist check on the Settings
 * screen — even users on a developer-allowlisted account never see
 * debug colors until they explicitly toggle the row.
 */
object LayoutDebugColors {
    val TopBar: Color = Color(0xFFE91E63)
    val NavChrome: Color = Color(0xFF00BCD4)
    val Body: Color = Color(0xFFFFF59D)
}

/**
 * `true` when the Settings → Developer → "Layout debug colors" toggle
 * is on. Provided at the root of [AppTheme] (or the topmost Scaffold
 * wrapper) and read by:
 *
 *   - [TopAppBar]'s `colors = ...` to set `containerColor`.
 *   - [NavigationBar] and [NavigationRail] `containerColor`.
 *   - The Scaffold body Box's `Modifier.background(...)`.
 *
 * Kept as a separate `staticCompositionLocalOf` (rather than wrapping
 * a richer [LayoutDebugColors] holder) because there's exactly one
 * boolean signal — the colors themselves are static `object` fields
 * that don't need to be re-provided per instance.
 *
 * `staticCompositionLocalOf` (not `compositionLocalOf`) because flips
 * of this value are rare enough (a developer manually tapping the
 * toggle) that re-reading composables is cheaper than the equality-
 * tracking overhead, and it always changes the same uniform set of
 * surfaces.
 */
val LocalLayoutDebugEnabled: ProvidableCompositionLocal<Boolean> =
    staticCompositionLocalOf { false }
