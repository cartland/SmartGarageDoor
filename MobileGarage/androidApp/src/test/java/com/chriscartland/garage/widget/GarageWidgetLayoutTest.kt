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

package com.chriscartland.garage.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.SizeMode
import org.junit.Assert.assertEquals
import org.junit.Test

/** The layout decision, pinned on plain sizes so the drawing can only ask. */
class GarageWidgetLayoutTest {
    @Test
    fun twoCellsStackAndFourCellsShareARow() {
        assertEquals(WidgetLayout.STACKED, GarageWidgetLayout.forSize(GarageWidgetLayout.COMPACT))
        assertEquals(WidgetLayout.INLINE, GarageWidgetLayout.forSize(GarageWidgetLayout.WIDE))
    }

    @Test
    fun widthAloneDecidesAndTheBoundaryIsTheWideWidth() {
        val justNarrower = DpSize(GarageWidgetLayout.WIDE.width - 1.dp, GarageWidgetLayout.WIDE.height)
        assertEquals(WidgetLayout.STACKED, GarageWidgetLayout.forSize(justNarrower))
        // A taller two-cell widget still has no room beside the headline.
        val tallCompact = DpSize(GarageWidgetLayout.COMPACT.width, 120.dp)
        assertEquals(WidgetLayout.STACKED, GarageWidgetLayout.forSize(tallCompact))
        // Wider than declared (a launcher with roomier cells) still shares the row.
        val roomier = DpSize(GarageWidgetLayout.WIDE.width + 40.dp, GarageWidgetLayout.WIDE.height)
        assertEquals(WidgetLayout.INLINE, GarageWidgetLayout.forSize(roomier))
    }

    @Test
    fun everyArrangementIsReachableFromTheDeclaredSizes() {
        // A layout no declared size reaches is dead drawing; a size that
        // reaches none would compose nothing.
        assertEquals(WidgetLayout.entries.toSet(), GarageWidgetLayout.SIZES.map { GarageWidgetLayout.forSize(it) }.toSet())
    }

    @Test
    fun theWidgetComposesOncePerDeclaredSize() {
        // The rule above is inert unless the widget asks Glance for both
        // compositions; SizeMode.Single would draw the two-cell layout at
        // every size and every test here would still pass.
        assertEquals(SizeMode.Responsive(GarageWidgetLayout.SIZES), GarageDoorWidget().sizeMode)
    }

    @Test
    fun theSizesAreTheProviderCellArithmetic() {
        // 70 dp x n - 30 dp, the same numbers minWidth/minHeight in
        // garage_door_widget_info.xml are written from.
        assertEquals(DpSize(110.dp, 40.dp), GarageWidgetLayout.COMPACT)
        assertEquals(DpSize(250.dp, 40.dp), GarageWidgetLayout.WIDE)
    }
}
