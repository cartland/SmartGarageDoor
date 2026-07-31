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

package com.chriscartland.garage.wear.ui

import kotlin.math.sqrt

/**
 * Where the hold ring is allowed to draw, and where content is allowed to sit.
 *
 * The hero screen has one recurring failure mode: the ring hugs the bezel, the
 * bottom label hugs the bottom edge, and on a ROUND screen those two are the
 * same place. The chord at the bottom of the screen is far narrower than the
 * chord through the middle, so a label sized against the screen's own circle
 * still runs straight through a ring drawn inside it. That is what happened —
 * "Waiting for the door" was crossed by the ring on both sides, and worse on
 * the LARGER watch, because the label's width scales with the screen while the
 * ring's thickness does not.
 *
 * The fix is to stop treating the two as independent. Everything here derives
 * from one idea: the outer [RING_BAND_DP] of the screen belongs to the ring and
 * to nothing else, and all content lives inside [contentRadiusDp]. Callers ask
 * this object where their content may go rather than carrying hand-tuned dp.
 *
 * Radii are measured from the centre of a round screen of the given diameter.
 * Everything is in dp and pure, so the rules are unit-testable — which matters,
 * because the failure is a few dp of overlap that is easy to miss by eye on one
 * device size and glaring on another.
 */
internal object HeroLayout {
    /** Gap between the physical screen edge and the ring's outer edge. */
    const val RING_EDGE_PADDING_DP: Float = 2f

    /**
     * Resting stroke of the sweep and in-flight rings.
     *
     * Distinct from [RING_BAND_DP]: this is what the ring normally draws, that
     * is the lane it may never leave.
     */
    const val RING_STROKE_DP: Float = 5f

    /**
     * Width of the annulus reserved for the ring, measured inward from the
     * ring's outer edge.
     *
     * The commit bloom thickens the ring inward to exactly this width — which
     * is why the band exists at all. Before it, the bloom grew until its inner
     * edge reached the centre of the screen, so the one moment the user most
     * needs to read the door's state was the moment the door and both labels
     * were painted over. Reserving a lane lets the bloom stay emphatic without
     * ever owning a pixel that content might be using.
     */
    const val RING_BAND_DP: Float = 8f

    /** Breathing room between the band's inner edge and the nearest content. */
    const val CONTENT_CLEARANCE_DP: Float = 4f

    /** Radius inside which all content must stay. */
    fun contentRadiusDp(diameterDp: Float): Float = diameterDp / 2f - RING_EDGE_PADDING_DP - RING_BAND_DP - CONTENT_CLEARANCE_DP

    /**
     * Bottom padding for a bottom-anchored block [widthDp] wide, placing its
     * bottom CORNERS exactly on the content circle.
     *
     * The corners are the whole problem: a centred block's lowest, outermost
     * points are what a ring drawn inside the screen cuts through first, and
     * they are the last thing you look at when checking a layout by eye. For a
     * bottom-anchored block the bottom edge is the furthest from the centre and
     * therefore has the narrowest chord, so satisfying it satisfies the block.
     *
     * Returns half the diameter (i.e. dead centre) for a block too wide to fit
     * at any height, which is a caller bug rather than something to render
     * around; [maxBottomBlockWidthDp] is the guard for that.
     */
    fun bottomInsetDp(
        diameterDp: Float,
        widthDp: Float,
    ): Float {
        val radius = contentRadiusDp(diameterDp)
        val halfWidth = widthDp / 2f
        if (halfWidth >= radius) return diameterDp / 2f
        return diameterDp / 2f - sqrt(radius * radius - halfWidth * halfWidth)
    }

    /** Widest a bottom-anchored block may be and still have somewhere to sit. */
    fun maxBottomBlockWidthDp(diameterDp: Float): Float = 2f * contentRadiusDp(diameterDp)

    /**
     * Side of the largest square that fits inside the content circle.
     *
     * The door is square, so this is its ceiling. In practice the vertical
     * space left over by the label usually binds first — see the `weight(1f)`
     * slot in `HeroScreenLayout`, which is what actually budgets the door.
     */
    fun maxSquareSideDp(diameterDp: Float): Float = contentRadiusDp(diameterDp) * SQRT_TWO

    /**
     * Stroke width for the commit bloom at [bloom] progress, growing from
     * [RING_STROKE_DP] to fill [RING_BAND_DP].
     *
     * Paired with [bloomArcInsetDp]: the bloom must grow INWARD only, keeping
     * its outer edge pinned to the ring's outer edge. Widening a stroke about a
     * fixed centreline would push half the growth off the screen and, more to
     * the point, put the inner edge somewhere the band never accounted for.
     */
    fun bloomStrokeDp(bloom: Float): Float = RING_STROKE_DP + (RING_BAND_DP - RING_STROKE_DP) * bloom.coerceIn(0f, 1f)

    /**
     * Inset from the ring box's edge to the CENTRELINE of a bloom stroke of
     * [strokeDp], which is what pins the bloom's outer edge in place.
     */
    fun bloomArcInsetDp(strokeDp: Float): Float = strokeDp / 2f

    private const val SQRT_TWO = 1.41421356f
}
