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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the door-geometry layout *relationships* (not just literal values) so a
 * fat-finger edit on the single shared source is caught on every platform.
 * Runs in `commonTest`, so it guards the Tier-1 brand geometry for Android and
 * iOS alike (ADR-032).
 */
class GarageDoorGeometryTest {
    @Test
    fun clipInsetIsDerivedFromFrameAndGap() {
        // frame inset (10) + half stroke (6) + panel gap (6) = 22.
        assertEquals(22f, GarageDoorGeometry.CLIP_INSET)
        assertEquals(
            GarageDoorGeometry.FRAME_INSET +
                GarageDoorGeometry.FRAME_STROKE_WIDTH / 2f +
                GarageDoorGeometry.PANEL_GAP,
            GarageDoorGeometry.CLIP_INSET,
        )
    }

    @Test
    fun fourPanelsEvenlySpaced() {
        val ys = GarageDoorGeometry.PANEL_Y_STARTS
        assertEquals(listOf(22f, 89f, 156f, 223f), ys)
        // Each panel starts one (panel height + gap) below the previous.
        val step = GarageDoorGeometry.PANEL_HEIGHT + GarageDoorGeometry.PANEL_GAP // 67
        assertEquals(listOf(step, step, step), ys.zipWithNext { a, b -> b - a })
    }

    @Test
    fun panelsAndHandleFitWithinViewport() {
        // Panels are centered horizontally within the viewport.
        assertEquals(
            GarageDoorGeometry.VP - GarageDoorGeometry.PANEL_X,
            GarageDoorGeometry.PANEL_X + GarageDoorGeometry.PANEL_WIDTH,
        )
        // Last panel bottom stays above the frame bottom.
        val lastPanelBottom = GarageDoorGeometry.PANEL_Y_STARTS.last() + GarageDoorGeometry.PANEL_HEIGHT
        assertTrue(lastPanelBottom <= GarageDoorGeometry.FRAME_BOTTOM)
        // Handle sits on the bottom panel.
        assertTrue(GarageDoorGeometry.HANDLE_Y >= GarageDoorGeometry.PANEL_Y_STARTS.last())
        assertTrue(
            GarageDoorGeometry.HANDLE_Y <=
                GarageDoorGeometry.PANEL_Y_STARTS.last() + GarageDoorGeometry.PANEL_HEIGHT,
        )
    }
}
