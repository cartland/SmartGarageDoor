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
 * Pins the door-animation mapping contract. See
 * `AndroidGarage/docs/DOOR_ANIMATION.md`.
 *
 * Each mapping is an exhaustive `when` over [DoorPosition], so adding a new enum
 * value fails to compile until every mapping is updated. These tests pin the
 * *values* so a wrong update is also caught. Runs in `commonTest`, so it guards
 * the Tier-1 door-animation spec for Android and iOS alike (ADR-032).
 */
class DoorAnimationTest {
    // --- targetPositionFor ----------------------------------------------------

    @Test
    fun targetPositionFor_unknown_isMidway() {
        assertEquals(DoorAnimation.MIDWAY_POSITION, DoorAnimation.targetPositionFor(DoorPosition.UNKNOWN))
    }

    @Test
    fun targetPositionFor_closed_isClosed() {
        assertEquals(DoorAnimation.CLOSED_POSITION, DoorAnimation.targetPositionFor(DoorPosition.CLOSED))
    }

    @Test
    fun targetPositionFor_opening_isOpen() {
        assertEquals(DoorAnimation.OPEN_POSITION, DoorAnimation.targetPositionFor(DoorPosition.OPENING))
    }

    @Test
    fun targetPositionFor_openingTooLong_isMidway() {
        assertEquals(DoorAnimation.MIDWAY_POSITION, DoorAnimation.targetPositionFor(DoorPosition.OPENING_TOO_LONG))
    }

    @Test
    fun targetPositionFor_open_isOpen() {
        assertEquals(DoorAnimation.OPEN_POSITION, DoorAnimation.targetPositionFor(DoorPosition.OPEN))
    }

    @Test
    fun targetPositionFor_openMisaligned_isOpen() {
        assertEquals(DoorAnimation.OPEN_POSITION, DoorAnimation.targetPositionFor(DoorPosition.OPEN_MISALIGNED))
    }

    @Test
    fun targetPositionFor_closing_isClosed() {
        assertEquals(DoorAnimation.CLOSED_POSITION, DoorAnimation.targetPositionFor(DoorPosition.CLOSING))
    }

    @Test
    fun targetPositionFor_closingTooLong_isMidway() {
        assertEquals(DoorAnimation.MIDWAY_POSITION, DoorAnimation.targetPositionFor(DoorPosition.CLOSING_TOO_LONG))
    }

    @Test
    fun targetPositionFor_errorSensorConflict_isMidway() {
        assertEquals(DoorAnimation.MIDWAY_POSITION, DoorAnimation.targetPositionFor(DoorPosition.ERROR_SENSOR_CONFLICT))
    }

    // --- fromPositionFor ------------------------------------------------------

    @Test
    fun fromPositionFor_opening_isClosedEnd() {
        // OPENING slides up out of CLOSED.
        assertEquals(DoorAnimation.CLOSED_POSITION, DoorAnimation.fromPositionFor(DoorPosition.OPENING))
    }

    @Test
    fun fromPositionFor_closing_isOpenEnd() {
        // CLOSING slides down out of OPEN.
        assertEquals(DoorAnimation.OPEN_POSITION, DoorAnimation.fromPositionFor(DoorPosition.CLOSING))
    }

    @Test
    fun fromPositionFor_nonMotionStates_matchTarget() {
        // Non-motion states have no distinct "from" end; the spring settles in
        // place, so the seed equals the target.
        for (state in nonMotionStates) {
            assertEquals(
                DoorAnimation.targetPositionFor(state),
                DoorAnimation.fromPositionFor(state),
                "From position for $state should equal target",
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
            assertTrue(DoorAnimation.useSpringFor(state), "$state should use spring")
        }
    }

    // --- staticPositionFor ----------------------------------------------------

    @Test
    fun staticPositionFor_opening_isOpeningSnapshot() {
        // Static snapshot of OPENING should look "in motion" (mid-cycle), not
        // identical to OPEN.
        assertEquals(DoorAnimation.OPENING_STATIC_POSITION, DoorAnimation.staticPositionFor(DoorPosition.OPENING))
    }

    @Test
    fun staticPositionFor_closing_isClosingSnapshot() {
        assertEquals(DoorAnimation.CLOSING_STATIC_POSITION, DoorAnimation.staticPositionFor(DoorPosition.CLOSING))
    }

    @Test
    fun staticPositionFor_nonMotionStates_matchTarget() {
        for (state in nonMotionStates) {
            assertEquals(
                DoorAnimation.targetPositionFor(state),
                DoorAnimation.staticPositionFor(state),
                "Static position for $state should equal target",
            )
        }
    }

    // --- overlayFor -----------------------------------------------------------

    @Test
    fun overlayFor_opening_isArrowUp() {
        assertEquals(DoorOverlayKind.ARROW_UP, DoorAnimation.overlayFor(DoorPosition.OPENING))
    }

    @Test
    fun overlayFor_closing_isArrowDown() {
        assertEquals(DoorOverlayKind.ARROW_DOWN, DoorAnimation.overlayFor(DoorPosition.CLOSING))
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
                DoorOverlayKind.WARNING,
                DoorAnimation.overlayFor(state),
                "$state should use warning overlay",
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
            assertEquals(DoorOverlayKind.NONE, DoorAnimation.overlayFor(state), "$state should have no overlay")
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
                target in DoorAnimation.OPEN_POSITION..DoorAnimation.CLOSED_POSITION,
                "$state target $target outside valid offset range [OPEN_POSITION, CLOSED_POSITION]",
            )
        }
    }

    private val nonMotionStates = DoorPosition
        .values()
        .filter { it != DoorPosition.OPENING && it != DoorPosition.CLOSING }
}
