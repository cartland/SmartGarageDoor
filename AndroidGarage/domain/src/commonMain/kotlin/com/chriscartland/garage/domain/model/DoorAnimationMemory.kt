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

/**
 * Identity of one physical door motion event, used to decide whether the door
 * icon should replay its full open/close slide from the start.
 *
 * `lastChangeTimeSeconds` is the server-reported timestamp of the door's last
 * position change, so `(doorPosition, lastChangeTimeSeconds)` is stable for a
 * given OPENING/CLOSING event and changes when the door next transitions.
 *
 * Shared `:domain` ([DoorAnimation] sibling) so the **replay policy** is one
 * source of truth across Android and iOS (Tier 1, ADR-032). Only the *holder
 * lifecycle* differs per platform (Android: a Compose-root `remember`; iOS: an
 * app-level object) — the dedup decision itself lives here and is pinned by
 * `DoorAnimationMemoryTest` (commonTest).
 */
data class DoorMotionKey(
    val doorPosition: DoorPosition,
    val lastChangeTimeSeconds: Long?,
)

/**
 * Presentation-layer memory of which door motion events have already played
 * their slide animation. Intentionally **not** business logic and not in the DI
 * graph — it is a tiny dedup holder owned by the UI layer of each platform.
 *
 * ## Why it exists
 *
 * The door icon should slide from the start (CLOSED→OPEN for OPENING,
 * OPEN→CLOSED for CLOSING) whenever the user first sees an in-motion state —
 * including a **cold open** where the door has already been opening/closing for
 * a while. This is a deliberate "always start at the beginning regardless of
 * elapsed duration" choice: we do NOT seed mid-motion from elapsed time,
 * because device/server clock drift would put the icon at a silently wrong
 * position (see ADR-025, amended, and `AndroidGarage/docs/DOOR_ANIMATION.md`).
 *
 * This memory remembers which event keys have already animated, so re-entry of
 * an already-animated event (tab-switch / back-nav) snaps to the target instead
 * of replaying. The holder is created once at the UI root and survives
 * navigation (root not disposed) but resets on process death — which is exactly
 * when a cold open *should* replay the slide.
 *
 * Not thread-safe by design: all access is from the platform's main/UI thread
 * (Compose composition on Android, the main actor on iOS).
 */
class DoorAnimationMemory {
    private val animated = mutableSetOf<DoorMotionKey>()

    /**
     * Returns `true` the first time it sees [key] and records it; `false` on
     * every subsequent call for the same key. A `true` result means "this is a
     * newly observed motion event — play the slide from the start."
     */
    fun consumeAnimateFromStart(key: DoorMotionKey): Boolean = animated.add(key)
}
