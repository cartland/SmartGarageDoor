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

package com.chriscartland.garage.presentation

import com.chriscartland.garage.domain.model.DoorPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The escalation that used to happen on the watch only.
 *
 * Android and iOS said "Connecting…" forever on an empty cache and raised no
 * banner either — `HomeAlertMapper` has an arm for a stale check-in and one
 * for a failed fetch, but none for *never heard anything* — so the settle
 * window's "then show more indicators" half simply did not happen there.
 */
class StatusHeadlineMapperTest {
    @Test
    fun aKnownDoorIsNamed() {
        assertEquals(
            StatusHeadline.Door(DoorHeadline.OPEN),
            StatusHeadlineMapper.forDoor(DoorPosition.OPEN, DataFreshness.FRESH),
        )
    }

    @Test
    fun anEmptyCacheSaysConnectingWhileTheWindowIsOpen() {
        assertEquals(
            StatusHeadline.Connecting,
            StatusHeadlineMapper.forDoor(null, DataFreshness.SETTLING),
        )
    }

    @Test
    fun anEmptyCacheSaysNoSignalOnceTheWindowHasClosed() {
        assertEquals(
            StatusHeadline.NoSignal,
            StatusHeadlineMapper.forDoor(null, DataFreshness.STALE),
        )
    }

    /**
     * A reading we HAVE keeps being named even when the verdict is spoken —
     * the card goes muted and the banner appears, but the headline still says
     * what the door was last known to be doing.
     *
     * Replacing a known position with "No signal" would throw away the last
     * thing we actually know at the moment it becomes most useful. This is the
     * assertion that stops a future "simplify" from keying the headline on
     * `freshness` alone.
     */
    @Test
    fun aKnownDoorIsStillNamedWhenTheVerdictIsSpoken() {
        assertEquals(
            StatusHeadline.Door(DoorHeadline.CLOSED),
            StatusHeadlineMapper.forDoor(DoorPosition.CLOSED, DataFreshness.STALE),
        )
    }

    /**
     * The file's positive control: the mapper must be able to return more than
     * one thing, discriminating on EACH input independently.
     *
     * Without this, a `forDoor` that had collapsed to always-`Connecting`
     * would satisfy one of the tests above outright, and the shape of the rest
     * makes it easy to write a suite that never proves the two inputs both
     * matter.
     */
    @Test
    fun bothInputsChangeTheAnswer() {
        assertTrue(
            StatusHeadlineMapper.forDoor(null, DataFreshness.SETTLING) !=
                StatusHeadlineMapper.forDoor(null, DataFreshness.STALE),
            "freshness alone must change the headline when there is no door",
        )
        assertTrue(
            StatusHeadlineMapper.forDoor(null, DataFreshness.STALE) !=
                StatusHeadlineMapper.forDoor(DoorPosition.OPEN, DataFreshness.STALE),
            "having a reading must change the headline at the same freshness",
        )
    }

    /**
     * Every door position maps to a `Door` headline, never to one of the
     * no-data answers — those two are reachable only through a null position.
     */
    @Test
    fun noDoorPositionEverReportsNoSignal() {
        for (position in DoorPosition.entries) {
            val headline = StatusHeadlineMapper.forDoor(position, DataFreshness.STALE)
            assertTrue(
                headline is StatusHeadline.Door,
                "$position should name the door, got $headline",
            )
        }
    }
}
