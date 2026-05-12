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

package com.chriscartland.garage.domain.model

/**
 * Constants for aligning the Wide-mode `NavigationRail` selected-item
 * indicator pill with the body's first content row.
 *
 * The default value for the user's "Nav rail top padding" setting is
 * **derived** from these two named constants rather than hardcoded.
 * The user can still override the value via Settings → Developer →
 * Nav rail; the derivation only affects the default returned when no
 * override exists.
 *
 * ## Why this exists
 *
 * Pre-derivation the default was a magic `8` in three places
 * (`DataStoreAppSettings`, `FakeAppSettingsRepository`,
 * `ProfileViewModel`). The relationship between `8` and the body's
 * `safeListContentPadding.top` (currently 16 dp) was implicit and
 * fragile — a future bump of `Spacing.ListContentPadding.top` (e.g.
 * 16 → 24 dp to make sections feel more spacious) would silently
 * break rail alignment until someone smoke-tested on hardware.
 *
 * Now the relationship is explicit:
 * `DEFAULT_TOP_PADDING_DP = BODY_CONTENT_TOP_DP - RAIL_INTRINSIC_PILL_OFFSET_DP`.
 * `BODY_CONTENT_TOP_DP` must match `Spacing.ListContentPadding.top`
 * in `:androidApp` — pinned by `NavigationRailLayoutTest` so a
 * mismatch fails the build.
 *
 * ## The math
 *
 * ```
 * body's first content row top  =  BODY_CONTENT_TOP_DP  (from safeListContentPadding.top)
 *
 * rail's selected pill top      =  M3 NavigationRailVerticalPadding (4 dp)
 *                                + user-configurable extra padding (N dp)
 *                                + M3 NavigationRailItem internal pad before pill (~4 dp)
 *                                =  RAIL_INTRINSIC_PILL_OFFSET_DP + N
 *
 * Setting them equal:           BODY_CONTENT_TOP_DP = RAIL_INTRINSIC_PILL_OFFSET_DP + N
 * Solving for N (the default):  N = BODY_CONTENT_TOP_DP - RAIL_INTRINSIC_PILL_OFFSET_DP
 * ```
 *
 * ## Drift risk
 *
 * `RAIL_INTRINSIC_PILL_OFFSET_DP` is empirical — it captures two M3
 * internal padding constants (`NavigationRailVerticalPadding` plus
 * `NavigationRailItem`'s own pre-indicator padding) that are NOT
 * exposed by `androidx.compose.material3`. If a future M3 update
 * changes either, this constant must be updated to keep alignment.
 * Verified empirically through 2.16.30 (Material 3 1.4.x).
 */
object NavigationRailLayout {
    /**
     * Body's first content row top, in dp. Must match
     * `Spacing.ListContentPadding.top` in `:androidApp`.
     * Pinned by `NavigationRailLayoutTest`.
     */
    const val BODY_CONTENT_TOP_DP: Int = 16

    /**
     * Sum of M3 NavigationRail's internal vertical padding above the
     * first item (`NavigationRailVerticalPadding` = 4 dp) and the
     * NavigationRailItem's own internal padding above the
     * selected-indicator pill (~4 dp; undocumented in M3 source).
     * Empirical. See class KDoc for drift risk.
     */
    const val RAIL_INTRINSIC_PILL_OFFSET_DP: Int = 8

    /**
     * Default user-configurable extra padding above the rail items so
     * the selected pill aligns with body content. Derived; not magic.
     * User can override via Settings → Developer → Nav rail.
     */
    const val DEFAULT_TOP_PADDING_DP: Int = BODY_CONTENT_TOP_DP - RAIL_INTRINSIC_PILL_OFFSET_DP
}
