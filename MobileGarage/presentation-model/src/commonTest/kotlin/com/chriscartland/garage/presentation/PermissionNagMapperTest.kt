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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PermissionNagMapperTest {
    @Test
    fun baseLineIsAlwaysPresent() {
        // Including at zero and at absurd counts — the banner must never be empty.
        listOf(0, 1, 2, 3, 4, 5, 50, Int.MAX_VALUE).forEach { count ->
            assertTrue(
                PermissionNagMapper.linesFor(count).contains(PermissionNagLine.BASE),
                "attemptCount $count dropped the base line",
            )
        }
    }

    @Test
    fun escalationIsCumulative() {
        // Each step is a superset of the one before, so the banner only grows.
        // This is the property the two hand-written ladders were relied on to
        // have; nothing checked it before.
        (0..8).forEach { count ->
            val smaller = PermissionNagMapper.linesFor(count).toSet()
            val larger = PermissionNagMapper.linesFor(count + 1).toSet()
            assertTrue(
                larger.containsAll(smaller),
                "going from $count to ${count + 1} attempts dropped a line",
            )
        }
    }

    @Test
    fun thresholdsMatchTheShippedLadder() {
        // Pins the exact boundaries both platforms shipped: settings at 3+,
        // repeated-denial at 4+, the tap count at 5+.
        assertEquals(listOf(PermissionNagLine.BASE), PermissionNagMapper.linesFor(2))
        assertEquals(
            listOf(PermissionNagLine.BASE, PermissionNagLine.MENTION_SETTINGS),
            PermissionNagMapper.linesFor(3),
        )
        assertEquals(
            listOf(
                PermissionNagLine.BASE,
                PermissionNagLine.MENTION_SETTINGS,
                PermissionNagLine.MENTION_REPEATED_DENIAL,
            ),
            PermissionNagMapper.linesFor(4),
        )
        assertEquals(
            listOf(
                PermissionNagLine.BASE,
                PermissionNagLine.MENTION_SETTINGS,
                PermissionNagLine.MENTION_REPEATED_DENIAL,
                PermissionNagLine.ATTEMPT_COUNT,
            ),
            PermissionNagMapper.linesFor(5),
        )
    }

    @Test
    fun linesAreOrderedAndUnique() {
        // Display order is part of the contract — the platform just joins them.
        val lines = PermissionNagMapper.linesFor(10)
        assertEquals(lines.distinct(), lines, "a line was emitted twice")
        assertEquals(
            lines.sortedBy { PermissionNagLine.entries.indexOf(it) },
            lines,
            "lines must come out in declaration order",
        )
    }
}
