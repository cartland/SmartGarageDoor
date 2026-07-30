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

import com.chriscartland.garage.domain.model.DoorPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryRowMapperTest {
    private val t = 1_700_000_000L
    private val hour = 3_600L

    private fun opened(
        isCurrent: Boolean = false,
        misaligned: Boolean = false,
        transit: TransitWarning? = null,
        duration: Long = 2 * hour,
    ) = HistoryEntry.Opened(
        timeSeconds = t,
        durationSeconds = duration,
        isCurrent = isCurrent,
        transitWarning = transit,
        misaligned = misaligned,
    )

    private fun closed(
        isCurrent: Boolean = false,
        transit: TransitWarning? = null,
    ) = HistoryEntry.Closed(
        timeSeconds = t,
        durationSeconds = 2 * hour,
        isCurrent = isCurrent,
        transitWarning = transit,
    )

    // ---- the suppression rule (the reason this mapper exists) ----

    @Test
    fun aMisalignedTagIsSuppressedWhenTheHeadlineAlreadySaysIt() {
        val row = HistoryRowMapper.forEntry(opened(isCurrent = true, misaligned = true))
        assertEquals(HistoryHeadline.OpenNowMisaligned, row.headline)
        assertFalse(
            row.tags.contains(HistoryTag.Misaligned),
            "the headline already says (misaligned); the tag would repeat it",
        )
    }

    @Test
    fun aMisalignedTagAppearsWhenTheHeadlineDoesNot() {
        val row = HistoryRowMapper.forEntry(opened(isCurrent = false, misaligned = true))
        assertTrue(row.headline is HistoryHeadline.OpenedAt)
        assertTrue(
            row.tags.contains(HistoryTag.Misaligned),
            "a past misaligned open has nowhere else to report it",
        )
    }

    @Test
    fun noRowEverStatesMisalignmentTwice() {
        // The rule as a property over every combination, rather than the two
        // examples above: the misaligned tag and the misaligned headline are
        // mutually exclusive, always.
        listOf(true, false).forEach { isCurrent ->
            listOf(true, false).forEach { misaligned ->
                val row = HistoryRowMapper.forEntry(opened(isCurrent = isCurrent, misaligned = misaligned))
                val headlineSaysIt = row.headline == HistoryHeadline.OpenNowMisaligned
                val tagSaysIt = row.tags.contains(HistoryTag.Misaligned)
                assertFalse(
                    headlineSaysIt && tagSaysIt,
                    "isCurrent=$isCurrent misaligned=$misaligned said it twice",
                )
            }
        }
    }

    @Test
    fun misalignmentIsReportedExactlyOnceWhenPresent() {
        // The complement: suppression must not lose the information entirely.
        listOf(true, false).forEach { isCurrent ->
            val row = HistoryRowMapper.forEntry(opened(isCurrent = isCurrent, misaligned = true))
            val places = listOf(
                row.headline == HistoryHeadline.OpenNowMisaligned,
                row.tags.contains(HistoryTag.Misaligned),
            ).count { it }
            assertEquals(1, places, "isCurrent=$isCurrent should report misalignment exactly once")
        }
        // ...and never when absent.
        listOf(true, false).forEach { isCurrent ->
            val row = HistoryRowMapper.forEntry(opened(isCurrent = isCurrent, misaligned = false))
            assertFalse(row.headline == HistoryHeadline.OpenNowMisaligned)
            assertFalse(row.tags.contains(HistoryTag.Misaligned))
        }
    }

    // ---- door art ----

    @Test
    fun aMisalignedOpenShowsTheMisalignedArt() {
        assertEquals(
            DoorPosition.OPEN_MISALIGNED,
            HistoryRowMapper.forEntry(opened(misaligned = true)).doorPosition,
        )
        assertEquals(
            DoorPosition.OPEN,
            HistoryRowMapper.forEntry(opened(misaligned = false)).doorPosition,
        )
    }

    @Test
    fun theArtDoesNotDependOnWhetherTheSpanIsOngoing() {
        // Art tracks the door's state, not the row's tense.
        listOf(true, false).forEach { isCurrent ->
            assertEquals(
                DoorPosition.OPEN_MISALIGNED,
                HistoryRowMapper.forEntry(opened(isCurrent = isCurrent, misaligned = true)).doorPosition,
            )
        }
    }

    @Test
    fun closedRowsAlwaysShowClosedArt() {
        listOf(true, false).forEach { isCurrent ->
            assertEquals(DoorPosition.CLOSED, HistoryRowMapper.forEntry(closed(isCurrent = isCurrent)).doorPosition)
        }
    }

    @Test
    fun anomalyRowsCarryTheirOwnArt() {
        val row = HistoryRowMapper.forEntry(
            HistoryEntry.Anomaly(
                doorPosition = DoorPosition.ERROR_SENSOR_CONFLICT,
                kind = AnomalyKind.SensorConflict,
                timeSeconds = t,
            ),
        )
        assertEquals(DoorPosition.ERROR_SENSOR_CONFLICT, row.doorPosition)
        assertEquals(HistoryHeadline.Anomaly(AnomalyKind.SensorConflict), row.headline)
    }

    // ---- headline / supporting pairing ----

    @Test
    fun ongoingSpansNameTheirStartAndPastOnesDoNot() {
        // An ongoing row's headline has no timestamp ("Open"), so the supporting
        // line supplies it. A past row's headline already carries it ("Opened at
        // 9:47"), so repeating it below would be redundant.
        val current = HistoryRowMapper.forEntry(opened(isCurrent = true))
        assertEquals(HistoryHeadline.OpenNow, current.headline)
        assertTrue(current.supporting is HistorySupporting.SinceWithSpan)

        val past = HistoryRowMapper.forEntry(opened(isCurrent = false))
        assertEquals(HistoryHeadline.OpenedAt(t), past.headline)
        assertTrue(past.supporting is HistorySupporting.Span)
    }

    @Test
    fun exactlyOneOfHeadlineAndSupportingCarriesTheTimestamp() {
        // Stated as a property across both entry kinds and both tenses.
        listOf(opened(isCurrent = true), opened(isCurrent = false), closed(isCurrent = true), closed(isCurrent = false))
            .forEach { entry ->
                val row = HistoryRowMapper.forEntry(entry)
                val headlineHasTime = row.headline is HistoryHeadline.OpenedAt ||
                    row.headline is HistoryHeadline.ClosedAt
                val supportingHasTime = row.supporting is HistorySupporting.SinceWithSpan
                assertTrue(
                    headlineHasTime != supportingHasTime,
                    "$entry put the timestamp in both places or neither",
                )
            }
    }

    @Test
    fun closedHeadlinesFollowTheSameTensePattern() {
        assertEquals(HistoryHeadline.ClosedNow, HistoryRowMapper.forEntry(closed(isCurrent = true)).headline)
        assertEquals(HistoryHeadline.ClosedAt(t), HistoryRowMapper.forEntry(closed(isCurrent = false)).headline)
    }

    @Test
    fun anomalyRowsReportOnlyAClockTime() {
        val row = HistoryRowMapper.forEntry(
            HistoryEntry.Anomaly(DoorPosition.UNKNOWN, AnomalyKind.UnknownState, t),
        )
        assertEquals(HistorySupporting.ClockTime(t), row.supporting)
        assertTrue(row.tags.isEmpty(), "anomaly rows carry no tags")
    }

    // ---- the span embedded in the supporting line ----

    @Test
    fun theSupportingSpanUsesTheStateLadderAndTheOngoingFraming() {
        val row = HistoryRowMapper.forEntry(opened(isCurrent = true, duration = 2 * hour + 14 * 60))
        val supporting = row.supporting as HistorySupporting.SinceWithSpan
        assertEquals(StateSpanFraming.AND_COUNTING, supporting.span.framing)
        assertEquals(StateSpanDuration.HoursMinutes(2, 14), supporting.span.duration)
    }

    @Test
    fun completedOpenAndCloseSpansAreFramedDifferently() {
        val open = HistoryRowMapper.forEntry(opened(isCurrent = false)).supporting as HistorySupporting.Span
        val close = HistoryRowMapper.forEntry(closed(isCurrent = false)).supporting as HistorySupporting.Span
        assertEquals(StateSpanFraming.OPEN_FOR, open.span.framing)
        assertEquals(StateSpanFraming.CLOSED_FOR, close.span.framing)
    }

    // ---- tags ----

    @Test
    fun aTransitWarningBecomesTheFirstTag() {
        val warning = TransitWarning.ToOpen(transitSeconds = 240)
        val row = HistoryRowMapper.forEntry(opened(misaligned = true, transit = warning))
        assertEquals(HistoryTag.Transit(warning), row.tags.first())
        // Order matters: the transit is about this event, the misalignment about
        // the resulting state.
        assertEquals(listOf(HistoryTag.Transit(warning), HistoryTag.Misaligned), row.tags)
    }

    @Test
    fun closedRowsCanCarryATransitTag() {
        val warning = TransitWarning.ToClose(transitSeconds = 300)
        assertEquals(
            listOf(HistoryTag.Transit(warning)),
            HistoryRowMapper.forEntry(closed(transit = warning)).tags,
        )
    }

    @Test
    fun aCleanRowHasNoTags() {
        assertTrue(HistoryRowMapper.forEntry(opened()).tags.isEmpty())
        assertTrue(HistoryRowMapper.forEntry(closed()).tags.isEmpty())
    }
}
