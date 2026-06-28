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
 * Shared garage-door **fill** palette — the single source of truth for the
 * brand-locked door colors rendered by **both** Android (Jetpack Compose
 * `Color.kt` → `DoorStatusColorScheme`) and iOS (SwiftUI `GarageDoorCanvas.swift`
 * `DoorPalette`).
 *
 * Each value is an opaque `0xFFRRGGBB` ARGB color (the high alpha byte is always
 * `0xFF`). Android consumes them directly via `Color(Long)`; iOS masks to the
 * low 24 bits and builds `Color(rgb:)`. Per ADR-032 the door visualization is a
 * **Tier 1 (brand-locked)** surface, so these colors must not drift between
 * platforms — they previously lived as hand-duplicated literals in each
 * platform's source. The structural invariants (opacity; gray has no "stale"
 * variant; colored states do) are pinned by `GarageDoorPaletteTest`.
 *
 * Scope is the door *fill* only. Android's matching `on*` foreground/text colors
 * are platform-local (iOS draws overlays with the system label color), so they
 * are deliberately **not** hoisted here. Sibling of the geometry source
 * [GarageDoorGeometry]; follows the `AppLinks` shared-constant precedent.
 *
 * Naming: `<state>_<freshness>_<scheme>`. State = closed (green) / open (red) /
 * unknown (gray). Freshness = fresh (recent device check-in) / stale (old).
 * Scheme = light / dark.
 */
object GarageDoorPalette {
    // Closed — variations of green.
    const val CLOSED_FRESH_LIGHT: Long = 0xFF226B43
    const val CLOSED_FRESH_DARK: Long = 0xFF25673C
    const val CLOSED_STALE_LIGHT: Long = 0xFF456C54
    const val CLOSED_STALE_DARK: Long = 0xFF40694F

    // Open — variations of red.
    const val OPEN_FRESH_LIGHT: Long = 0xFF932F1E
    const val OPEN_FRESH_DARK: Long = 0xFF7A2B1E
    const val OPEN_STALE_LIGHT: Long = 0xFF9A655C
    const val OPEN_STALE_DARK: Long = 0xFF7A524B

    // Unknown — variations of gray. No distinct stale variant (see test).
    const val UNKNOWN_FRESH_LIGHT: Long = 0xFF444444
    const val UNKNOWN_FRESH_DARK: Long = 0xFF555555
    const val UNKNOWN_STALE_LIGHT: Long = 0xFF444444
    const val UNKNOWN_STALE_DARK: Long = 0xFF555555
}
