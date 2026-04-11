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

package com.chriscartland.garage.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * WCAG 2.1 contrast ratio utilities.
 *
 * Used by theme tests to verify all color pairs meet accessibility standards.
 */
object ContrastCheck {
    /** WCAG AA minimum contrast for normal text (< 18pt / < 14pt bold). */
    const val WCAG_AA_NORMAL_TEXT = 4.5

    /** WCAG AA minimum contrast for large text (>= 18pt / >= 14pt bold). */
    const val WCAG_AA_LARGE_TEXT = 3.0

    /**
     * Computes the WCAG 2.1 contrast ratio between two colors.
     * Returns a value between 1.0 (identical) and 21.0 (black on white).
     */
    fun contrastRatio(
        foreground: Color,
        background: Color,
    ): Double {
        val fgLum = relativeLuminance(foreground)
        val bgLum = relativeLuminance(background)
        val lighter = maxOf(fgLum, bgLum)
        val darker = minOf(fgLum, bgLum)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /**
     * Relative luminance per WCAG 2.1 definition.
     * https://www.w3.org/TR/WCAG21/#dfn-relative-luminance
     */
    private fun relativeLuminance(color: Color): Double {
        val r = linearize(color.red.toDouble())
        val g = linearize(color.green.toDouble())
        val b = linearize(color.blue.toDouble())
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun linearize(channel: Double): Double =
        if (channel <= 0.04045) {
            channel / 12.92
        } else {
            ((channel + 0.055) / 1.055).pow(2.4)
        }
}
