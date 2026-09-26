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
import com.chriscartland.garage.presentation.DoorHeadline
import com.chriscartland.garage.presentation.GlanceStatus
import com.chriscartland.garage.presentation.Liveness
import com.chriscartland.garage.presentation.StatusHeadline
import com.chriscartland.garage.wear.R

/**
 * What the complication says, in the seven characters it gets.
 *
 * **No number this object prints is ever computed here.** The one number worth
 * showing — how long the door has been in its current state — is handed to the
 * watch face as a `TimeDifferenceComplicationText`, and the FACE renders it at
 * read time. That is the whole point: a complication is redrawn on the
 * system's schedule, as rarely as every ten minutes, so a duration we computed
 * and froze would be silently wrong by up to that much. "8m" read eighteen
 * minutes later is a specific, confident, wrong statement.
 *
 * What is left for this object is the words either side of it:
 *
 * | | `text` | `title` |
 * |---|---|---|
 * | live | the door — "Open" | **live duration**, rendered by the face |
 * | stale | "Stale" | the door — "Open" |
 * | nothing known | "No data" | — |
 *
 * **Stale leads, and it is a word rather than an age.** Many faces render
 * `text` alone, so whatever goes there has to survive on its own: "Open" by
 * itself would be an unqualified claim about a door we have lost contact with.
 * The previous design led with the check-in age ("6h ago") for the same
 * reason, which was right about the emphasis and wrong about the content —
 * the age was both the uninteresting number (it describes our plumbing, not
 * the garage) and the one guaranteed to be out of date.
 *
 * Seven characters is a hard budget
 * ([androidx.wear.watchface.complications.data.ShortTextComplicationData.MAX_TEXT_LENGTH]);
 * the face's own duration rendering respects it, and everything here is
 * checked against the real strings by `GarageComplicationLengthTest`.
 */
object GarageComplicationWords {
    /**
     * The door in one short word, or null when there is no door to name.
     *
     * Its own vocabulary rather than the door screen's: "Sensor conflict"
     * does not fit, and a complication that ellipsised it would say something
     * worse than a shorter word chosen on purpose. ADR-035 puts exactly this
     * call on the platform — the shared layer already decided WHICH state
     * applies.
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
     * Whether the doubt has to lead.
     *
     * When the reading cannot be vouched for there is no live duration to
     * show — the shared mapper withholds the instant, because a duration
     * asserts the door has been this way continuously and a door we have lost
     * contact with may have moved twice since. So the word takes its place at
     * the front, where a text-only face will see it.
     */
    fun staleLeads(status: GlanceStatus): Boolean = status.liveness == Liveness.STALE

    /**
     * The word for a reading we cannot vouch for. Five characters, no number,
     * and it cannot go out of date the way a measurement can.
     */
    @StringRes
    val STALE: Int = R.string.complication_stale

    /** Shown when nothing has ever been heard, so there is no door to name. */
    @StringRes
    val NO_DATA: Int = R.string.complication_no_data
}
