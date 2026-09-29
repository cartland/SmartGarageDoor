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

/** How the widget's two lines are arranged at a given size. */
enum class WidgetLayout {
    /** Headline over subline: the two-cell widget. */
    STACKED,

    /** Headline and subline on one row: the four-cell widget. */
    INLINE,
}

/**
 * The widget's sizes, and the one layout decision that depends on them.
 *
 * The provider has declared the widget resizable from the start
 * (`garage_door_widget_info.xml`), but every size got the 2×1 layout because
 * the widget composed once with [androidx.glance.appwidget.SizeMode.Single].
 * With [androidx.glance.appwidget.SizeMode.Responsive] Glance composes the body
 * once per size in [SIZES] and the launcher shows the largest that fits, so a
 * widget stretched to four cells finally uses them (strategy 3.2).
 *
 * The sizes are Android's cell arithmetic (70 dp × n − 30 dp), which is also
 * what `minWidth`/`minHeight` in the provider say. Decided here, on plain
 * values, so the JVM can pin it (`GarageWidgetLayoutTest`) and the drawing only
 * asks.
 */
object GarageWidgetLayout {
    /** Two cells by one: the placement default (`targetCellWidth`/`Height`). */
    val COMPACT: DpSize = DpSize(width = 110.dp, height = 40.dp)

    /** Four cells by one: room for both lines on a single row. */
    val WIDE: DpSize = DpSize(width = 250.dp, height = 40.dp)

    /** What [androidx.glance.appwidget.SizeMode.Responsive] is given. */
    val SIZES: Set<DpSize> = setOf(COMPACT, WIDE)

    /** Test tags on the container, so a unit test can see WHICH arrangement drew. */
    const val TAG_STACKED = "widget-stacked"
    const val TAG_INLINE = "widget-inline"

    /** Width alone decides: a taller two-cell widget still has no room beside the headline. */
    fun forSize(size: DpSize): WidgetLayout = if (size.width >= WIDE.width) WidgetLayout.INLINE else WidgetLayout.STACKED
}
