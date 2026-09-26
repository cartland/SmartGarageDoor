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
 * Whether what we are showing still matches what the server sees.
 *
 * Deliberately TWO states and no number. A glance surface is redrawn on the
 * system's schedule — as rarely as every ten minutes for a complication — so
 * any precise-looking figure it prints about its own currency has probably
 * drifted by the time it is read. "Checked 8 minutes ago" displayed eighteen
 * minutes late is worse than no claim at all: it is a specific, confident,
 * wrong statement about exactly the thing the reader is trying to judge.
 *
 * A coarse verdict does not have that failure mode. [LIVE] can only be wrong
 * by the width of one update, and [STALE] is not a measurement.
 */
enum class Liveness {
    /**
     * The garage reported recently AND our last fetch succeeded — so what is
     * on screen is what the server had.
     */
    LIVE,

    /**
     * One of those failed: either the garage has gone quiet, or we could not
     * reach the server to confirm. What is shown is remembered, not agreed.
     */
    STALE,
}

/**
 * Everything a GLANCE surface needs to say about the door, decided once.
 *
 * A glance surface is one the SYSTEM renders while the app is not running and
 * nobody is interacting with it — a Wear tile, a watch-face complication, an
 * iOS widget. The name is the concept rather than any platform's word for it
 * (ADR-035's corollary: a type called `TileStatus` would have answered the
 * question for the next implementer before they asked it).
 *
 * The fields are the questions a glance has to answer at once: what is the
 * door doing ([headline]), how long has it been doing it
 * ([stateSinceEpochSeconds]), may we present that as current ([liveness]),
 * how confidently may we draw it ([freshness]), and which of the three door
 * colours is it ([colorState]).
 *
 * **[stateSinceEpochSeconds] is when the DOOR last changed, not when we last
 * heard from it.** Those are different questions and only one of them is
 * interesting: "open for 8 minutes" is a fact about the garage, where "we
 * checked 8 minutes ago" is a fact about our plumbing. The second one also
 * decays the moment it is drawn, which is what makes it actively misleading
 * on a surface that redraws every ten minutes. It is still computed — it is
 * what [liveness] and [freshness] are derived FROM — but it is no longer
 * offered for display.
 *
 * **A surface that can render this instant as a self-updating duration
 * should.** Both Wear surfaces can: a complication via
 * `TimeDifferenceComplicationText`, a tile via ProtoLayout's platform time
 * source. Then the number is produced by the renderer at read time and cannot
 * go stale at all, whatever the update period. A surface that cannot should
 * show no number rather than a frozen one.
 */
data class GlanceStatus(
    val headline: StatusHeadline,
    val freshness: DataFreshness,
    val liveness: Liveness,
    val stateSinceEpochSeconds: Long?,
    val colorState: DoorColorState,
)

/**
 * Builds a [GlanceStatus] from what a glance surface can actually know.
 *
 * Composed entirely from the mappers the live screens already use —
 * [DataFreshnessMapper], [StatusHeadlineMapper], [CheckInStatusMapper] — so a
 * glance surface and the app it belongs to cannot reach different verdicts
 * about the same door. This object contributes two decisions of its own: the
 * settle-window one below, and collapsing freshness to a two-state
 * [Liveness].
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
     * @param lastCheckInEpochSeconds when the GARAGE last reported. Decides
     *   whether [doorPosition] is still worth believing, and is therefore an
     *   input to [Liveness] — but is never handed to a surface to print.
     * @param lastChangeEpochSeconds when the door entered its current state.
     *   This is the one a surface should show, ideally as a self-updating
     *   duration.
     * @param nowEpochSeconds the current wall clock.
     * @param isFetchError whether the most recent attempt to refresh failed.
     *   A glance that could not reach the server is showing a remembered
     *   value, and says so.
     */
    fun forGlance(
        doorPosition: DoorPosition?,
        lastCheckInEpochSeconds: Long?,
        lastChangeEpochSeconds: Long?,
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
            // `isMuted` is the verdict that means "we cannot vouch for this",
            // which is exactly the question Liveness answers. Deriving it here
            // rather than in each surface keeps the tile, the complication and
            // any future widget from drawing the line in different places.
            liveness = if (freshness.isMuted) Liveness.STALE else Liveness.LIVE,
            // Withheld when we cannot vouch for the reading: a duration is a
            // claim that the door has been this way continuously, and a door
            // we have lost contact with may have moved twice since. The
            // surface shows the liveness instead.
            stateSinceEpochSeconds = lastChangeEpochSeconds.takeIf { !freshness.isMuted },
            // Nothing known reads as the UNKNOWN (grey) door rather than as
            // a colour we would have to invent — the same thing the door
            // screen shows before it has heard anything.
            colorState = doorPosition?.let(DoorAnimation::colorStateFor) ?: DoorColorState.UNKNOWN,
        )
    }
}
