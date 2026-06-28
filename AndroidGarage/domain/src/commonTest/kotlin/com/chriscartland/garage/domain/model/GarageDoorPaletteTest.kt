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
import kotlin.test.assertNotEquals

/**
 * Pins the door-fill palette *structure* (not just literal values) so a
 * fat-finger edit on the single shared source is caught on every platform.
 * Runs in `commonTest`, so it guards the Tier-1 brand colors for Android and
 * iOS alike (ADR-032).
 */
class GarageDoorPaletteTest {
    private val all = listOf(
        GarageDoorPalette.CLOSED_FRESH_LIGHT,
        GarageDoorPalette.CLOSED_FRESH_DARK,
        GarageDoorPalette.CLOSED_STALE_LIGHT,
        GarageDoorPalette.CLOSED_STALE_DARK,
        GarageDoorPalette.OPEN_FRESH_LIGHT,
        GarageDoorPalette.OPEN_FRESH_DARK,
        GarageDoorPalette.OPEN_STALE_LIGHT,
        GarageDoorPalette.OPEN_STALE_DARK,
        GarageDoorPalette.UNKNOWN_FRESH_LIGHT,
        GarageDoorPalette.UNKNOWN_FRESH_DARK,
        GarageDoorPalette.UNKNOWN_STALE_LIGHT,
        GarageDoorPalette.UNKNOWN_STALE_DARK,
    )

    @Test
    fun everyDoorColorIsFullyOpaque() {
        // ARGB Longs — the door fill must never render translucent. The high
        // alpha byte is what Android's Color(Long) reads; iOS masks it off.
        all.forEach {
            assertEquals(0xFFL, (it ushr 24) and 0xFFL, "0x${it.toString(16)} is not opaque")
        }
    }

    @Test
    fun unknownHasNoStaleVariant() {
        // Gray (unknown) uses the same color whether the check-in is fresh or
        // stale; only the colored states get a muted "stale" variant.
        assertEquals(GarageDoorPalette.UNKNOWN_FRESH_LIGHT, GarageDoorPalette.UNKNOWN_STALE_LIGHT)
        assertEquals(GarageDoorPalette.UNKNOWN_FRESH_DARK, GarageDoorPalette.UNKNOWN_STALE_DARK)
    }

    @Test
    fun coloredStatesHaveDistinctStaleVariant() {
        assertNotEquals(GarageDoorPalette.CLOSED_FRESH_LIGHT, GarageDoorPalette.CLOSED_STALE_LIGHT)
        assertNotEquals(GarageDoorPalette.CLOSED_FRESH_DARK, GarageDoorPalette.CLOSED_STALE_DARK)
        assertNotEquals(GarageDoorPalette.OPEN_FRESH_LIGHT, GarageDoorPalette.OPEN_STALE_LIGHT)
        assertNotEquals(GarageDoorPalette.OPEN_FRESH_DARK, GarageDoorPalette.OPEN_STALE_DARK)
    }
}
