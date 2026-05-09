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

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Single source of truth for adaptive-layout decisions.
 *
 * The app has historically read `LocalAppWindowSizeClass.current.widthSizeClass`
 * directly at every consumer (bottom nav visibility, bottom nav highlight,
 * per-screen entry-block branching). With three to four such consumers in
 * `Main.kt`, the threshold rule and the merge rule (`History` collapses
 * into `Home` on wide) have drifted twice during refactors. This file
 * centralizes the rules so consumers ask "what mode am I in?" instead of
 * recomputing it from raw size classes.
 *
 * Adding a new screen, changing the activation threshold, or introducing
 * a third mode (e.g. a future Expanded-only three-pane treatment) is a
 * single-file edit here — the sealed-type `when` exhaustiveness check
 * forces every consumer to handle the new case.
 *
 * **Reading the size class:** consumers must call [rememberAppLayoutMode]
 * (or take an `AppLayoutMode` as a parameter) instead of reading
 * `LocalAppWindowSizeClass.current` directly. This is enforced by
 * `checkAppLayoutModeReads` (in `validate.sh`) — only this file is
 * allowed to read the local.
 */
sealed interface AppLayoutMode {
    /**
     * Tabs visible in the bottom navigation for this layout mode.
     * Order is the on-screen order; absent tabs are hidden in this mode.
     */
    val visibleTabs: List<Tab>

    /**
     * Back-stack-entry redirects: when the current top of the back stack
     * is a key in this map, the UI should treat it as the value (for
     * tab-highlight selection AND for which route renderer is invoked).
     *
     * Today's only entry: `Screen.History → Screen.Home` on [Wide], so a
     * back stack restored from a phone-shaped state with `[Home, History]`
     * on top still highlights Home tab and renders the wide dashboard.
     * On [Compact] the map is empty (every screen is its own destination).
     */
    val mergedRoutes: Map<Screen, Screen>

    /**
     * Phone-shaped layout: 3 tabs, no merging, single-pane routes.
     * Activation: `WindowWidthSizeClass.Compact` (<600dp).
     */
    data object Compact : AppLayoutMode {
        override val visibleTabs: List<Tab> = Tab.entries.toList()
        override val mergedRoutes: Map<Screen, Screen> = emptyMap()
    }

    /**
     * Wide layout: 2 tabs (History merges into Home), Home renders the
     * Home + History dashboard. Activation: `WindowWidthSizeClass.Medium`
     * (600–839dp).
     */
    data object Wide : AppLayoutMode {
        override val visibleTabs: List<Tab> = Tab.entries.filter { it != Tab.History }
        override val mergedRoutes: Map<Screen, Screen> = mapOf(Screen.History to Screen.Home)
    }

    /**
     * Expanded layout: no tabs (bottom nav hidden), Home renders the
     * Home + History + Settings 3-pane dashboard. Settings sub-screens
     * (`FunctionList`, `Diagnostics`) overlay the Settings pane only —
     * Home + History stay visible. Activation:
     * `WindowWidthSizeClass.Expanded` (≥840dp).
     *
     * **Threshold note (M3 standard).** ≥840dp includes phones in
     * landscape (~916dp on a Pixel 9 Pro), where each pane is ~280dp
     * after gaps — narrower than ideal. If landscape phones look
     * cramped in production, the fallback is to raise activation to a
     * custom 1200dp threshold via a one-line change in
     * [Companion.fromWidthSizeClass]. That keeps phones in landscape
     * on the [Wide] 2-pane layout (~458dp/pane) while tablets in
     * landscape (~1280dp+) and ChromeOS still get 3-pane (~415dp/pane).
     *
     * **Why Profile + FunctionList + Diagnostics all merge to Home.**
     * In Expanded the Settings column is always visible, and sub-screens
     * overlay only that column. The route for `Screen.Profile` therefore
     * doesn't need its own destination — it canonicalizes to `Home` and
     * renders the 3-pane with Profile in the Settings slot. Same for
     * `Screen.FunctionList` and `Screen.Diagnostics` — they canonicalize
     * to `Home` and render the 3-pane with their content as the Settings
     * slot's body. The dispatch in `Main.kt` reads the original (non-
     * canonicalized) screen to decide which body to drop into the slot.
     */
    data object Expanded : AppLayoutMode {
        override val visibleTabs: List<Tab> = emptyList()
        override val mergedRoutes: Map<Screen, Screen> = mapOf(
            Screen.History to Screen.Home,
            Screen.Profile to Screen.Home,
            Screen.FunctionList to Screen.Home,
            Screen.Diagnostics to Screen.Home,
        )
    }

    companion object {
        /**
         * Pure mapping from raw `WindowWidthSizeClass` to layout mode.
         * Thresholds live here — change activation by editing this
         * single function.
         *
         * **Custom threshold fallback for [Expanded].** If the
         * M3-standard ≥840dp activation results in cramped 3-pane
         * layouts on phones in landscape (~916dp / ~280dp per pane),
         * raise the [Expanded] threshold to a custom width:
         * ```kotlin
         * widthSizeClass >= WindowWidthSizeClass.Expanded -> Expanded
         * ```
         * becomes (with `LocalConfiguration` access — adjust for the
         * actual reading site):
         * ```kotlin
         * configuration.screenWidthDp >= 1200 -> Expanded
         * ```
         * That keeps phones-in-landscape on [Wide] 2-pane and only
         * promotes to 3-pane on tablets in landscape and ChromeOS.
         */
        fun fromWidthSizeClass(widthSizeClass: WindowWidthSizeClass): AppLayoutMode =
            when {
                widthSizeClass >= WindowWidthSizeClass.Expanded -> Expanded
                widthSizeClass >= WindowWidthSizeClass.Medium -> Wide
                else -> Compact
            }
    }
}

/**
 * Resolve the current [AppLayoutMode] from [LocalAppWindowSizeClass].
 *
 * **The only place in the app allowed to read `LocalAppWindowSizeClass.current`
 * directly.** All other consumers should either take an `AppLayoutMode`
 * parameter or call this function — the lint enforces this boundary.
 */
@Composable
@ReadOnlyComposable
fun currentAppLayoutMode(): AppLayoutMode = AppLayoutMode.fromWidthSizeClass(LocalAppWindowSizeClass.current.widthSizeClass)

/**
 * Returns the canonical destination for [currentScreen] under [mode],
 * applying [AppLayoutMode.mergedRoutes]. Used by both the bottom nav
 * (highlight) and the entry provider (render which route).
 */
fun AppLayoutMode.canonicalScreen(currentScreen: Screen?): Screen? = currentScreen?.let { mergedRoutes[it] ?: it }
