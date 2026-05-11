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

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pin the asymmetry rule on `Spacing.ListContentPadding` (top vs bottom)
 * so a future "tidy-up" doesn't silently make it symmetric.
 *
 * Rule documented in `AndroidGarage/docs/SPACING_PLAN.md` (Top vs.
 * bottom asymmetry section). Top is below the TopAppBar (chrome already
 * provides separation, minimal list padding needed). Bottom is above
 * the NavigationBar (last item scrolls INTO that boundary; needs
 * explicit clearance).
 *
 * If you intentionally change the values, update both this test and
 * the doc — the asymmetry direction is the rule, not the specific dp
 * values.
 */
class SpacingTest {
    @Test
    fun listContentPaddingTopIsTighterThanBottom() {
        val pv = Spacing.ListContentPadding
        val top = pv.calculateTopPadding()
        val bottom = pv.calculateBottomPadding()

        assertTrue(
            "Spacing.ListContentPadding top ($top) must be < bottom ($bottom). " +
                "Asymmetry rule: see AndroidGarage/docs/SPACING_PLAN.md.",
            bottom > top,
        )
    }

    @Test
    fun listContentPaddingValues() {
        // Pin the current values so an accidental rename / refactor that
        // changes the dp literals also updates the doc and CHANGELOG.
        // Top bumped 8 → 16dp in 2.16.29: SectionHeaderTop deleted; the
        // first-item gap is now owned exclusively by ListContentPadding.top
        // (parent-owns rule). Bottom unchanged.
        val pv = Spacing.ListContentPadding
        assertEquals(16.dp, pv.calculateTopPadding())
        assertEquals(24.dp, pv.calculateBottomPadding())
    }
}
