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

package com.chriscartland.garage.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.chriscartland.garage.domain.model.DoorPosition

/**
 * Identity of one physical door motion event, used to decide whether the
 * [GarageIcon] should replay its full open/close slide from the start.
 *
 * `lastChangeTimeSeconds` is the server-reported timestamp of the door's last
 * position change, so `(position, lastChangeTimeSeconds)` is stable for a
 * given OPENING/CLOSING event and changes when the door next transitions.
 */
data class DoorMotionKey(
    val doorPosition: DoorPosition,
    val lastChangeTimeSeconds: Long?,
)

/**
 * Presentation-layer memory of which door motion events have already played
 * their slide animation. This lives entirely in the Compose tree (provided at
 * the app root via [LocalDoorAnimationMemory]) — it is NOT business logic and
 * is intentionally kept out of the DI graph / domain layer.
 *
 * ## Why it exists
 *
 * The door icon should slide from the start (CLOSED→OPEN for OPENING,
 * OPEN→CLOSED for CLOSING) whenever the user first sees an in-motion state —
 * including a **cold open** where the door has already been opening/closing
 * for a while. This is a deliberate "always start at the beginning regardless
 * of elapsed duration" choice: we do NOT seed mid-motion from elapsed time,
 * because device/server clock drift would put the icon at a silently wrong
 * position (see ADR-025, amended).
 *
 * The earlier 2.16.4 behavior suppressed the slide on every fresh composition
 * to avoid replaying it on every tab-switch / back-nav while the same event
 * was still current. This memory recovers the cold-open slide *without*
 * bringing back that replay: it remembers which event keys have already
 * animated, so re-entry of an already-animated event snaps to the target
 * instead of replaying.
 *
 * Because the holder is a plain `remember {}` at the Compose root, it survives
 * tab-switch / back-nav (the root is not disposed) but resets on process death
 * — which is exactly when a cold open *should* replay the slide.
 *
 * Not thread-safe by design: all access is from the main (composition) thread.
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

/**
 * Compose-tree access to the [DoorAnimationMemory]. Provided once at the app
 * root (`GarageApp`). The default factory yields a throwaway instance so
 * previews and tests that don't provide one simply animate-from-start every
 * time (harmless); production and the instrumented audit provide an explicit
 * instance.
 */
val LocalDoorAnimationMemory = staticCompositionLocalOf { DoorAnimationMemory() }
