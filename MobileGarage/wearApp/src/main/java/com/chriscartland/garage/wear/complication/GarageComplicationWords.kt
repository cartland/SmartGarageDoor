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

package com.chriscartland.garage.wear.complication

import androidx.annotation.StringRes
import com.chriscartland.garage.presentation.CheckInAge
import com.chriscartland.garage.presentation.CheckInStatus
import com.chriscartland.garage.presentation.DoorHeadline
import com.chriscartland.garage.presentation.GlanceStatus
import com.chriscartland.garage.presentation.StatusHeadline
import com.chriscartland.garage.wear.R

/**
 * What the complication says, in the seven characters it gets.
 *
 * **A complication cannot be muted, and that changes the answer.** The door
 * screen and the tile both lean on the same trick for a reading they cannot
 * vouch for: drain the colour and dim it, keep the word, let the age explain.
 * That is unavailable here. The WATCH FACE owns these pixels and renders our
 * text in whatever colour it likes, so `DataFreshness.isMuted` — the whole
 * quiet half of the design — has nowhere to land. Doubt has to be carried by
 * the words or not at all.
 *
 * So the emphasis inverts with trust:
 *
 * | | leads (`text`) | follows (`title`) |
 * |---|---|---|
 * | confirmed | the door — "Open" | its age — "2m" |
 * | not vouched for | the age — "6h ago" | the door — "Open" |
 * | nothing known | "No data" | — |
 *
 * Demoting the door when it cannot be vouched for looks like it contradicts
 * the rule the rest of this app follows (`aKnownDoorIsStillNamedWhenTheVerdictIsSpoken`
 * — never throw away the last thing we know). It does not: the door word is
 * still there, in the title. What changes is which one a watch face shows if
 * it only has room for one, and **many faces render `text` alone**. On those,
 * "Open" by itself would be an unqualified claim about a door we last heard
 * from six hours ago. "6h ago" is the true statement.
 *
 * Seven characters is a hard budget ([androidx.wear.watchface.complications.data.ShortTextComplicationData.MAX_TEXT_LENGTH]),
 * which is also why the leading form tops out: "59m ago" and "23h ago" are
 * exactly seven, and anything that would overflow falls back to [R.string.complication_stale].
 */
object GarageComplicationWords {
    /**
     * The door in one short word, or null when there is no door to name.
     *
     * Its own vocabulary rather than the door screen's: "Sensor conflict"
     * does not fit, and a complication that ellipsised it would say
     * something worse than a shorter word chosen on purpose. ADR-035 puts
     * exactly this call on the platform — the shared layer already decided
     * WHICH state applies.
     */
    @StringRes
    fun doorWord(headline: StatusHeadline): Int? =
        when (headline) {
            is StatusHeadline.Door ->
                when (headline.headline) {
                    DoorHeadline.OPEN -> R.string.complication_door_open
                    DoorHeadline.CLOSED -> R.string.complication_door_closed
                    DoorHeadline.OPENING -> R.string.complication_door_opening
                    DoorHeadline.CLOSING -> R.string.complication_door_closing
                    DoorHeadline.UNKNOWN -> R.string.complication_door_unknown
                    DoorHeadline.SENSOR_CONFLICT -> R.string.complication_door_sensor_conflict
                }
            // Nothing is known, so there is nothing to name. The caller
            // renders "No data" rather than inventing a door.
            StatusHeadline.Connecting, StatusHeadline.NoSignal -> null
        }

    /**
     * The age as a title beside a confirmed door: "now", "5m", "6h", "3d".
     *
     * Null when there is no age to state, in which case the title is simply
     * omitted — a complication that guessed would be guessing about exactly
     * the thing it exists to be honest about.
     */
    fun shortAge(age: CheckInStatus): Words? =
        when (age) {
            CheckInStatus.NoData -> null
            is CheckInStatus.Reported ->
                when (val bucket = age.age) {
                    CheckInAge.JustNow -> Words(R.string.complication_age_now)
                    is CheckInAge.Seconds -> Words(R.string.complication_age_now)
                    is CheckInAge.Minutes -> Words(R.string.complication_age_minutes_short, bucket.minutes)
                    is CheckInAge.Hours -> Words(R.string.complication_age_hours_short, bucket.hours)
                    is CheckInAge.Days -> Words(R.string.complication_age_days_short, bucket.days)
                }
        }

    /**
     * The age as the LEADING text, for a reading we cannot vouch for:
     * "12m ago", "6h ago", "3d ago".
     *
     * Falls back to [R.string.complication_stale] whenever it cannot fit
     * honestly in seven characters — an unknown age, or a reading so old the
     * number itself would overflow. "Stale" says the same thing and always
     * fits; a truncated "1234d a…" would say nothing at all.
     */
    fun leadingAge(age: CheckInStatus): Words =
        when (age) {
            CheckInStatus.NoData -> Words(R.string.complication_stale)
            is CheckInStatus.Reported ->
                when (val bucket = age.age) {
                    // Reachable when a fetch failed seconds after a good
                    // reading: recent, but unconfirmed.
                    CheckInAge.JustNow -> Words(R.string.complication_stale)
                    is CheckInAge.Seconds -> Words(R.string.complication_stale)
                    is CheckInAge.Minutes -> Words(R.string.complication_age_minutes_ago, bucket.minutes)
                    is CheckInAge.Hours -> Words(R.string.complication_age_hours_ago, bucket.hours)
                    is CheckInAge.Days ->
                        if (bucket.days > MAX_DAYS_THAT_FIT) {
                            Words(R.string.complication_stale)
                        } else {
                            Words(R.string.complication_age_days_ago, bucket.days)
                        }
                }
        }

    /**
     * The age for a LONG_TEXT slot, which has room for the unabbreviated
     * wording: "2 min ago", "6 hr ago".
     *
     * The same shared buckets, said at length — these are the `glance_age_*`
     * strings the tile's line uses, so the two surfaces cannot come to
     * describe the same age differently. Null when there is none to state.
     */
    fun longAge(age: CheckInStatus): Words? =
        when (age) {
            CheckInStatus.NoData -> null
            is CheckInStatus.Reported ->
                when (val bucket = age.age) {
                    CheckInAge.JustNow -> Words(R.string.glance_age_just_now)
                    is CheckInAge.Seconds -> Words(R.string.glance_age_seconds, bucket.seconds)
                    is CheckInAge.Minutes -> Words(R.string.glance_age_minutes, bucket.minutes)
                    is CheckInAge.Hours -> Words(R.string.glance_age_hours, bucket.hours)
                    is CheckInAge.Days -> Words(R.string.glance_age_days, bucket.days)
                }
        }

    /**
     * Which of the two shapes above applies.
     *
     * Keyed on `isMuted` rather than `isSpoken`: the muted verdict is the one
     * that means "we cannot vouch for this", and on every other surface it is
     * the one that drains the colour. Here it decides which fact leads.
     */
    fun ageLeads(status: GlanceStatus): Boolean = status.freshness.isMuted

    /** A string resource plus the count it may need formatting into. */
    data class Words(
        @param:StringRes val resId: Int,
        val quantity: Int? = null,
    )

    /**
     * Above this, "%dd ago" no longer fits in seven characters, so the
     * fallback takes over. 99 days is already far past any reading worth
     * quoting precisely.
     */
    private const val MAX_DAYS_THAT_FIT = 99
}
