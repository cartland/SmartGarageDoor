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
 * Where the [androidx.compose.material3.NavigationRail] tab items sit
 * vertically inside the rail (Wide layout mode only — Compact uses the
 * bottom bar; Expanded has no rail).
 *
 * Default [CenteredVertically] reflects the current production behavior
 * (M3-canonical for rails with few items — see Gmail / YouTube tablet
 * layouts; CLAUDE.md captures the rationale and the previous "top-align
 * fights M3 internal padding" trap from the 2.16.10 → 2.16.11 iteration).
 *
 * Toggled via Settings → Developer → "Nav rail items" so the layout is
 * adjustable on real hardware without a rebuild. Persists across launches
 * via [com.chriscartland.garage.domain.repository.AppSettingsRepository.navigationRailItemPosition].
 */
enum class NavigationRailItemPosition {
    /**
     * Items sit in the rail's vertical center via two `weight(1f)`
     * Spacers wrapping the items inside the rail's `ColumnScope`.
     * Default — chrome-vs-content alignment is intentionally NOT
     * compared eye-to-eye (items sit in their own vertical zone).
     */
    CenteredVertically,

    /**
     * Items sit at the rail's top so the first visible item lands in
     * the same horizontal band as the body's first content row. Single
     * `weight(1f)` Spacer below the items pushes them upward.
     */
    TopAligned,
}
