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

/** Overlay glyph drawn on top of the door for a [DoorPosition]. */
enum class DoorOverlayKind { NONE, ARROW_UP, ARROW_DOWN, WARNING }

/**
 * Shared **door-animation spec** — the pure `DoorPosition → visual` mappings
 * (offset targets + overlay) that drive the garage-door visualization on
 * **both** Android (`GarageIcon` / `AnimatableGarageDoor.kt`) and iOS
 * (`GarageDoorCanvas.swift` `DoorVisual`).
 *
 * Per ADR-032 the door visualization is a **Tier 1 (brand-locked)** surface and
 * its motion is split into a provable **spec** (this object) and a best-effort
 * **execution** (each platform's native animation engine). This object is the
 * spec: *what* the animation does — the offsets, the per-state targets, the
 * tween-vs-spring family, the static snapshot offset, and the overlay. *How* it
 * renders frame-by-frame (Compose `Animatable` vs SwiftUI `.animation`, frame
 * pacing, reduce-motion) is platform-local and intentionally not shared. See
 * `AndroidGarage/docs/UI_FIDELITY_TIERS.md` § "Animation: split the spec from
 * the execution" and `AndroidGarage/docs/DOOR_ANIMATION.md` for the contract.
 *
 * Each `when` is exhaustive (no `else`) so adding a [DoorPosition] forces a
 * decision at compile time; `DoorAnimationTest` (commonTest) pins the values so
 * a wrong update is caught on every platform. Previously these lived as
 * hand-duplicated literals in each platform's source; this object makes the
 * agreement structural. Sibling of [GarageDoorGeometry] / [GarageDoorPalette].
 *
 * Today iOS consumes [staticPositionFor] (its displayed offset) + [overlayFor];
 * Android additionally drives the live trajectory from [targetPositionFor] /
 * [fromPositionFor] / [useSpringFor]. iOS adopting the live trajectory is a
 * separate, deliberate convergence (not a no-op) — see UI_FIDELITY_TIERS § (b).
 */
object DoorAnimation {
    // Door offset positions as a proportion of viewport height (300×300 square).
    // Negative = door slides up (opening). Panels at y=[22, 89, 156, 223],
    // height 61. At -0.75 (shift 225px): panel-4 bottom = 284 - 225 = 59,
    // visible = 59 - 22 = 37px (≈60% of panel).
    const val CLOSED_POSITION: Float = 0.0f
    const val CLOSING_STATIC_POSITION: Float = -0.20f
    const val MIDWAY_POSITION: Float = -0.35f
    const val OPENING_STATIC_POSITION: Float = -0.65f
    const val OPEN_POSITION: Float = -0.75f

    /** Target offset to animate toward for the given state. */
    fun targetPositionFor(doorPosition: DoorPosition): Float =
        when (doorPosition) {
            DoorPosition.UNKNOWN -> MIDWAY_POSITION
            DoorPosition.CLOSED -> CLOSED_POSITION
            DoorPosition.OPENING -> OPEN_POSITION
            DoorPosition.OPENING_TOO_LONG -> MIDWAY_POSITION
            DoorPosition.OPEN -> OPEN_POSITION
            DoorPosition.OPEN_MISALIGNED -> OPEN_POSITION
            DoorPosition.CLOSING -> CLOSED_POSITION
            DoorPosition.CLOSING_TOO_LONG -> MIDWAY_POSITION
            DoorPosition.ERROR_SENSOR_CONFLICT -> MIDWAY_POSITION
        }

    /**
     * The "from" end a motion state slides *out of* — the position the icon is
     * seeded at when it should replay the full open/close animation from the
     * start.
     *
     * - `OPENING` slides up out of `CLOSED_POSITION`.
     * - `CLOSING` slides down out of `OPEN_POSITION`.
     * - Non-motion states have no distinct "from" end; they equal
     *   [targetPositionFor] (the spring settles in place, no replay).
     *
     * Whether this seed is actually used is decided per fresh composition by
     * the platform's replay memory (Android `DoorAnimationMemory`): a newly
     * observed motion event (cold-open / first view) seeds here and slides;
     * re-entry of an already-animated event seeds at [targetPositionFor] and
     * snaps. Live transitions animate from the current value, never from this
     * seed. See `AndroidGarage/docs/DOOR_ANIMATION.md` and ADR-025 (amended).
     */
    fun fromPositionFor(doorPosition: DoorPosition): Float =
        when (doorPosition) {
            DoorPosition.OPENING -> CLOSED_POSITION
            DoorPosition.CLOSING -> OPEN_POSITION
            DoorPosition.UNKNOWN,
            DoorPosition.CLOSED,
            DoorPosition.OPENING_TOO_LONG,
            DoorPosition.OPEN,
            DoorPosition.OPEN_MISALIGNED,
            DoorPosition.CLOSING_TOO_LONG,
            DoorPosition.ERROR_SENSOR_CONFLICT,
            -> targetPositionFor(doorPosition)
        }

    /**
     * Which animation spec family applies.
     *
     * - `false` → tween (linear) for OPENING/CLOSING — matches a real garage
     *   door's roughly constant-speed motion.
     * - `true` → spring (slow, no overshoot) for terminal/error/unknown —
     *   states "settle" to their target.
     */
    fun useSpringFor(doorPosition: DoorPosition): Boolean =
        when (doorPosition) {
            DoorPosition.OPENING, DoorPosition.CLOSING -> false
            DoorPosition.UNKNOWN,
            DoorPosition.CLOSED,
            DoorPosition.OPENING_TOO_LONG,
            DoorPosition.OPEN,
            DoorPosition.OPEN_MISALIGNED,
            DoorPosition.CLOSING_TOO_LONG,
            DoorPosition.ERROR_SENSOR_CONFLICT,
            -> true
        }

    /**
     * Snapshot offset for a non-animated render — used when the icon is drawn
     * static (past-event snapshots, previews, and the whole iOS render today).
     * For motion states the snapshot is a mid-cycle position so the door
     * visibly looks "in motion" without actually animating.
     */
    fun staticPositionFor(doorPosition: DoorPosition): Float =
        when (doorPosition) {
            DoorPosition.OPENING -> OPENING_STATIC_POSITION
            DoorPosition.CLOSING -> CLOSING_STATIC_POSITION
            DoorPosition.UNKNOWN,
            DoorPosition.CLOSED,
            DoorPosition.OPENING_TOO_LONG,
            DoorPosition.OPEN,
            DoorPosition.OPEN_MISALIGNED,
            DoorPosition.CLOSING_TOO_LONG,
            DoorPosition.ERROR_SENSOR_CONFLICT,
            -> targetPositionFor(doorPosition)
        }

    /** Which overlay glyph to draw on top of the door, if any. */
    fun overlayFor(doorPosition: DoorPosition): DoorOverlayKind =
        when (doorPosition) {
            DoorPosition.OPENING -> DoorOverlayKind.ARROW_UP
            DoorPosition.CLOSING -> DoorOverlayKind.ARROW_DOWN
            DoorPosition.UNKNOWN,
            DoorPosition.OPENING_TOO_LONG,
            DoorPosition.CLOSING_TOO_LONG,
            DoorPosition.ERROR_SENSOR_CONFLICT,
            -> DoorOverlayKind.WARNING
            DoorPosition.CLOSED,
            DoorPosition.OPEN,
            DoorPosition.OPEN_MISALIGNED,
            -> DoorOverlayKind.NONE
        }
}
