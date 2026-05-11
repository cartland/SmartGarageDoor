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

package com.chriscartland.garage.ui.history

import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.ui.history.HistoryMapper.MergedRecord
import com.chriscartland.garage.ui.history.HistoryMapper.WithDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class HistoryMapperTest {
    private val zone = ZoneOffset.UTC
    private val now: Instant = Instant.parse("2026-04-29T17:30:00Z")

    private fun event(
        position: DoorPosition,
        time: Long,
    ): DoorEvent = DoorEvent(doorPosition = position, lastChangeTimeSeconds = time)

    // ---------- dedupConsecutive ----------

    @Test
    fun dedup_emptyInput() {
        assertEquals(emptyList<DoorEvent>(), HistoryMapper.dedupConsecutive(emptyList()))
    }

    @Test
    fun dedup_singleEventPassesThrough() {
        val e = event(DoorPosition.OPEN, 10)
        assertEquals(listOf(e), HistoryMapper.dedupConsecutive(listOf(e)))
    }

    @Test
    fun dedup_twoConsecutiveSamePosition_keepsFirst() {
        val first = event(DoorPosition.OPEN, 10)
        val second = event(DoorPosition.OPEN, 20)
        assertEquals(listOf(first), HistoryMapper.dedupConsecutive(listOf(first, second)))
    }

    @Test
    fun dedup_twoConsecutiveDifferentPositions_bothKept() {
        val open = event(DoorPosition.OPEN, 10)
        val closed = event(DoorPosition.CLOSED, 20)
        assertEquals(listOf(open, closed), HistoryMapper.dedupConsecutive(listOf(open, closed)))
    }

    @Test
    fun dedup_aba_allKept() {
        val a1 = event(DoorPosition.OPEN, 10)
        val b = event(DoorPosition.CLOSED, 20)
        val a2 = event(DoorPosition.OPEN, 30)
        assertEquals(listOf(a1, b, a2), HistoryMapper.dedupConsecutive(listOf(a1, b, a2)))
    }

    @Test
    fun dedup_fourConsecutiveDuplicates_onlyFirstKept() {
        val first = event(DoorPosition.OPEN, 10)
        val rest = (1..3).map { event(DoorPosition.OPEN, 10L + it * 10) }
        assertEquals(listOf(first), HistoryMapper.dedupConsecutive(listOf(first) + rest))
    }

    // ---------- mergeEvents ----------

    @Test
    fun merge_emptyInput() {
        assertEquals(emptyList<MergedRecord>(), HistoryMapper.mergeEvents(emptyList()))
    }

    @Test
    fun merge_singleOpen_emitsOpened() {
        val out = HistoryMapper.mergeEvents(listOf(event(DoorPosition.OPEN, 100)))
        assertEquals(listOf(MergedRecord.Opened(timeSeconds = 100, transitDurationSeconds = null)), out)
    }

    @Test
    fun merge_singleClosed_emitsClosed() {
        val out = HistoryMapper.mergeEvents(listOf(event(DoorPosition.CLOSED, 100)))
        assertEquals(listOf(MergedRecord.Closed(timeSeconds = 100, transitDurationSeconds = null)), out)
    }

    @Test
    fun merge_openingThenOpen_silentTransitInOpened() {
        val out = HistoryMapper.mergeEvents(
            listOf(
                event(DoorPosition.OPENING, 90),
                event(DoorPosition.OPEN, 100),
            ),
        )
        // Normal transit (no _TOO_LONG) doesn't surface as a warning.
        assertEquals(listOf(MergedRecord.Opened(timeSeconds = 100, transitDurationSeconds = null)), out)
    }

    @Test
    fun merge_openingTooLongThenOpen_emitsTransitDuration() {
        val out = HistoryMapper.mergeEvents(
            listOf(
                event(DoorPosition.OPENING_TOO_LONG, 90),
                event(DoorPosition.OPEN, 100),
            ),
        )
        assertEquals(listOf(MergedRecord.Opened(timeSeconds = 100, transitDurationSeconds = 10)), out)
    }

    @Test
    fun merge_openingThenTooLongThenOpen_durationFromFirstOpening() {
        val out = HistoryMapper.mergeEvents(
            listOf(
                event(DoorPosition.OPENING, 80),
                event(DoorPosition.OPENING_TOO_LONG, 95),
                event(DoorPosition.OPEN, 100),
            ),
        )
        // Transit duration spans from the first OPENING (80) to the terminal (100) = 20s.
        assertEquals(listOf(MergedRecord.Opened(timeSeconds = 100, transitDurationSeconds = 20)), out)
    }

    @Test
    fun merge_lonelyOpening_dropped() {
        val out = HistoryMapper.mergeEvents(listOf(event(DoorPosition.OPENING, 90)))
        assertEquals(emptyList<MergedRecord>(), out)
    }

    @Test
    fun merge_lonelyOpeningTooLong_emitsStuckOpeningAnomaly() {
        val out = HistoryMapper.mergeEvents(listOf(event(DoorPosition.OPENING_TOO_LONG, 90)))
        assertEquals(
            listOf(
                MergedRecord.Anomaly(
                    timeSeconds = 90,
                    position = DoorPosition.OPENING_TOO_LONG,
                    kind = AnomalyKind.StuckOpening,
                ),
            ),
            out,
        )
    }

    @Test
    fun merge_openingInterruptedByClosingThatReachesClosed_openingDroppedClosedEmitted() {
        val out = HistoryMapper.mergeEvents(
            listOf(
                event(DoorPosition.OPENING, 80),
                event(DoorPosition.CLOSING, 85),
                event(DoorPosition.CLOSED, 100),
            ),
        )
        // Plain OPENING that didn't resolve to OPEN drops silently — only the
        // CLOSED row remains.
        assertEquals(listOf(MergedRecord.Closed(timeSeconds = 100, transitDurationSeconds = null)), out)
    }

    @Test
    fun merge_openingTooLongInterruptedByClosingTerminal_anomalyAndClosed() {
        val out = HistoryMapper.mergeEvents(
            listOf(
                event(DoorPosition.OPENING_TOO_LONG, 80),
                event(DoorPosition.CLOSING, 85),
                event(DoorPosition.CLOSED, 100),
            ),
        )
        assertEquals(
            listOf(
                MergedRecord.Anomaly(timeSeconds = 80, position = DoorPosition.OPENING_TOO_LONG, kind = AnomalyKind.StuckOpening),
                MergedRecord.Closed(timeSeconds = 100, transitDurationSeconds = null),
            ),
            out,
        )
    }

    @Test
    fun merge_closingThenClosed_silentTransit() {
        val out = HistoryMapper.mergeEvents(
            listOf(
                event(DoorPosition.CLOSING, 90),
                event(DoorPosition.CLOSED, 100),
            ),
        )
        assertEquals(listOf(MergedRecord.Closed(timeSeconds = 100, transitDurationSeconds = null)), out)
    }

    @Test
    fun merge_closingTooLongThenClosed_emitsTransitDuration() {
        val out = HistoryMapper.mergeEvents(
            listOf(
                event(DoorPosition.CLOSING_TOO_LONG, 90),
                event(DoorPosition.CLOSED, 100),
            ),
        )
        assertEquals(listOf(MergedRecord.Closed(timeSeconds = 100, transitDurationSeconds = 10)), out)
    }

    @Test
    fun merge_lonelyClosingTooLong_emitsStuckClosingAnomaly() {
        val out = HistoryMapper.mergeEvents(listOf(event(DoorPosition.CLOSING_TOO_LONG, 90)))
        assertEquals(
            listOf(
                MergedRecord.Anomaly(timeSeconds = 90, position = DoorPosition.CLOSING_TOO_LONG, kind = AnomalyKind.StuckClosing),
            ),
            out,
        )
    }

    @Test
    fun merge_openMisalignedAfterPendingOpening_terminatesAsOpenedWithMisaligned() {
        // OPEN_MISALIGNED with a pending Opening is treated as a terminal —
        // the door reached its open destination but in a misaligned state.
        val out = HistoryMapper.mergeEvents(
            listOf(
                event(DoorPosition.OPENING, 80),
                event(DoorPosition.OPEN_MISALIGNED, 100),
            ),
        )
        assertEquals(
            listOf(
                MergedRecord.Opened(timeSeconds = 100, transitDurationSeconds = null, misaligned = true),
            ),
            out,
        )
    }

    @Test
    fun merge_openMisalignedAfterPendingOpeningTooLong_carriesTransitWarning() {
        // Same as the previous case but the opening was tooLong — preserve
        // the transit duration so the row gets a "longer than expected" tag.
        val out = HistoryMapper.mergeEvents(
            listOf(
                event(DoorPosition.OPENING_TOO_LONG, 80),
                event(DoorPosition.OPEN_MISALIGNED, 100),
            ),
        )
        assertEquals(
            listOf(
                MergedRecord.Opened(timeSeconds = 100, transitDurationSeconds = 20, misaligned = true),
            ),
            out,
        )
    }

    @Test
    fun merge_openMisalignedAfterOpened_mergesIntoPreviousOpened() {
        // The user's rule: misalignment usually happens immediately after
        // open and merges with the previous open.
        val out = HistoryMapper.mergeEvents(
            listOf(
                event(DoorPosition.OPEN, 100),
                event(DoorPosition.OPEN_MISALIGNED, 110),
            ),
        )
        assertEquals(
            listOf(
                MergedRecord.Opened(timeSeconds = 100, transitDurationSeconds = null, misaligned = true),
            ),
            out,
        )
    }

    @Test
    fun merge_openMisalignedRepeatedAfterOpened_idempotent() {
        // Multiple OPEN_MISALIGNED heartbeats after an Opened don't
        // duplicate; the misaligned flag stays true.
        val out = HistoryMapper.mergeEvents(
            listOf(
                event(DoorPosition.OPEN, 100),
                event(DoorPosition.OPEN_MISALIGNED, 110),
                event(DoorPosition.OPEN_MISALIGNED, 120),
                event(DoorPosition.OPEN_MISALIGNED, 130),
            ),
        )
        assertEquals(
            listOf(
                MergedRecord.Opened(timeSeconds = 100, transitDurationSeconds = null, misaligned = true),
            ),
            out,
        )
    }

    @Test
    fun merge_openMisalignedAcrossAnomaly_stillMergesIntoOpened() {
        // Anomalies between an Opened and an OPEN_MISALIGNED don't break
        // the merge — the misalignment still applies to the open state.
        val out = HistoryMapper.mergeEvents(
            listOf(
                event(DoorPosition.OPEN, 100),
                event(DoorPosition.ERROR_SENSOR_CONFLICT, 110),
                event(DoorPosition.OPEN_MISALIGNED, 120),
            ),
        )
        assertEquals(
            listOf(
                MergedRecord.Opened(timeSeconds = 100, transitDurationSeconds = null, misaligned = true),
                MergedRecord.Anomaly(timeSeconds = 110, position = DoorPosition.ERROR_SENSOR_CONFLICT, kind = AnomalyKind.SensorConflict),
            ),
            out,
        )
    }

    @Test
    fun merge_openMisalignedAfterClosed_fallsBackToAnomaly() {
        // Once the door has been Closed, an OPEN_MISALIGNED doesn't merge
        // (no current open context) — emit a standalone Anomaly.
        val out = HistoryMapper.mergeEvents(
            listOf(
                event(DoorPosition.OPEN, 100),
                event(DoorPosition.CLOSED, 200),
                event(DoorPosition.OPEN_MISALIGNED, 300),
            ),
        )
        assertEquals(
            listOf(
                MergedRecord.Opened(timeSeconds = 100, transitDurationSeconds = null, misaligned = false),
                MergedRecord.Closed(timeSeconds = 200, transitDurationSeconds = null),
                MergedRecord.Anomaly(timeSeconds = 300, position = DoorPosition.OPEN_MISALIGNED, kind = AnomalyKind.OpenMisaligned),
            ),
            out,
        )
    }

    @Test
    fun merge_openMisalignedFirstEvent_fallsBackToAnomaly() {
        // No previous Opened, no pending Opening — fall back to Anomaly so
        // the data isn't silently dropped.
        val out = HistoryMapper.mergeEvents(listOf(event(DoorPosition.OPEN_MISALIGNED, 100)))
        assertEquals(
            listOf(
                MergedRecord.Anomaly(timeSeconds = 100, position = DoorPosition.OPEN_MISALIGNED, kind = AnomalyKind.OpenMisaligned),
            ),
            out,
        )
    }

    @Test
    fun merge_sensorConflict_emitsAnomaly() {
        val out = HistoryMapper.mergeEvents(listOf(event(DoorPosition.ERROR_SENSOR_CONFLICT, 100)))
        assertEquals(
            listOf(
                MergedRecord.Anomaly(timeSeconds = 100, position = DoorPosition.ERROR_SENSOR_CONFLICT, kind = AnomalyKind.SensorConflict),
            ),
            out,
        )
    }

    @Test
    fun merge_unknown_emitsAnomaly() {
        val out = HistoryMapper.mergeEvents(listOf(event(DoorPosition.UNKNOWN, 100)))
        assertEquals(
            listOf(
                MergedRecord.Anomaly(timeSeconds = 100, position = DoorPosition.UNKNOWN, kind = AnomalyKind.UnknownState),
            ),
            out,
        )
    }

    @Test
    fun merge_openMisalignedBetweenOpenAndOpen_misalignmentMergesIntoFirstOpened() {
        // The first OPEN absorbs the OPEN_MISALIGNED via the merge rule. The
        // second OPEN is a separate event (a new "open" reading after the
        // misalignment cleared, semantically).
        val out = HistoryMapper.mergeEvents(
            listOf(
                event(DoorPosition.OPEN, 100),
                event(DoorPosition.OPEN_MISALIGNED, 200),
                event(DoorPosition.OPEN, 300),
            ),
        )
        assertEquals(
            listOf(
                MergedRecord.Opened(timeSeconds = 100, transitDurationSeconds = null, misaligned = true),
                MergedRecord.Opened(timeSeconds = 300, transitDurationSeconds = null, misaligned = false),
            ),
            out,
        )
    }

    @Test
    fun merge_sensorConflictDuringOpening_anomalyEmittedPendingPreserved() {
        val out = HistoryMapper.mergeEvents(
            listOf(
                event(DoorPosition.OPENING_TOO_LONG, 80),
                event(DoorPosition.ERROR_SENSOR_CONFLICT, 90),
                event(DoorPosition.OPEN, 100),
            ),
        )
        // Sensor conflict doesn't clear pending — the OPENING_TOO_LONG still
        // resolves into the eventual OPEN with a transit warning.
        assertEquals(
            listOf(
                MergedRecord.Anomaly(timeSeconds = 90, position = DoorPosition.ERROR_SENSOR_CONFLICT, kind = AnomalyKind.SensorConflict),
                MergedRecord.Opened(timeSeconds = 100, transitDurationSeconds = 20),
            ),
            out,
        )
    }

    @Test
    fun merge_openingTooLongFollowedByOppositeClosed_flushesStuckOpening() {
        // Symmetric to the OPENING_TOO_LONG → CLOSING → CLOSED case but
        // with the OPEN arriving while a CLOSING_TOO_LONG is pending.
        // The pending stuck-closing must flush as an anomaly when OPEN
        // arrives (which is opposite direction).
        val out = HistoryMapper.mergeEvents(
            listOf(
                event(DoorPosition.CLOSING_TOO_LONG, 80),
                event(DoorPosition.OPEN, 100),
            ),
        )
        assertEquals(
            listOf(
                MergedRecord.Anomaly(timeSeconds = 80, position = DoorPosition.CLOSING_TOO_LONG, kind = AnomalyKind.StuckClosing),
                MergedRecord.Opened(timeSeconds = 100, transitDurationSeconds = null),
            ),
            out,
        )
    }

    @Test
    fun merge_closingTooLongFollowedByOppositeOpened_flushesStuckClosing() {
        // Equivalent to the previous test but flushed via CLOSED arriving
        // with a pending OPENING_TOO_LONG.
        val out = HistoryMapper.mergeEvents(
            listOf(
                event(DoorPosition.OPENING_TOO_LONG, 80),
                event(DoorPosition.CLOSED, 100),
            ),
        )
        assertEquals(
            listOf(
                MergedRecord.Anomaly(timeSeconds = 80, position = DoorPosition.OPENING_TOO_LONG, kind = AnomalyKind.StuckOpening),
                MergedRecord.Closed(timeSeconds = 100, transitDurationSeconds = null),
            ),
            out,
        )
    }

    @Test
    fun merge_fullCycleSequence() {
        val out = HistoryMapper.mergeEvents(
            listOf(
                event(DoorPosition.OPENING, 80),
                event(DoorPosition.OPEN, 90),
                event(DoorPosition.CLOSING, 100),
                event(DoorPosition.CLOSED, 110),
                event(DoorPosition.OPENING, 200),
                event(DoorPosition.OPEN, 210),
            ),
        )
        assertEquals(
            listOf(
                MergedRecord.Opened(timeSeconds = 90, transitDurationSeconds = null),
                MergedRecord.Closed(timeSeconds = 110, transitDurationSeconds = null),
                MergedRecord.Opened(timeSeconds = 210, transitDurationSeconds = null),
            ),
            out,
        )
    }

    // ---------- computeDurations ----------

    @Test
    fun durations_emptyInput() {
        assertEquals(emptyList<WithDuration>(), HistoryMapper.computeDurations(emptyList(), now))
    }

    @Test
    fun durations_singleOpened_isCurrent_durationToNow() {
        val rec = MergedRecord.Opened(timeSeconds = now.epochSecond - 600, transitDurationSeconds = null)
        val out = HistoryMapper.computeDurations(listOf(rec), now)
        assertEquals(1, out.size)
        assertEquals(rec, out[0].record)
        assertEquals(600L, out[0].durationSeconds)
        assertTrue(out[0].isCurrent)
    }

    @Test
    fun durations_singleClosed_isCurrent_durationToNow() {
        val rec = MergedRecord.Closed(timeSeconds = now.epochSecond - 1800, transitDurationSeconds = null)
        val out = HistoryMapper.computeDurations(listOf(rec), now)
        assertTrue(out[0].isCurrent)
        assertEquals(1800L, out[0].durationSeconds)
    }

    @Test
    fun durations_openedThenClosed_openedHasFiniteDuration_closedIsCurrent() {
        val opened = MergedRecord.Opened(timeSeconds = now.epochSecond - 1200, transitDurationSeconds = null)
        val closed = MergedRecord.Closed(timeSeconds = now.epochSecond - 600, transitDurationSeconds = null)
        val out = HistoryMapper.computeDurations(listOf(opened, closed), now)
        assertEquals(2, out.size)
        // Opened: 600s long (until Closed)
        assertEquals(600L, out[0].durationSeconds)
        assertEquals(false, out[0].isCurrent)
        // Closed: 600s and counting (no later Opened)
        assertEquals(600L, out[1].durationSeconds)
        assertTrue(out[1].isCurrent)
    }

    @Test
    fun durations_openedClosedOpened_threeWithLatestOpenedCurrent() {
        val o1 = MergedRecord.Opened(timeSeconds = now.epochSecond - 3000, transitDurationSeconds = null)
        val c1 = MergedRecord.Closed(timeSeconds = now.epochSecond - 2400, transitDurationSeconds = null)
        val o2 = MergedRecord.Opened(timeSeconds = now.epochSecond - 600, transitDurationSeconds = null)
        val out = HistoryMapper.computeDurations(listOf(o1, c1, o2), now)
        assertEquals(3, out.size)
        // o1: closed at -2400, opened at -3000 → 600s open
        assertEquals(600L, out[0].durationSeconds)
        assertEquals(false, out[0].isCurrent)
        // c1: opened at -600, closed at -2400 → 1800s closed
        assertEquals(1800L, out[1].durationSeconds)
        assertEquals(false, out[1].isCurrent)
        // o2: no later closed → 600s open and counting
        assertEquals(600L, out[2].durationSeconds)
        assertTrue(out[2].isCurrent)
    }

    @Test
    fun durations_anomaliesPassThroughWithoutDuration() {
        val anomaly = MergedRecord.Anomaly(
            timeSeconds = now.epochSecond - 100,
            position = DoorPosition.UNKNOWN,
            kind = AnomalyKind.UnknownState,
        )
        val out = HistoryMapper.computeDurations(listOf(anomaly), now)
        assertEquals(1, out.size)
        assertEquals(null, out[0].durationSeconds)
        assertEquals(false, out[0].isCurrent)
    }

    @Test
    fun durations_consecutiveOpenedsBoundByEachOther() {
        // Two adjacent Openeds (an OPEN_MISALIGNED merged into the first,
        // then another OPEN) must not overlap durations.
        val o1 = MergedRecord.Opened(timeSeconds = now.epochSecond - 3000, transitDurationSeconds = null, misaligned = true)
        val o2 = MergedRecord.Opened(timeSeconds = now.epochSecond - 1800, transitDurationSeconds = null)
        val c = MergedRecord.Closed(timeSeconds = now.epochSecond - 600, transitDurationSeconds = null)
        val out = HistoryMapper.computeDurations(listOf(o1, o2, c), now)
        // o1 ends at o2 → 1200s
        assertEquals(1200L, out[0].durationSeconds)
        // o2 ends at c → 1200s
        assertEquals(1200L, out[1].durationSeconds)
        // c is current → 600s and counting
        assertTrue(out[2].isCurrent)
    }

    @Test
    fun durations_consecutiveClosedsBoundByEachOther() {
        // Symmetric for two adjacent Closeds.
        val c1 = MergedRecord.Closed(timeSeconds = now.epochSecond - 3000, transitDurationSeconds = null)
        val c2 = MergedRecord.Closed(timeSeconds = now.epochSecond - 1800, transitDurationSeconds = null)
        val o = MergedRecord.Opened(timeSeconds = now.epochSecond - 600, transitDurationSeconds = null)
        val out = HistoryMapper.computeDurations(listOf(c1, c2, o), now)
        assertEquals(1200L, out[0].durationSeconds)
        assertEquals(1200L, out[1].durationSeconds)
        assertTrue(out[2].isCurrent)
    }

    @Test
    fun durations_anomalyBetweenTerminalsDoesNotInterfere() {
        val o = MergedRecord.Opened(timeSeconds = now.epochSecond - 3000, transitDurationSeconds = null)
        val a = MergedRecord.Anomaly(timeSeconds = now.epochSecond - 1500, position = DoorPosition.UNKNOWN, kind = AnomalyKind.UnknownState)
        val c = MergedRecord.Closed(timeSeconds = now.epochSecond - 600, transitDurationSeconds = null)
        val out = HistoryMapper.computeDurations(listOf(o, a, c), now)
        // Opened reaches forward through anomaly to closed at -600 → 2400s
        assertEquals(2400L, out[0].durationSeconds)
        assertEquals(false, out[0].isCurrent)
        // Anomaly: no duration
        assertEquals(null, out[1].durationSeconds)
        // Closed: current
        assertTrue(out[2].isCurrent)
    }

    // ---------- typed dayLabel ----------
    //
    // (Phase 2E — formatStateDuration / formatTransitDuration / formatTime
    //  were removed from HistoryMapper. Their pure-function equivalents live
    //  in HistoryStatusFormatter (`formatTime`, `stateDurationParts`,
    //  `transitDurationParts`) and are tested in HistoryFormatterTest. The
    //  Composable layer in HistoryContent.kt assembles the final localized
    //  strings via `stringResource` + `pluralStringResource` at render time.
    //  formatDayLabel is replaced by `dayLabel` returning a typed [DayLabel]
    //  — tested below.)

    @Test
    fun dayLabel_today() {
        val today = LocalDate.parse("2026-04-29")
        assertEquals(DayLabel.Today, HistoryMapper.dayLabel(today, today))
    }

    @Test
    fun dayLabel_yesterday() {
        val today = LocalDate.parse("2026-04-29")
        val yesterday = today.minusDays(1)
        assertEquals(DayLabel.Yesterday, HistoryMapper.dayLabel(yesterday, today))
    }

    @Test
    fun dayLabel_twoDaysAgo() {
        val today = LocalDate.parse("2026-04-29")
        val twoAgo = today.minusDays(2)
        assertEquals(DayLabel.Date(twoAgo), HistoryMapper.dayLabel(twoAgo, today))
    }

    @Test
    fun dayLabel_lastWeek() {
        val today = LocalDate.parse("2026-04-29")
        val lastWeek = today.minusDays(7)
        assertEquals(DayLabel.Date(lastWeek), HistoryMapper.dayLabel(lastWeek, today))
    }

    // ---------- toHistoryDays end-to-end ----------

    @Test
    fun e2e_emptyInput() {
        assertEquals(emptyList<HistoryDay>(), HistoryMapper.toHistoryDays(emptyList(), now, zone))
    }

    @Test
    fun e2e_eventsWithNullPositionFiltered() {
        val events = listOf(
            DoorEvent(doorPosition = null, lastChangeTimeSeconds = 100),
            DoorEvent(doorPosition = DoorPosition.OPEN, lastChangeTimeSeconds = null),
            event(DoorPosition.OPEN, now.epochSecond - 600),
        )
        val days = HistoryMapper.toHistoryDays(events, now, zone)
        assertEquals(1, days.size)
        assertEquals(1, days[0].entries.size)
        assertEquals(DayLabel.Today, days[0].label)
    }

    @Test
    fun e2e_singleOpenToday_currentlyOpenRow() {
        val events = listOf(event(DoorPosition.OPEN, now.epochSecond - 720))
        val days = HistoryMapper.toHistoryDays(events, now, zone)
        assertEquals(1, days.size)
        assertEquals(DayLabel.Today, days[0].label)
        val entry = days[0].entries.single() as HistoryEntry.Opened
        assertTrue(entry.isCurrent)
        assertEquals(720L, entry.durationSeconds) // 12 min
        assertEquals(null, entry.transitWarning)
    }

    @Test
    fun e2e_singleClosedToday_currentlyClosedRow() {
        val events = listOf(event(DoorPosition.CLOSED, now.epochSecond - 720))
        val days = HistoryMapper.toHistoryDays(events, now, zone)
        val entry = days[0].entries.single() as HistoryEntry.Closed
        assertTrue(entry.isCurrent)
        assertEquals(720L, entry.durationSeconds) // 12 min
    }

    @Test
    fun e2e_pastOpenedRow_durationDescribesOpenSpan() {
        val openedTime = now.epochSecond - 1800 // 30 min ago
        val closedTime = now.epochSecond - 1080 // 18 min ago — open for 12 min
        val events = listOf(
            event(DoorPosition.OPEN, openedTime),
            event(DoorPosition.CLOSED, closedTime),
        )
        val days = HistoryMapper.toHistoryDays(events, now, zone)
        // Display order is newest-first within the day, so [Closed, Opened].
        val closed = days[0].entries[0] as HistoryEntry.Closed
        val opened = days[0].entries[1] as HistoryEntry.Opened
        assertTrue(closed.isCurrent)
        assertEquals(false, opened.isCurrent)
        assertEquals(720L, opened.durationSeconds) // 12 min
    }

    @Test
    fun e2e_warningTagForOpeningTooLong() {
        val events = listOf(
            event(DoorPosition.OPENING_TOO_LONG, now.epochSecond - 1500),
            event(DoorPosition.OPEN, now.epochSecond - 1260), // 4 min later
            event(DoorPosition.CLOSED, now.epochSecond - 600),
        )
        val days = HistoryMapper.toHistoryDays(events, now, zone)
        val opened = days[0].entries.last() as HistoryEntry.Opened
        assertEquals(TransitWarning.ToOpen(transitSeconds = 240L), opened.transitWarning)
    }

    @Test
    fun e2e_warningTagForClosingTooLong() {
        val events = listOf(
            event(DoorPosition.OPEN, now.epochSecond - 1800),
            event(DoorPosition.CLOSING_TOO_LONG, now.epochSecond - 800),
            event(DoorPosition.CLOSED, now.epochSecond - 620), // 3 min later
        )
        val days = HistoryMapper.toHistoryDays(events, now, zone)
        // Newest-first: closed, opened
        val closed = days[0].entries[0] as HistoryEntry.Closed
        assertEquals(TransitWarning.ToClose(transitSeconds = 180L), closed.transitWarning)
    }

    @Test
    fun e2e_misalignedMergesIntoPreviousOpenedRow() {
        val events = listOf(
            event(DoorPosition.OPEN, now.epochSecond - 3000),
            event(DoorPosition.OPEN_MISALIGNED, now.epochSecond - 2400),
            event(DoorPosition.OPEN, now.epochSecond - 1800),
            event(DoorPosition.CLOSED, now.epochSecond - 600),
        )
        val days = HistoryMapper.toHistoryDays(events, now, zone)
        // 3 entries (newest-first within day): Closed, Opened (no misaligned),
        // Opened (misaligned merged from OPEN_MISALIGNED). The misalignment is
        // a property of the older Opened — not a separate row.
        assertEquals(3, days[0].entries.size)
        val closed = days[0].entries[0] as HistoryEntry.Closed
        val openedNew = days[0].entries[1] as HistoryEntry.Opened
        val openedMisaligned = days[0].entries[2] as HistoryEntry.Opened
        assertTrue(closed.isCurrent)
        assertEquals(false, openedNew.misaligned)
        assertTrue(openedMisaligned.misaligned)
    }

    @Test
    fun e2e_misalignedAfterClosedFallsBackToAnomalyRow() {
        // After a Closed, OPEN_MISALIGNED has no open context to merge with —
        // it surfaces as a standalone Anomaly so the data is preserved.
        val events = listOf(
            event(DoorPosition.OPEN, now.epochSecond - 3000),
            event(DoorPosition.CLOSED, now.epochSecond - 1800),
            event(DoorPosition.OPEN_MISALIGNED, now.epochSecond - 600),
        )
        val days = HistoryMapper.toHistoryDays(events, now, zone)
        // Newest-first: Anomaly, Closed, Opened.
        val anomaly = days[0].entries[0] as HistoryEntry.Anomaly
        assertEquals(AnomalyKind.OpenMisaligned, anomaly.kind)
        assertEquals(DoorPosition.OPEN_MISALIGNED, anomaly.doorPosition)
    }

    @Test
    fun e2e_consecutiveOpenedsDoNotOverlapDurations() {
        // Two Opened rows separated by an OPEN_MISALIGNED (which merges into
        // the first) and a fresh OPEN. The first Opened's duration must end
        // at the second Opened's time, not extend through it to the eventual
        // Closed (otherwise the durations overlap).
        val t1 = now.epochSecond - 3000
        val t2 = now.epochSecond - 1800
        val tClosed = now.epochSecond - 600
        val events = listOf(
            event(DoorPosition.OPEN, t1),
            event(DoorPosition.OPEN_MISALIGNED, now.epochSecond - 2400),
            event(DoorPosition.OPEN, t2),
            event(DoorPosition.CLOSED, tClosed),
        )
        val days = HistoryMapper.toHistoryDays(events, now, zone)
        val openedNew = days[0].entries[1] as HistoryEntry.Opened
        val openedMisaligned = days[0].entries[2] as HistoryEntry.Opened
        // openedMisaligned (older, t1): bounded by next Opened at t2 → 1200s = 20 min.
        assertEquals(1200L, openedMisaligned.durationSeconds) // 20 min
        // openedNew (t2): bounded by Closed at tClosed → 1200s = 20 min.
        assertEquals(1200L, openedNew.durationSeconds) // 20 min
    }

    @Test
    fun e2e_currentlyOpenAndMisaligned_headlineSaysOpenMisaligned() {
        val events = listOf(
            event(DoorPosition.OPEN, now.epochSecond - 600),
            event(DoorPosition.OPEN_MISALIGNED, now.epochSecond - 300),
        )
        val days = HistoryMapper.toHistoryDays(events, now, zone)
        val opened = days[0].entries.single() as HistoryEntry.Opened
        assertTrue(opened.isCurrent)
        assertTrue(opened.misaligned)
    }

    @Test
    fun e2e_unknownStateAnomaly() {
        val events = listOf(event(DoorPosition.UNKNOWN, now.epochSecond - 600))
        val days = HistoryMapper.toHistoryDays(events, now, zone)
        val anomaly = days[0].entries.single() as HistoryEntry.Anomaly
        assertEquals(AnomalyKind.UnknownState, anomaly.kind)
        assertEquals(DoorPosition.UNKNOWN, anomaly.doorPosition)
    }

    @Test
    fun e2e_sensorConflictAnomaly() {
        val events = listOf(event(DoorPosition.ERROR_SENSOR_CONFLICT, now.epochSecond - 600))
        val days = HistoryMapper.toHistoryDays(events, now, zone)
        val anomaly = days[0].entries.single() as HistoryEntry.Anomaly
        assertEquals(AnomalyKind.SensorConflict, anomaly.kind)
    }

    @Test
    fun e2e_stuckOpeningAnomalyWhenNeverResolved() {
        val events = listOf(event(DoorPosition.OPENING_TOO_LONG, now.epochSecond - 600))
        val days = HistoryMapper.toHistoryDays(events, now, zone)
        val anomaly = days[0].entries.single() as HistoryEntry.Anomaly
        assertEquals(AnomalyKind.StuckOpening, anomaly.kind)
        assertEquals(DoorPosition.OPENING_TOO_LONG, anomaly.doorPosition)
    }

    @Test
    fun e2e_stuckClosingAnomalyWhenNeverResolved() {
        val events = listOf(event(DoorPosition.CLOSING_TOO_LONG, now.epochSecond - 600))
        val days = HistoryMapper.toHistoryDays(events, now, zone)
        val anomaly = days[0].entries.single() as HistoryEntry.Anomaly
        assertEquals(AnomalyKind.StuckClosing, anomaly.kind)
        assertEquals(DoorPosition.CLOSING_TOO_LONG, anomaly.doorPosition)
    }

    @Test
    fun e2e_eventsSpanMultipleDays_groupedNewestFirst() {
        val today = now.epochSecond - 600 // 12:20 PM today (now=17:30 UTC, so -600 ≈ 17:20)
        val yesterday = Instant.parse("2026-04-28T20:30:00Z").epochSecond
        val twoAgo = Instant.parse("2026-04-27T07:15:00Z").epochSecond
        val events = listOf(
            event(DoorPosition.OPEN, twoAgo),
            event(DoorPosition.CLOSED, yesterday),
            event(DoorPosition.OPEN, today),
        )
        val days = HistoryMapper.toHistoryDays(events, now, zone)
        assertEquals(3, days.size)
        assertEquals(DayLabel.Today, days[0].label)
        assertEquals(DayLabel.Yesterday, days[1].label)
        assertEquals(DayLabel.Date(LocalDate.parse("2026-04-27")), days[2].label)
    }

    @Test
    fun e2e_dedupCollapsesConsecutiveDuplicates() {
        // Three OPEN heartbeats followed by a CLOSED produce one Opened + one Closed.
        val events = listOf(
            event(DoorPosition.OPEN, now.epochSecond - 1800),
            event(DoorPosition.OPEN, now.epochSecond - 1500),
            event(DoorPosition.OPEN, now.epochSecond - 1200),
            event(DoorPosition.CLOSED, now.epochSecond - 600),
        )
        val days = HistoryMapper.toHistoryDays(events, now, zone)
        // Two entries: closed (current) + opened
        assertEquals(2, days[0].entries.size)
        val opened = days[0].entries[1] as HistoryEntry.Opened
        // Opened at the FIRST OPEN's timestamp, not the heartbeats — the
        // duration spans from the first OPEN to the eventual CLOSED.
        assertEquals(1200L, opened.durationSeconds) // 20 min
    }

    @Test
    fun e2e_inputOrderIrrelevant_outputDeterministic() {
        // Same events in different orders → same output (sorted internally).
        val a = event(DoorPosition.OPEN, now.epochSecond - 1800)
        val b = event(DoorPosition.CLOSED, now.epochSecond - 600)
        val days1 = HistoryMapper.toHistoryDays(listOf(a, b), now, zone)
        val days2 = HistoryMapper.toHistoryDays(listOf(b, a), now, zone)
        assertEquals(days1, days2)
    }

    @Test
    fun e2e_currentlyClosedAfterMisaligned_misalignedAppearsAfterClosed() {
        // Yesterday: door opened (6:30 PM) and misaligned (10:30 PM).
        // Today: door closed (11:30 AM) — current.
        // Post-merge expectation: misalignment merges INTO the previous Opened
        // row, so yesterday has a single Opened row with `misaligned = true`,
        // not a separate anomaly row.
        val yOpen = Instant.parse("2026-04-28T18:30:00Z").epochSecond
        val yMisaligned = Instant.parse("2026-04-28T22:30:00Z").epochSecond
        val tClosed = Instant.parse("2026-04-29T16:00:00Z").epochSecond
        val events = listOf(
            event(DoorPosition.OPEN, yOpen),
            event(DoorPosition.OPEN_MISALIGNED, yMisaligned),
            event(DoorPosition.CLOSED, tClosed),
        )
        val days = HistoryMapper.toHistoryDays(events, now, zone)
        assertEquals(2, days.size)
        val today = days[0]
        val yesterday = days[1]
        val closed = today.entries.single() as HistoryEntry.Closed
        assertTrue(closed.isCurrent)
        val opened = yesterday.entries.single() as HistoryEntry.Opened
        assertTrue(opened.misaligned)
    }
}
