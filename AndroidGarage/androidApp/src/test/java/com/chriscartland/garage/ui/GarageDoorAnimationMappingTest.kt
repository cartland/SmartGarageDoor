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

package com.chriscartland.garage.ui

import com.chriscartland.garage.domain.model.DoorPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the door-animation mapping contract. See `AndroidGarage/docs/DOOR_ANIMATION.md`.
 *
 * Each mapping is an exhaustive `when` over [DoorPosition], so adding a new
 * enum value fails to compile until every mapping is updated. These tests pin
 * the *values* so a wrong update is also caught.
 */
class GarageDoorAnimationMappingTest {
    // --- targetPositionFor ----------------------------------------------------

    @Test
    fun targetPositionFor_unknown_isMidway() {
        assertEquals(MIDWAY_POSITION, DoorAnimation.targetPositionFor(DoorPosition.UNKNOWN))
    }

    @Test
    fun targetPositionFor_closed_isClosed() {
        assertEquals(CLOSED_POSITION, DoorAnimation.targetPositionFor(DoorPosition.CLOSED))
    }

    @Test
    fun targetPositionFor_opening_isOpen() {
        assertEquals(OPEN_POSITION, DoorAnimation.targetPositionFor(DoorPosition.OPENING))
    }

    @Test
    fun targetPositionFor_openingTooLong_isMidway() {
        assertEquals(MIDWAY_POSITION, DoorAnimation.targetPositionFor(DoorPosition.OPENING_TOO_LONG))
    }

    @Test
    fun targetPositionFor_open_isOpen() {
        assertEquals(OPEN_POSITION, DoorAnimation.targetPositionFor(DoorPosition.OPEN))
    }

    @Test
    fun targetPositionFor_openMisaligned_isOpen() {
        assertEquals(OPEN_POSITION, DoorAnimation.targetPositionFor(DoorPosition.OPEN_MISALIGNED))
    }

    @Test
    fun targetPositionFor_closing_isClosed() {
        assertEquals(CLOSED_POSITION, DoorAnimation.targetPositionFor(DoorPosition.CLOSING))
    }

    @Test
    fun targetPositionFor_closingTooLong_isMidway() {
        assertEquals(MIDWAY_POSITION, DoorAnimation.targetPositionFor(DoorPosition.CLOSING_TOO_LONG))
    }

    @Test
    fun targetPositionFor_errorSensorConflict_isMidway() {
        assertEquals(MIDWAY_POSITION, DoorAnimation.targetPositionFor(DoorPosition.ERROR_SENSOR_CONFLICT))
    }

    // --- initialPositionFor ---------------------------------------------------

    @Test
    fun initialPositionFor_alwaysEqualsTarget() {
        // Per 2.16.4: every state's initial == target. Motion animations
        // (OPENING/CLOSING tweens) only fire when doorPosition CHANGES during
        // the icon's lifetime, not on every fresh composition. The arrow
        // overlays carry the "in motion" cue without re-animating.
        for (state in DoorPosition.entries) {
            assertEquals(
                "Initial position for $state should equal target",
                DoorAnimation.targetPositionFor(state),
                DoorAnimation.initialPositionFor(state),
            )
        }
    }

    // --- useSpringFor ---------------------------------------------------------

    @Test
    fun useSpringFor_motionStates_useTween() {
        assertEquals(false, DoorAnimation.useSpringFor(DoorPosition.OPENING))
        assertEquals(false, DoorAnimation.useSpringFor(DoorPosition.CLOSING))
    }

    @Test
    fun useSpringFor_nonMotionStates_useSpring() {
        for (state in nonMotionStates) {
            assertTrue("$state should use spring", DoorAnimation.useSpringFor(state))
        }
    }

    // --- staticPositionFor ----------------------------------------------------

    @Test
    fun staticPositionFor_opening_isOpeningSnapshot() {
        // Static snapshot of OPENING should look "in motion" (mid-cycle), not
        // identical to OPEN.
        assertEquals(OPENING_STATIC_POSITION, DoorAnimation.staticPositionFor(DoorPosition.OPENING))
    }

    @Test
    fun staticPositionFor_closing_isClosingSnapshot() {
        assertEquals(CLOSING_STATIC_POSITION, DoorAnimation.staticPositionFor(DoorPosition.CLOSING))
    }

    @Test
    fun staticPositionFor_nonMotionStates_matchTarget() {
        for (state in nonMotionStates) {
            assertEquals(
                "Static position for $state should equal target",
                DoorAnimation.targetPositionFor(state),
                DoorAnimation.staticPositionFor(state),
            )
        }
    }

    // --- overlayFor -----------------------------------------------------------

    @Test
    fun overlayFor_opening_isArrowUp() {
        assertEquals(OverlayKind.ARROW_UP, DoorAnimation.overlayFor(DoorPosition.OPENING))
    }

    @Test
    fun overlayFor_closing_isArrowDown() {
        assertEquals(OverlayKind.ARROW_DOWN, DoorAnimation.overlayFor(DoorPosition.CLOSING))
    }

    @Test
    fun overlayFor_warningStates_areWarning() {
        val warningStates = listOf(
            DoorPosition.UNKNOWN,
            DoorPosition.OPENING_TOO_LONG,
            DoorPosition.CLOSING_TOO_LONG,
            DoorPosition.ERROR_SENSOR_CONFLICT,
        )
        for (state in warningStates) {
            assertEquals(
                "$state should use warning overlay",
                OverlayKind.WARNING,
                DoorAnimation.overlayFor(state),
            )
        }
    }

    @Test
    fun overlayFor_terminalStates_areNone() {
        val terminalStates = listOf(
            DoorPosition.CLOSED,
            DoorPosition.OPEN,
            DoorPosition.OPEN_MISALIGNED,
        )
        for (state in terminalStates) {
            assertEquals("$state should have no overlay", OverlayKind.NONE, DoorAnimation.overlayFor(state))
        }
    }

    // --- Exhaustiveness sanity checks ----------------------------------------

    @Test
    fun everyDoorPosition_hasValidTarget() {
        // The `when` is exhaustive at compile time; this is a runtime sanity
        // check that every value falls in the allowed offset range.
        for (state in DoorPosition.values()) {
            val target = DoorAnimation.targetPositionFor(state)
            assertTrue(
                "$state target $target outside valid offset range [OPEN_POSITION, CLOSED_POSITION]",
                target in OPEN_POSITION..CLOSED_POSITION,
            )
        }
    }

    @Test
    fun everyDoorPosition_hasOverlay() {
        // Total function — no DoorPosition produces a null/missing overlay.
        for (state in DoorPosition.values()) {
            assertNotNull("$state has no overlay", DoorAnimation.overlayFor(state))
        }
    }

    private val nonMotionStates = DoorPosition
        .values()
        .filter { it != DoorPosition.OPENING && it != DoorPosition.CLOSING }
}
