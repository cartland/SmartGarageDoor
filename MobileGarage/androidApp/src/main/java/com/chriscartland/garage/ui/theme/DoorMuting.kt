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

import androidx.compose.ui.graphics.Color
import com.chriscartland.garage.domain.model.DoorColorState
import com.chriscartland.garage.presentation.DataFreshness

/**
 * The door's fill for a [DataFreshness], from ONE base colour.
 *
 * Until strategy 1.2 the phone picked the palette's `_STALE_` variant when
 * the check-in was stale and THEN drained it through [FreshnessTint], but
 * drained the `_FRESH_` variant on a fetch error — two greys for one verdict
 * on one card, and neither matched the watch (2026-09-28 audit, finding 3.1).
 * The muted grey is now always the fresh colour drained by luma and dimmed by
 * the shared alpha, on every pixel-owning surface. The `_STALE_` palette
 * entries are no longer read here; they are retired once nothing else does.
 *
 * Plain object rather than a Composable so the rule is pinned on the JVM
 * (`DoorMutingTest`), the way the tile's is.
 */
object DoorMuting {
    /** The fill: the fresh family colour, grey and dim when the verdict is muted. */
    fun doorColor(
        scheme: DoorStatusColorScheme,
        colorState: DoorColorState,
        freshness: DataFreshness,
    ): Color {
        val fresh = scheme.doorColorSet(isStale = false)
        val base = when (colorState) {
            DoorColorState.OPEN -> fresh.open
            DoorColorState.CLOSED -> fresh.closed
            DoorColorState.UNKNOWN -> fresh.unknown
        }
        return FreshnessTint.tint(base, freshness)
    }
}
