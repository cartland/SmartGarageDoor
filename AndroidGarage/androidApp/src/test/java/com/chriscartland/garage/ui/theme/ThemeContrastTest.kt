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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies WCAG AA contrast ratios for all color pairs in the app theme.
 *
 * This test automatically catches contrast issues when:
 * - Theme colors are changed
 * - New color roles are added to the theme
 * - Door status colors are adjusted
 *
 * Any component using theme colors is automatically covered.
 * Custom colors outside the theme are banned by the build-time import check.
 */
class ThemeContrastTest {
    /**
     * A named color pair: foreground on background.
     * The [name] appears in failure messages for easy diagnosis.
     */
    private data class ColorPair(
        val name: String,
        val foreground: Color,
        val background: Color,
        val minRatio: Double = ContrastCheck.WCAG_AA_NORMAL_TEXT,
    )

    // region Material3 color pairs

    private val lightM3Pairs = listOf(
        ColorPair("light: onPrimary / primary", onPrimaryLight, primaryLight),
        ColorPair("light: onPrimaryContainer / primaryContainer", onPrimaryContainerLight, primaryContainerLight),
        ColorPair("light: onSecondary / secondary", onSecondaryLight, secondaryLight),
        ColorPair("light: onSecondaryContainer / secondaryContainer", onSecondaryContainerLight, secondaryContainerLight),
        ColorPair("light: onTertiary / tertiary", onTertiaryLight, tertiaryLight),
        ColorPair("light: onTertiaryContainer / tertiaryContainer", onTertiaryContainerLight, tertiaryContainerLight),
        ColorPair("light: onError / error", onErrorLight, errorLight),
        ColorPair("light: onErrorContainer / errorContainer", onErrorContainerLight, errorContainerLight),
        ColorPair("light: onBackground / background", onBackgroundLight, backgroundLight),
        ColorPair("light: onSurface / surface", onSurfaceLight, surfaceLight),
        ColorPair("light: onSurfaceVariant / surfaceVariant", onSurfaceVariantLight, surfaceVariantLight),
    )

    private val darkM3Pairs = listOf(
        ColorPair("dark: onPrimary / primary", onPrimaryDark, primaryDark),
        ColorPair("dark: onPrimaryContainer / primaryContainer", onPrimaryContainerDark, primaryContainerDark),
        ColorPair("dark: onSecondary / secondary", onSecondaryDark, secondaryDark),
        ColorPair("dark: onSecondaryContainer / secondaryContainer", onSecondaryContainerDark, secondaryContainerDark),
        ColorPair("dark: onTertiary / tertiary", onTertiaryDark, tertiaryDark),
        ColorPair("dark: onTertiaryContainer / tertiaryContainer", onTertiaryContainerDark, tertiaryContainerDark),
        ColorPair("dark: onError / error", onErrorDark, errorDark),
        ColorPair("dark: onErrorContainer / errorContainer", onErrorContainerDark, errorContainerDark),
        ColorPair("dark: onBackground / background", onBackgroundDark, backgroundDark),
        ColorPair("dark: onSurface / surface", onSurfaceDark, surfaceDark),
        ColorPair("dark: onSurfaceVariant / surfaceVariant", onSurfaceVariantDark, surfaceVariantDark),
    )

    // endregion

    // region Door status color pairs

    private val lightDoorPairs = listOf(
        ColorPair("light: onClosedFresh / closedFresh", onClosedFreshLight, closedFreshLight),
        ColorPair("light: onClosedStale / closedStale", onClosedStaleLight, closedStaleLight),
        ColorPair("light: onOpenFresh / openFresh", onOpenFreshLight, openFreshLight),
        ColorPair("light: onOpenStale / openStale", onOpenStaleLight, openStaleLight),
        ColorPair("light: onUnknownFresh / unknownFresh", onUnknownFreshLight, unknownFreshLight),
        ColorPair("light: onUnknownStale / unknownStale", onUnknownStaleLight, unknownStaleLight),
    )

    private val darkDoorPairs = listOf(
        ColorPair("dark: onClosedFresh / closedFresh", onClosedFreshDark, closedFreshDark),
        ColorPair("dark: onClosedStale / closedStale", onClosedStaleDark, closedStaleDark),
        ColorPair("dark: onOpenFresh / openFresh", onOpenFreshDark, openFreshDark),
        ColorPair("dark: onOpenStale / openStale", onOpenStaleDark, openStaleDark),
        ColorPair("dark: onUnknownFresh / unknownFresh", onUnknownFreshDark, unknownFreshDark),
        ColorPair("dark: onUnknownStale / unknownStale", onUnknownStaleDark, unknownStaleDark),
    )

    // endregion

    private val allPairs = lightM3Pairs + darkM3Pairs + lightDoorPairs + darkDoorPairs

    @Test
    fun allThemeColorPairsMeetWcagAA() {
        val failures = allPairs.mapNotNull { pair ->
            val ratio = ContrastCheck.contrastRatio(pair.foreground, pair.background)
            if (ratio < pair.minRatio) {
                "${pair.name}: ratio %.2f < %.1f".format(ratio, pair.minRatio)
            } else {
                null
            }
        }
        assertTrue(
            "WCAG AA contrast failures:\n${failures.joinToString("\n")}",
            failures.isEmpty(),
        )
    }

    @Test
    fun contrastCheckUtilityBlackOnWhiteIsMaximum() {
        val ratio = ContrastCheck.contrastRatio(Color.Black, Color.White)
        assertTrue("Black on white should be 21:1, got $ratio", ratio > 20.9)
    }

    @Test
    fun contrastCheckUtilityIdenticalColorsIsMinimum() {
        val ratio = ContrastCheck.contrastRatio(Color.Red, Color.Red)
        assertTrue("Same color should be 1:1, got $ratio", ratio < 1.01)
    }
}
