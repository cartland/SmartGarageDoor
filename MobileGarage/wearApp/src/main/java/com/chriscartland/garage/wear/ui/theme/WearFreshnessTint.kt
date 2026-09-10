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

package com.chriscartland.garage.wear.ui.theme

import androidx.compose.ui.graphics.Color
import com.chriscartland.garage.presentation.DataFreshness
import com.chriscartland.garage.presentation.FreshnessTint

/**
 * The watch's wrapper around the shared [FreshnessTint].
 *
 * A near-copy of the phone's `com.chriscartland.garage.ui.theme.FreshnessTint`
 * — deliberately, because `wearApp` cannot see `androidApp` and the alternative
 * is a Compose-typed module neither app needs. The DUPLICATION IS THE THREE
 * LINES OF `Color` PLUMBING; the numbers that decide how grey and how dim live
 * once, in [FreshnessTint], so the two screens cannot drift apart on the thing
 * a user would actually notice.
 */
object WearFreshnessTint {
    /** [FreshnessTint.alphaFor], re-exported so callers need one import. */
    fun alphaFor(freshness: DataFreshness): Float = FreshnessTint.alphaFor(freshness)

    /** [color] with its hue drained out, keeping its perceived lightness. */
    fun desaturate(color: Color): Color {
        val luma = FreshnessTint.luma(color.red, color.green, color.blue)
        return Color(red = luma, green = luma, blue = luma, alpha = color.alpha)
    }

    /** [desaturate] applied only when [freshness] is muted. */
    fun tint(
        color: Color,
        freshness: DataFreshness,
    ): Color = if (freshness.isMuted) desaturate(color) else color
}
