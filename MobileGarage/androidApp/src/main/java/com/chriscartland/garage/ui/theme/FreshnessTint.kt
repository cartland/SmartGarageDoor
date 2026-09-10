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
import com.chriscartland.garage.presentation.DataFreshness
import com.chriscartland.garage.presentation.FreshnessTint as SharedFreshnessTint

/**
 * Compose's wrapper around the shared [SharedFreshnessTint] — the arithmetic
 * of "grey and dim" is shared with iOS and Wear so all three drain the door's
 * colour by the same amount; only the `Color` type is local.
 *
 * Named object per ADR-009. The Wear app has its own three-line twin
 * (`WearFreshnessTint`) because it cannot see this module; both defer to the
 * same shared weights, which is what keeps them from drifting.
 */
object FreshnessTint {
    /** [SharedFreshnessTint.alphaFor], re-exported so callers need one import. */
    fun alphaFor(freshness: DataFreshness): Float = SharedFreshnessTint.alphaFor(freshness)

    /** [color] with its hue drained out, keeping its perceived lightness. */
    fun desaturate(color: Color): Color {
        val luma = SharedFreshnessTint.luma(color.red, color.green, color.blue)
        return Color(red = luma, green = luma, blue = luma, alpha = color.alpha)
    }

    /** [desaturate] applied only when [freshness] is muted. */
    fun tint(
        color: Color,
        freshness: DataFreshness,
    ): Color = if (freshness.isMuted) desaturate(color) else color
}
