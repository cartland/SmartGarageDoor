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

import com.chriscartland.garage.domain.model.DoorAnimation
import com.chriscartland.garage.domain.model.DoorColorState
import com.chriscartland.garage.domain.model.DoorPosition

/**
 * Everything a GLANCE surface needs to say about the door, decided once.
 *
 * A glance surface is one the SYSTEM renders while the app is not running and
 * nobody is interacting with it — a Wear tile, a watch-face complication, an
 * iOS widget. The name is the concept rather than any platform's word for it
 * (ADR-035's corollary: a type called `TileStatus` would have answered the
 * question for the next implementer before they asked it).
 *
 * The four fields are the questions a glance has to answer at once: what is
 * the door doing ([headline]), may we present that confidently
 * ([freshness]), how old is it ([age]), and which of the three door colours
 * is it ([colorState]). Each platform supplies the words and the drawing.
 *
 * [colorState] is carried rather than left for the surface to derive from
 * [headline], because deriving it would mean re-implementing
 * `DoorAnimation.colorStateFor` — the shared rule that decides which
 * positions count as open, closed, or neither — in every surface that draws
 * a door. That is exactly the collapsing ADR-035 says belongs in the shared
 * layer.
 */
data class GlanceStatus(
    val headline: StatusHeadline,
    val freshness: DataFreshness,
    val age: CheckInStatus,
    val colorState: DoorColorState,
)

/**
 * Builds a [GlanceStatus] from what a glance surface can actually know.
 *
 * Composed entirely from the mappers the live screens already use —
 * [DataFreshnessMapper], [StatusHeadlineMapper], [CheckInStatusMapper] — so a
 * tile and the app it belongs to cannot reach different verdicts about the
 * same door. This object contributes exactly one new decision of its own, the
 * settle-window one below.
 */
object GlanceStatusMapper {
    /**
     * **A glance never settles: it always speaks.**
     *
     * The settle window exists because an app that has just been opened is
     * about to hear from the garage within a second or two, so the five
     * seconds it spends arriving should be grey and wordless rather than
     * alarming — see CLAUDE.md § "The settle window". None of that reasoning
     * survives the move to a glance surface. A tile is not arriving; it is
     * being ASKED, once, and whatever it returns is what the user reads and
     * swipes away from. Withholding the words there would produce a grey door
     * with no explanation at the one moment somebody looked at it, and the
     * explanation would arrive — if at all — after they had stopped looking.
     *
     * So `isSettling` is pinned false here rather than left to the caller.
     * A surface that wants the arriving behaviour is a screen, and should be
     * using the screen's path.
     */
    private const val A_GLANCE_IS_NEVER_SETTLING = false

    /**
     * @param doorPosition the last known position, or null if nothing is known.
     * @param lastCheckInEpochSeconds when the GARAGE last reported, which is
     *   what decides whether [doorPosition] is still worth believing. Null
     *   when unknown — see [CheckInStatus.NoData].
     * @param nowEpochSeconds the current wall clock.
     * @param isFetchError whether the most recent attempt to refresh failed.
     *   A glance that could not reach the server is showing a remembered
     *   value, and says so, exactly as the screens do.
     */
    fun forGlance(
        doorPosition: DoorPosition?,
        lastCheckInEpochSeconds: Long?,
        nowEpochSeconds: Long,
        isFetchError: Boolean,
    ): GlanceStatus {
        val age = CheckInStatusMapper.forCheckIn(
            lastCheckInEpochSeconds = lastCheckInEpochSeconds,
            nowEpochSeconds = nowEpochSeconds,
        )
        val freshness = DataFreshnessMapper.freshness(
            hasData = doorPosition != null,
            isCheckInStale = age is CheckInStatus.Reported && age.isStale,
            isFetchError = isFetchError,
            isSettling = A_GLANCE_IS_NEVER_SETTLING,
        )
        return GlanceStatus(
            headline = StatusHeadlineMapper.forDoor(
                doorPosition = doorPosition,
                freshness = freshness,
            ),
            freshness = freshness,
            age = age,
            // Nothing known reads as the UNKNOWN (grey) door rather than as
            // a colour we would have to invent — the same thing the door
            // screen shows before it has heard anything.
            colorState = doorPosition?.let(DoorAnimation::colorStateFor) ?: DoorColorState.UNKNOWN,
        )
    }
}
