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

/**
 * The arithmetic behind "grey and dim" — the look every platform gives a
 * [DataFreshness] verdict that is not [DataFreshness.FRESH].
 *
 * Shared for the same reason
 * [GarageDoorPalette][com.chriscartland.garage.domain.model.GarageDoorPalette]
 * is: three apps draw the same door, and a treatment that is only *nearly*
 * the same on each reads as a rendering bug rather than as a design.
 *
 * How much of it each platform needs differs, and that is fine:
 *
 * - [alphaFor] is used by all three. "How dim" is a taste decision, it is the
 *   one a viewer would notice drifting, and there is no reason for it to be
 *   written down more than once.
 * - [luma] is used by the two Compose apps only. Compose has no saturation
 *   modifier, so they compute the grey themselves; SwiftUI has `.saturation`
 *   and uses it. Both land on standard luminance, which is why they match.
 *
 * The colour TYPE is deliberately not shared — Compose has `Color`, SwiftUI
 * has its own — so this object trades in bare channel floats.
 *
 * Named object per ADR-009.
 */
object FreshnessTint {
    /**
     * Opacity for a muted hero, as a fraction.
     *
     * Low enough to read instantly as "hold on, this might not be current",
     * high enough that a door someone is actually trying to check stays
     * legible. The point is to withhold confidence, not information — a user
     * squinting at a half-invisible door has been told less than one looking
     * at a grey one.
     */
    const val MUTED_ALPHA: Float = 0.55f

    /** [MUTED_ALPHA] when the verdict is muted, fully opaque when it is not. */
    fun alphaFor(freshness: DataFreshness): Float = if (freshness.isMuted) MUTED_ALPHA else 1f

    /**
     * Perceived lightness of a colour, in the same 0..1 scale as its channels.
     * Feed it back in as all three channels to get that colour with its hue
     * drained out.
     *
     * Rec. 709 luma rather than a flat channel average: the door's palettes
     * are a green, a red and a grey chosen to sit at similar lightness, and
     * averaging channels would collapse the red far darker than the green —
     * turning "we are not sure" into what reads as a different, darker door
     * state. The weights are the reason this is worth sharing at all.
     */
    fun luma(
        red: Float,
        green: Float,
        blue: Float,
    ): Float = LUMA_R * red + LUMA_G * green + LUMA_B * blue

    private const val LUMA_R = 0.2126f
    private const val LUMA_G = 0.7152f
    private const val LUMA_B = 0.0722f
}
