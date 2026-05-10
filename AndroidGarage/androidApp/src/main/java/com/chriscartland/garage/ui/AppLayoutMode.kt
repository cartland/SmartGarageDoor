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
     * **`screenWidthDp >= [EXPANDED_THRESHOLD_DP]`** (1200dp).
     *
     * **Threshold history.** Originally activation was M3-standard
     * `WindowWidthSizeClass.Expanded` (≥840dp), which included phones
     * in landscape (~916dp on a Pixel 9 Pro). Each pane was ~280dp
     * after gaps — too cramped in practice. 2.16.7 (`android/221`)
     * raised the threshold to a custom 1200dp so:
     *  - Phones in landscape (~600–916dp): [Wide] 2-pane (~458dp/pane).
     *  - Foldables open in landscape (~896–1132dp): [Wide] 2-pane.
     *  - Tablets in landscape (1280dp+) and ChromeOS: [Expanded] 3-pane
     *    (~415dp/pane).
     *
     * 10.5"-class tablets (~1180dp) fall just under and stay on [Wide];
     * the coarseness is acceptable for a single-user personal app.
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
         * Custom dp threshold for [Expanded] (3-pane) activation. Set
         * above the widest landscape phone (~916dp Pixel 9 Pro) and the
         * widest common foldable in landscape (~1132dp Z Fold 6) so
         * those devices stay on [Wide] 2-pane. Tablets in landscape
         * (1280dp+) and ChromeOS still cross the threshold and get
         * 3-pane (~415dp/pane). 10.5"-class tablets (~1180dp) fall
         * just under and stay on [Wide]; see [Expanded] KDoc.
         */
        const val EXPANDED_THRESHOLD_DP = 1200

        /**
         * Pure mapping from raw window dimensions to layout mode.
         * Thresholds live here — change activation by editing this
         * single function.
         *
         * Two inputs: M3 [WindowWidthSizeClass] for the Wide cutoff
         * (≥600dp) and raw [widthDp] for the custom Expanded cutoff
         * ([EXPANDED_THRESHOLD_DP]). The size class alone can't
         * express a threshold tighter than its three buckets.
         */
        fun fromSize(
            widthSizeClass: WindowWidthSizeClass,
            widthDp: Int,
        ): AppLayoutMode =
            when {
                widthDp >= EXPANDED_THRESHOLD_DP -> Expanded
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
fun currentAppLayoutMode(): AppLayoutMode =
    AppLayoutMode.fromSize(
        widthSizeClass = LocalAppWindowSizeClass.current.widthSizeClass,
        widthDp = LocalAppWindowWidthDp.current,
    )

/**
 * Returns the canonical destination for [currentScreen] under [mode],
 * applying [AppLayoutMode.mergedRoutes]. Used by both the bottom nav
 * (highlight) and the entry provider (render which route).
 */
fun AppLayoutMode.canonicalScreen(currentScreen: Screen?): Screen? = currentScreen?.let { mergedRoutes[it] ?: it }
