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

import androidx.activity.compose.LocalActivity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.DpSize

/**
 * The current [WindowSizeClass], computed once at the app's Compose root and
 * provided downstream. **All adaptive layout decisions in this app must read
 * this local rather than `LocalConfiguration.current.screenWidthDp` /
 * `screenHeightDp` directly.**
 *
 * Why a single source of truth:
 *  - Foldable unfold mid-task and multi-window resize are events that change
 *    the size class but not necessarily other Configuration fields. A direct
 *    `LocalConfiguration` read can lag the size-class transition by a frame
 *    or skip it altogether on some OEM Android variants.
 *  - Two-pane / single-pane branching needs to be deterministic — every screen
 *    sees the same size class for the same window state.
 *  - Stripping `LocalConfiguration` size reads from screen code keeps screens
 *    layout-agnostic. Branching happens at one wrapper layer (today
 *    `RouteContent`; tomorrow `RouteContent` vs. `TwoPaneRouteContent`).
 *
 * The `staticCompositionLocalOf` (vs. `compositionLocalOf`) is intentional:
 * the value changes only on Activity reconfiguration, which forces a full
 * recomposition anyway. Using `static` saves the per-composition tracking
 * overhead.
 *
 * Enforced by `checkNoLocalConfigurationDimensionReads` (in `validate.sh`).
 *
 * Default value is the smallest reasonable phone size class. The default
 * exists only to keep `@Preview` Composables that don't explicitly provide
 * a size class from crashing on read; in production the value is always
 * overwritten by the [ProvideAppWindowSizeClass] provider at the
 * `GarageApp` root.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
val LocalAppWindowSizeClass: ProvidableCompositionLocal<WindowSizeClass> =
    staticCompositionLocalOf {
        // Compact phone default. Provider always overrides in production.
        WindowSizeClass.calculateFromSize(DpSize.Zero)
    }

/**
 * Computes the current [WindowSizeClass] from the host Activity and provides
 * it via [LocalAppWindowSizeClass] to all descendants. Wrap the app's root
 * Composable content with this provider once.
 *
 * Recomputes on every Compose recomposition, which Activity recreation
 * triggers — so rotation, multi-window resize, and foldable posture changes
 * all flow through.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun ProvideAppWindowSizeClass(content: @Composable () -> Unit) {
    val activity = LocalActivity.current ?: error(
        "ProvideAppWindowSizeClass requires a host Activity (LocalActivity is null). " +
            "This typically means it was called outside an Activity-hosted Compose tree.",
    )
    val windowSizeClass = calculateWindowSizeClass(activity)
    CompositionLocalProvider(LocalAppWindowSizeClass provides windowSizeClass) {
        content()
    }
}

/**
 * Convenience for "is this a wide window where two-pane layouts make sense?".
 * Boundary is the M3 `WindowWidthSizeClass.Medium` cutoff (600dp+) — anything
 * Medium or Expanded qualifies. Today this returns false for all phone form
 * factors and true for tablets, foldables in landscape, and ChromeOS / desktop
 * windowed instances.
 */
val WindowSizeClass.isTwoPaneCapable: Boolean
    get() = widthSizeClass >= WindowWidthSizeClass.Medium
