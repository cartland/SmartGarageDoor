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
 * How long the door sat in one state, bucketed for the History row's
 * "Open for 2 hr 14 min" line.
 *
 * The sibling of [ElapsedDuration], which does the same job for Home's "Since"
 * line. History needs its own ladder because it wants more precision: Home
 * shows "1 day" where History shows "1 day 5 hr", because Home's line is a
 * glance and History's is the record.
 *
 * Each arm carries only the units it renders — the leftovers are dropped
 * deliberately, not lost. Arms map one-to-one onto the string resources both
 * platforms already have, so wording stays entirely per-platform.
 */
sealed interface StateSpanDuration {
    /** 1+ whole days, on the hour. Leftover minutes dropped. */
    data class Days(
        val days: Int,
    ) : StateSpanDuration

    /** 1+ whole days with leftover hours. Leftover minutes dropped. */
    data class DaysHours(
        val days: Int,
        val hours: Int,
    ) : StateSpanDuration

    /** Under a day, 1+ hours, on the hour. Leftover seconds dropped. */
    data class Hours(
        val hours: Int,
    ) : StateSpanDuration

    /** Under a day, 1+ hours with leftover minutes. Leftover seconds dropped. */
    data class HoursMinutes(
        val hours: Int,
        val minutes: Int,
    ) : StateSpanDuration

    /** Under an hour, 1+ minutes. Leftover seconds dropped. */
    data class Minutes(
        val minutes: Int,
    ) : StateSpanDuration

    /** Under a minute. */
    data class Seconds(
        val seconds: Int,
    ) : StateSpanDuration
}

/**
 * How long a transit (opening or closing) took, bucketed for the warning tag.
 *
 * A *different* ladder from [StateSpanDuration] on purpose. A transit is
 * normally seconds and occasionally minutes, so seconds stay visible at minute
 * scale — "2 min 30 sec" is the interesting fact about a slow door, and
 * rounding it to "2 min" throws away the point. A state span, by contrast, is
 * normally hours, so its seconds are noise and get dropped.
 *
 * The two ladders differing is exactly why they are worth sharing: two
 * near-identical rules are far easier to accidentally converge or diverge than
 * one, and both platforms previously carried both.
 */
sealed interface TransitSpanDuration {
    /** 1+ hours, on the hour. */
    data class Hours(
        val hours: Int,
    ) : TransitSpanDuration

    /** 1+ hours with leftover minutes. Leftover seconds dropped — hours-scale
     *  transits are pathological, and precision stops helping. */
    data class HoursMinutes(
        val hours: Int,
        val minutes: Int,
    ) : TransitSpanDuration

    /** Under an hour, 1+ minutes, on the minute. */
    data class Minutes(
        val minutes: Int,
    ) : TransitSpanDuration

    /** Under an hour, 1+ minutes with leftover seconds — kept, unlike the
     *  state-span ladder. */
    data class MinutesSeconds(
        val minutes: Int,
        val seconds: Int,
    ) : TransitSpanDuration

    /** Under a minute. The common case. */
    data class Seconds(
        val seconds: Int,
    ) : TransitSpanDuration
}

/**
 * How a state-span duration is framed in the row.
 *
 * The precedence is the decision: **an ongoing span is "and counting"
 * regardless of which state it is in**. Both platforms encoded that as a
 * conditional chain that checked `isCurrent` first; stating it once means the
 * two cannot disagree about whether a currently-open door reads "Open for 3 hr"
 * or "3 hr and counting".
 */
enum class StateSpanFraming {
    /** Span is still running. */
    AND_COUNTING,

    /** Completed span, door was open. */
    OPEN_FOR,

    /** Completed span, door was closed. */
    CLOSED_FOR,
}

/** A state span: how long, and how to frame it. */
data class StateSpanDisplay(
    val duration: StateSpanDuration,
    val framing: StateSpanFraming,
)

/**
 * Buckets History's two duration ladders.
 *
 * Both were implemented twice — `HistoryFormatter.stateDurationParts` +
 * `stateDurationDisplay` on Android, `stateDuration` / `transitText` on iOS —
 * with the granularity rules restated in each. That is the highest-reach
 * duplication in the app: it renders on every row of the history list.
 *
 * Negative inputs clamp to zero. A negative span means clock skew between the
 * device and the server, and "0 sec" is a better answer than a negative
 * duration or a crash.
 */
object HistoryDurationMapper {
    fun stateSpan(
        seconds: Long,
        isCurrent: Boolean,
        isOpen: Boolean,
    ): StateSpanDisplay =
        StateSpanDisplay(
            duration = stateSpanDuration(seconds),
            framing = when {
                // Ongoing outranks which state it is: a door that is open right now
                // reads "3 hr and counting", not "Open for 3 hr".
                isCurrent -> StateSpanFraming.AND_COUNTING
                isOpen -> StateSpanFraming.OPEN_FOR
                else -> StateSpanFraming.CLOSED_FOR
            },
        )

    fun stateSpanDuration(seconds: Long): StateSpanDuration {
        val safe = seconds.coerceAtLeast(0L)
        val days = (safe / SECONDS_PER_DAY).toInt()
        val hours = ((safe % SECONDS_PER_DAY) / SECONDS_PER_HOUR).toInt()
        val minutes = ((safe % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE).toInt()
        return when {
            days >= 1 && hours == 0 -> StateSpanDuration.Days(days)
            days >= 1 -> StateSpanDuration.DaysHours(days, hours)
            hours >= 1 && minutes == 0 -> StateSpanDuration.Hours(hours)
            hours >= 1 -> StateSpanDuration.HoursMinutes(hours, minutes)
            minutes >= 1 -> StateSpanDuration.Minutes(minutes)
            else -> StateSpanDuration.Seconds((safe % SECONDS_PER_MINUTE).toInt())
        }
    }

    fun transitSpan(seconds: Long): TransitSpanDuration {
        val safe = seconds.coerceAtLeast(0L)
        val hours = (safe / SECONDS_PER_HOUR).toInt()
        val minutes = ((safe % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE).toInt()
        val secs = (safe % SECONDS_PER_MINUTE).toInt()
        return when {
            hours >= 1 && minutes == 0 -> TransitSpanDuration.Hours(hours)
            hours >= 1 -> TransitSpanDuration.HoursMinutes(hours, minutes)
            minutes >= 1 && secs == 0 -> TransitSpanDuration.Minutes(minutes)
            minutes >= 1 -> TransitSpanDuration.MinutesSeconds(minutes, secs)
            else -> TransitSpanDuration.Seconds(secs)
        }
    }

    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3_600L
    private const val SECONDS_PER_DAY = 86_400L
}
