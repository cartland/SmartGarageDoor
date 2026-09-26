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

package com.chriscartland.garage.wear.tile

import androidx.annotation.StringRes
import com.chriscartland.garage.domain.model.DoorColorState
import com.chriscartland.garage.domain.model.GarageDoorPalette
import com.chriscartland.garage.presentation.CheckInAge
import com.chriscartland.garage.presentation.CheckInStatus
import com.chriscartland.garage.presentation.DataFreshness
import com.chriscartland.garage.presentation.DoorHeadline
import com.chriscartland.garage.presentation.FreshnessTint
import com.chriscartland.garage.presentation.StatusHeadline
import com.chriscartland.garage.wear.R

/**
 * The tile's words, as string resources.
 *
 * Resource IDs rather than resolved strings, so the decisions in here are
 * testable on the JVM without a `Context` — a tile layout needs one, but which
 * word goes with which verdict does not. ADR-035: the shared layer decided
 * WHICH of these applies ([StatusHeadline], [CheckInStatus]); this object only
 * says it in the watch's words, and reuses the door screen's existing strings
 * so the tile and the app cannot describe the same door differently.
 */
object GarageTileWords {
    @StringRes
    fun headline(headline: StatusHeadline): Int =
        when (headline) {
            is StatusHeadline.Door -> doorHeadline(headline.headline)
            StatusHeadline.Connecting -> R.string.door_state_connecting
            StatusHeadline.NoSignal -> R.string.door_state_no_signal
        }

    @StringRes
    private fun doorHeadline(headline: DoorHeadline): Int =
        when (headline) {
            DoorHeadline.UNKNOWN -> R.string.door_state_unknown
            DoorHeadline.CLOSED -> R.string.door_state_closed
            DoorHeadline.OPENING -> R.string.door_state_opening
            DoorHeadline.OPEN -> R.string.door_state_open
            DoorHeadline.CLOSING -> R.string.door_state_closing
            DoorHeadline.SENSOR_CONFLICT -> R.string.door_state_sensor_conflict
        }

    /**
     * The "… ago" line, or null when there is no age to state.
     *
     * Null for [CheckInStatus.NoData] — a reading with no timestamp is
     * unjudgeable rather than new, and inventing "Just now" for it would be
     * the single most misleading thing this tile could say. The surface then
     * simply omits the line.
     *
     * Coarser than the phone's pill on purpose: the watch has one line to
     * spend and a glance wants an order of magnitude, not a stopwatch. The
     * BUCKETS are still the shared ones, so "stale" means the same thing
     * everywhere; only how many of them get their own sentence differs.
     */
    fun ageLine(age: CheckInStatus): AgeLine? =
        when (age) {
            CheckInStatus.NoData -> null
            is CheckInStatus.Reported ->
                when (val bucket = age.age) {
                    CheckInAge.JustNow -> AgeLine(R.string.glance_age_just_now)
                    is CheckInAge.Seconds -> AgeLine(R.string.glance_age_seconds, bucket.seconds)
                    is CheckInAge.Minutes -> AgeLine(R.string.glance_age_minutes, bucket.minutes)
                    is CheckInAge.Hours -> AgeLine(R.string.glance_age_hours, bucket.hours)
                    is CheckInAge.Days -> AgeLine(R.string.glance_age_days, bucket.days)
                }
        }

    /** A string resource plus the count it may need formatting into. */
    data class AgeLine(
        @param:StringRes val resId: Int,
        val quantity: Int? = null,
    )
}

/**
 * The tile's colours, as ARGB ints.
 *
 * ProtoLayout has no `Color` type of its own and cannot see Compose's, so the
 * watch's `WearDoorColors` / `WearFreshnessTint` pair is unusable here. The
 * SOURCES are still the shared ones — [GarageDoorPalette] for the fill and
 * [FreshnessTint] for how grey and how dim — so the tile drains to exactly the
 * same grey by exactly the same amount as the door screen behind it. Only the
 * channel arithmetic is local, and only because the types are.
 */
object GarageTileColors {
    /**
     * The door fill, muted when the verdict says we cannot vouch for it.
     *
     * Reads the `_FRESH_DARK` palette entries and drains them through
     * [FreshnessTint], rather than reaching for the palette's own `_STALE_`
     * variants: those are a PARTIAL desaturation built for the phone's card,
     * so mixing the two would grey the door twice, by different amounts, on
     * two screens of the same app. Same reasoning as `WearDoorColors`.
     */
    fun doorFill(
        colorState: DoorColorState,
        freshness: DataFreshness,
    ): Int {
        val base = when (colorState) {
            DoorColorState.CLOSED -> GarageDoorPalette.CLOSED_FRESH_DARK
            DoorColorState.OPEN -> GarageDoorPalette.OPEN_FRESH_DARK
            DoorColorState.UNKNOWN -> GarageDoorPalette.UNKNOWN_FRESH_DARK
        }.toInt()
        if (!freshness.isMuted) return base
        return withAlpha(desaturate(base), FreshnessTint.alphaFor(freshness))
    }

    /**
     * Text drawn on top of [doorFill].
     *
     * Dimmed alongside the fill rather than left at full strength: the point
     * of the muted look is that the whole reading recedes together. A bright
     * word on a drained fill would read as a rendering fault.
     */
    fun onDoorFill(freshness: DataFreshness): Int = withAlpha(ON_DOOR_FILL, FreshnessTint.alphaFor(freshness))

    /** [color] with its hue drained out, keeping its perceived lightness. */
    private fun desaturate(color: Int): Int {
        val luma = FreshnessTint.luma(
            red = channel(color, RED_SHIFT),
            green = channel(color, GREEN_SHIFT),
            blue = channel(color, BLUE_SHIFT),
        )
        val grey = (luma * CHANNEL_MAX).toInt().coerceIn(0, CHANNEL_MAX_INT)
        return (color and ALPHA_MASK) or (grey shl RED_SHIFT) or (grey shl GREEN_SHIFT) or grey
    }

    private fun withAlpha(
        color: Int,
        alpha: Float,
    ): Int {
        val scaled = (channel(color, ALPHA_SHIFT) * alpha * CHANNEL_MAX).toInt().coerceIn(0, CHANNEL_MAX_INT)
        return (scaled shl ALPHA_SHIFT) or (color and RGB_MASK)
    }

    private fun channel(
        color: Int,
        shift: Int,
    ): Float = ((color shr shift) and CHANNEL_MAX_INT) / CHANNEL_MAX

    /** Near-white; the palette fills are dark enough that white always reads. */
    private const val ON_DOOR_FILL: Int = 0xFFF2F2F2.toInt()

    private const val ALPHA_SHIFT = 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val BLUE_SHIFT = 0
    private const val CHANNEL_MAX_INT = 0xFF
    private const val CHANNEL_MAX = 255f
    private const val ALPHA_MASK = 0xFF000000.toInt()
    private const val RGB_MASK = 0x00FFFFFF
}
