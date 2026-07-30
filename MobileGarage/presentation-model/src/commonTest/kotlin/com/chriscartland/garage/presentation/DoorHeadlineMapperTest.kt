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

class DoorHeadlineMapperTest {
    @Test
    fun everyPositionHasAHeadline() {
        // Guards the mapper against a DoorPosition added later: the `when` is
        // exhaustive so this cannot actually throw, but the assertion documents
        // that total coverage is the contract, not an accident.
        DoorPosition.entries.forEach { position ->
            DoorHeadlineMapper.forPosition(position)
        }
    }

    @Test
    fun anomalousVariantsShareTheirNormalHeadline() {
        // The core product rule: an anomaly changes the warning chip, not the
        // headline. A door that has been opening too long is still "opening".
        assertEquals(
            DoorHeadlineMapper.forPosition(DoorPosition.OPEN),
            DoorHeadlineMapper.forPosition(DoorPosition.OPEN_MISALIGNED),
        )
        assertEquals(
            DoorHeadlineMapper.forPosition(DoorPosition.OPENING),
            DoorHeadlineMapper.forPosition(DoorPosition.OPENING_TOO_LONG),
        )
        assertEquals(
            DoorHeadlineMapper.forPosition(DoorPosition.CLOSING),
            DoorHeadlineMapper.forPosition(DoorPosition.CLOSING_TOO_LONG),
        )
    }

    @Test
    fun ninePositionsCollapseToSixHeadlines() {
        val distinct = DoorPosition.entries.map { DoorHeadlineMapper.forPosition(it) }.toSet()
        assertEquals(6, distinct.size)
        // ...and every headline is reachable, so none is dead.
        assertEquals(DoorHeadline.entries.toSet(), distinct)
    }

    @Test
    fun openAndClosedNeverShareAHeadline() {
        // The one collapse that would be a real bug. Stated explicitly because
        // the "n positions to m headlines" count above would still pass if two
        // opposite states were merged and two others split.
        assertTrue(
            DoorHeadlineMapper.forPosition(DoorPosition.OPEN) !=
                DoorHeadlineMapper.forPosition(DoorPosition.CLOSED),
        )
        assertTrue(
            DoorHeadlineMapper.forPosition(DoorPosition.OPENING) !=
                DoorHeadlineMapper.forPosition(DoorPosition.CLOSING),
        )
    }
}
