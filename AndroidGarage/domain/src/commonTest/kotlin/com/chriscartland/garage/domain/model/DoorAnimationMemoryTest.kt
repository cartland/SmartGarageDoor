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

package com.chriscartland.garage.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the shared **replay policy** — `DoorAnimationMemory.consumeAnimateFromStart`
 * returns `true` exactly once per distinct [DoorMotionKey], `false` thereafter.
 * Runs in `commonTest`, so the "animate the slide from the start once per motion
 * event" decision is identical on Android and iOS (Tier 1, ADR-032). See
 * `AndroidGarage/docs/DOOR_ANIMATION.md`.
 */
class DoorAnimationMemoryTest {
    @Test
    fun consumeAnimateFromStart_isTrueOnceThenFalse() {
        val memory = DoorAnimationMemory()
        val key = DoorMotionKey(DoorPosition.OPENING, lastChangeTimeSeconds = 100L)

        // First sighting of this event → animate from the start.
        assertTrue(memory.consumeAnimateFromStart(key), "first call should animate")
        // Re-entry of the same event (tab-switch / back-nav) → snap, no replay.
        assertFalse(memory.consumeAnimateFromStart(key), "second call should snap")
        assertFalse(memory.consumeAnimateFromStart(key), "third call should snap")
    }

    @Test
    fun consumeAnimateFromStart_distinctEventsEachAnimateOnce() {
        val memory = DoorAnimationMemory()
        // Same position, different change timestamp = a different physical event.
        val first = DoorMotionKey(DoorPosition.CLOSING, lastChangeTimeSeconds = 100L)
        val second = DoorMotionKey(DoorPosition.CLOSING, lastChangeTimeSeconds = 200L)

        assertTrue(memory.consumeAnimateFromStart(first), "first event animates")
        assertTrue(memory.consumeAnimateFromStart(second), "second event animates")
        assertFalse(memory.consumeAnimateFromStart(first), "first event re-entry snaps")
        assertFalse(memory.consumeAnimateFromStart(second), "second event re-entry snaps")
    }

    @Test
    fun consumeAnimateFromStart_nullTimestampIsItsOwnKey() {
        val memory = DoorAnimationMemory()
        val nullKey = DoorMotionKey(DoorPosition.OPENING, lastChangeTimeSeconds = null)
        val timedKey = DoorMotionKey(DoorPosition.OPENING, lastChangeTimeSeconds = 100L)

        assertTrue(memory.consumeAnimateFromStart(nullKey), "null-timestamp event animates")
        // A null timestamp must not collide with a timestamped event of the same
        // position — they are distinct keys.
        assertTrue(memory.consumeAnimateFromStart(timedKey), "timestamped event still animates")
        assertFalse(memory.consumeAnimateFromStart(nullKey), "null-timestamp re-entry snaps")
    }
}
