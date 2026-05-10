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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure-function pipeline that converts raw [DoorEvent]s into the display-ready
 * [HistoryDay] list rendered by [HistoryContent].
 *
 * The composable takes its arguments verbatim — every string in [HistoryDay] /
 * [HistoryEntry] is computed here. Tests in `HistoryMapperTest` cover the
 * merge rules, dedup, duration computation, and formatting in isolation.
 *
 * Pipeline (in order):
 *
 *   1. Sort by `lastChangeTimeSeconds` (oldest → newest) and drop events with
 *      missing position or timestamp.
 *   2. Dedup consecutive same-position events.
 *   3. Merge transitions: `OPENING → OPEN` and `CLOSING → CLOSED` collapse
 *      into a single `Opened` / `Closed` record. `_TOO_LONG → terminal`
 *      collapses into a terminal with a transit-warning note. `_TOO_LONG`
 *      that never reaches its terminal becomes a `Stuck opening` /
 *      `Stuck closing` anomaly.
 *   4. Compute durations: each terminal carries its own state's duration —
 *      "Open for 6 min" reaches forward to the next CLOSED, "Closed for
 *      22 min" reaches forward to the next OPENED. The most recent terminal
 *      gets `isCurrent = true` and "X and counting".
 *   5. Format display strings using the supplied [ZoneId] (locale-fixed to
 *      `Locale.US` for predictable test output).
 *   6. Group entries by local date, newest day first, with friendly day
 *      labels ("Today" / "Yesterday" / "Mon, Apr 27").
 */
object HistoryMapper {
    /**
     * Convert a list of door events into display-ready [HistoryDay]s.
     *
     * @param events raw events; order is irrelevant (sorted internally).
     * @param now reference instant for "and counting" durations and
     *   "Today" / "Yesterday" day labels.
     * @param zone time zone for time-of-day formatting and day grouping.
     */
    fun toHistoryDays(
        events: List<DoorEvent>,
        now: Instant,
        zone: ZoneId,
    ): List<HistoryDay> {
        val chronological = events
            .filter { it.lastChangeTimeSeconds != null && it.doorPosition != null }
            .sortedBy { it.lastChangeTimeSeconds!! }
            .let(::dedupConsecutive)
        if (chronological.isEmpty()) return emptyList()

        val merged = mergeEvents(chronological)
        val withDurations = computeDurations(merged, now)
        // Display order is newest-first; group by local date while we still
        // have the raw epoch seconds.
        return groupByDay(withDurations.asReversed(), now, zone)
    }

    // ---------- Step 2: dedup ----------

    internal fun dedupConsecutive(events: List<DoorEvent>): List<DoorEvent> =
        events.fold(mutableListOf<DoorEvent>()) { acc, e ->
            if (acc.isEmpty() || acc.last().doorPosition != e.doorPosition) {
                acc.add(e)
            }
            acc
        }

    // ---------- Step 3: merge transitions ----------

    internal sealed interface MergedRecord {
        val timeSeconds: Long

        data class Opened(
            override val timeSeconds: Long,
            val transitDurationSeconds: Long?,
            val misaligned: Boolean = false,
        ) : MergedRecord

        data class Closed(
            override val timeSeconds: Long,
            val transitDurationSeconds: Long?,
        ) : MergedRecord

        data class Anomaly(
            override val timeSeconds: Long,
            val position: DoorPosition,
            val title: String,
        ) : MergedRecord
    }

    private sealed interface PendingTransition {
        val startTimeSeconds: Long
        val tooLong: Boolean

        data class Opening(
            override val startTimeSeconds: Long,
            override val tooLong: Boolean,
        ) : PendingTransition

        data class Closing(
            override val startTimeSeconds: Long,
            override val tooLong: Boolean,
        ) : PendingTransition
    }

    /**
     * Walk events oldest → newest, applying merge rules. See class doc.
     */
    internal fun mergeEvents(chronological: List<DoorEvent>): List<MergedRecord> {
        val state = MergeState()
        chronological
            .filter { it.doorPosition != null && it.lastChangeTimeSeconds != null }
            .forEach { state.processEvent(it.doorPosition!!, it.lastChangeTimeSeconds!!) }
        state.flushPending()
        return state.result
    }

    /**
     * Mutable state holder that the per-position handlers operate on. Pulling
     * the loop body out of [mergeEvents] keeps each handler shallow and lets
     * detekt see them as standalone functions instead of branches deep inside
     * a `for` over a giant `when`.
     */
    private class MergeState {
        val result = mutableListOf<MergedRecord>()
        var pending: PendingTransition? = null

        fun processEvent(
            pos: DoorPosition,
            time: Long,
        ) {
            when (pos) {
                DoorPosition.OPENING -> handleOpening(time)
                DoorPosition.OPENING_TOO_LONG -> handleOpeningTooLong(time)
                DoorPosition.OPEN -> handleOpen(time)
                DoorPosition.CLOSING -> handleClosing(time)
                DoorPosition.CLOSING_TOO_LONG -> handleClosingTooLong(time)
                DoorPosition.CLOSED -> handleClosed(time)
                DoorPosition.OPEN_MISALIGNED -> handleOpenMisaligned(time)
                DoorPosition.ERROR_SENSOR_CONFLICT ->
                    result += MergedRecord.Anomaly(time, pos, "Sensor conflict")
                DoorPosition.UNKNOWN ->
                    result += MergedRecord.Anomaly(time, pos, "Unknown state")
            }
        }

        private fun handleOpening(time: Long) {
            pending = handleDirectionChange(pending, result, newDirectionIsOpening = true)
                ?: PendingTransition.Opening(time, tooLong = false)
        }

        private fun handleOpeningTooLong(time: Long) {
            val carried = handleDirectionChange(pending, result, newDirectionIsOpening = true)
            pending = when (carried) {
                is PendingTransition.Opening -> carried.copy(tooLong = true)
                is PendingTransition.Closing -> PendingTransition.Opening(time, tooLong = true)
                null -> PendingTransition.Opening(time, tooLong = true)
            }
        }

        private fun handleOpen(time: Long) {
            val carried = handleDirectionChange(pending, result, newDirectionIsOpening = true)
            val transit = (carried as? PendingTransition.Opening)
                ?.takeIf { it.tooLong }
                ?.let { time - it.startTimeSeconds }
            result += MergedRecord.Opened(time, transit)
            pending = null
        }

        private fun handleClosing(time: Long) {
            pending = handleDirectionChange(pending, result, newDirectionIsOpening = false)
                ?: PendingTransition.Closing(time, tooLong = false)
        }

        private fun handleClosingTooLong(time: Long) {
            val carried = handleDirectionChange(pending, result, newDirectionIsOpening = false)
            pending = when (carried) {
                is PendingTransition.Closing -> carried.copy(tooLong = true)
                is PendingTransition.Opening -> PendingTransition.Closing(time, tooLong = true)
                null -> PendingTransition.Closing(time, tooLong = true)
            }
        }

        private fun handleClosed(time: Long) {
            val carried = handleDirectionChange(pending, result, newDirectionIsOpening = false)
            val transit = (carried as? PendingTransition.Closing)
                ?.takeIf { it.tooLong }
                ?.let { time - it.startTimeSeconds }
            result += MergedRecord.Closed(time, transit)
            pending = null
        }

        /**
         * Misalignment is a property of an open state, not a separate event.
         * Three cases in priority order:
         *   1. An Opening is pending → terminate as Opened(misaligned = true)
         *      with the transit warning if the opening was tooLong.
         *   2. The most recent open-state context in `result` is an Opened
         *      (no Closed between) → set its misaligned flag. Don't emit.
         *   3. Otherwise → standalone Anomaly so data isn't dropped.
         */
        private fun handleOpenMisaligned(time: Long) {
            val pendingOpening = pending as? PendingTransition.Opening
            if (pendingOpening != null) {
                val transit = pendingOpening
                    .takeIf { it.tooLong }
                    ?.let { time - it.startTimeSeconds }
                result += MergedRecord.Opened(time, transit, misaligned = true)
                pending = null
                return
            }
            val mergeIndex = findOpenedToMergeMisalignment(result)
            if (mergeIndex != null) {
                val existing = result[mergeIndex] as MergedRecord.Opened
                result[mergeIndex] = existing.copy(misaligned = true)
            } else {
                result += MergedRecord.Anomaly(time, DoorPosition.OPEN_MISALIGNED, "Open (misaligned)")
            }
        }

        /** End-of-stream: flush any unresolved tooLong pending as an anomaly. */
        fun flushPending() {
            when (val p = pending) {
                is PendingTransition.Opening ->
                    if (p.tooLong) {
                        result += MergedRecord.Anomaly(p.startTimeSeconds, DoorPosition.OPENING_TOO_LONG, "Stuck opening")
                    }
                is PendingTransition.Closing ->
                    if (p.tooLong) {
                        result += MergedRecord.Anomaly(p.startTimeSeconds, DoorPosition.CLOSING_TOO_LONG, "Stuck closing")
                    }
                null -> Unit
            }
            pending = null
        }
    }

    /**
     * Find the index of the most recent [MergedRecord.Opened] in [result] that
     * still represents the current open-state context — i.e. there is no
     * intervening [MergedRecord.Closed]. Anomalies are skipped (they don't
     * break the open-state context). Returns null when the most recent
     * terminal is a Closed or there is no Opened at all.
     */
    private fun findOpenedToMergeMisalignment(result: List<MergedRecord>): Int? {
        for (i in result.indices.reversed()) {
            when (result[i]) {
                is MergedRecord.Opened -> return i
                is MergedRecord.Closed -> return null
                is MergedRecord.Anomaly -> continue
            }
        }
        return null
    }

    /**
     * Reconcile the existing pending transition with an incoming transition.
     *
     * Returns the pending to carry forward (same direction → preserved; opposite
     * direction → null after flushing as anomaly if tooLong).
     */
    private fun handleDirectionChange(
        pending: PendingTransition?,
        result: MutableList<MergedRecord>,
        newDirectionIsOpening: Boolean,
    ): PendingTransition? =
        when (pending) {
            is PendingTransition.Opening -> if (newDirectionIsOpening) {
                pending
            } else {
                if (pending.tooLong) {
                    result += MergedRecord.Anomaly(
                        timeSeconds = pending.startTimeSeconds,
                        position = DoorPosition.OPENING_TOO_LONG,
                        title = "Stuck opening",
                    )
                }
                null
            }
            is PendingTransition.Closing -> if (!newDirectionIsOpening) {
                pending
            } else {
                if (pending.tooLong) {
                    result += MergedRecord.Anomaly(
                        timeSeconds = pending.startTimeSeconds,
                        position = DoorPosition.CLOSING_TOO_LONG,
                        title = "Stuck closing",
                    )
                }
                null
            }
            null -> null
        }

    // ---------- Step 4: durations + isCurrent ----------

    internal data class WithDuration(
        val record: MergedRecord,
        val durationSeconds: Long?,
        val isCurrent: Boolean,
    )

    /**
     * For each terminal record, compute "how long this state lasted." Walks
     * in reverse so we can reach the next opposite-state event in O(n).
     *
     * An `Opened`'s span ends at the earlier of the next `Closed` or next
     * `Opened` — adjacent same-state records (e.g. when an `OPEN_MISALIGNED`
     * splits an open run into two `Opened` rows) carve out non-overlapping
     * spans rather than each spanning to the same future `Closed`. Symmetric
     * for `Closed`.
     */
    internal fun computeDurations(
        merged: List<MergedRecord>,
        now: Instant,
    ): List<WithDuration> {
        var nextClosedTime: Long? = null
        var nextOpenedTime: Long? = null
        val reversed = mutableListOf<WithDuration>()

        for (record in merged.asReversed()) {
            when (record) {
                is MergedRecord.Opened -> {
                    val end = listOfNotNull(nextClosedTime, nextOpenedTime).minOrNull()
                    val duration = (end ?: now.epochSecond) - record.timeSeconds
                    reversed.add(
                        WithDuration(
                            record = record,
                            durationSeconds = duration,
                            isCurrent = end == null,
                        ),
                    )
                    nextOpenedTime = record.timeSeconds
                }
                is MergedRecord.Closed -> {
                    val end = listOfNotNull(nextOpenedTime, nextClosedTime).minOrNull()
                    val duration = (end ?: now.epochSecond) - record.timeSeconds
                    reversed.add(
                        WithDuration(
                            record = record,
                            durationSeconds = duration,
                            isCurrent = end == null,
                        ),
                    )
                    nextClosedTime = record.timeSeconds
                }
                is MergedRecord.Anomaly -> {
                    reversed.add(WithDuration(record, durationSeconds = null, isCurrent = false))
                }
            }
        }

        return reversed.reversed()
    }

    private fun WithDuration.toHistoryEntry(zone: ZoneId): HistoryEntry =
        when (val r = record) {
            is MergedRecord.Opened -> HistoryEntry.Opened(
                timeDisplay = formatTime(r.timeSeconds, zone),
                durationDisplay = formatStateDurationLabel(
                    durationSeconds = durationSeconds ?: 0L,
                    isCurrent = isCurrent,
                    isOpenState = true,
                ),
                isCurrent = isCurrent,
                transitWarning = r.transitDurationSeconds?.let {
                    "Took ${formatTransitDuration(it)} to open, longer than expected"
                },
                misaligned = r.misaligned,
            )
            is MergedRecord.Closed -> HistoryEntry.Closed(
                timeDisplay = formatTime(r.timeSeconds, zone),
                durationDisplay = formatStateDurationLabel(
                    durationSeconds = durationSeconds ?: 0L,
                    isCurrent = isCurrent,
                    isOpenState = false,
                ),
                isCurrent = isCurrent,
                transitWarning = r.transitDurationSeconds?.let {
                    "Took ${formatTransitDuration(it)} to close, longer than expected"
                },
            )
            is MergedRecord.Anomaly -> HistoryEntry.Anomaly(
                doorPosition = r.position,
                title = r.title,
                timeDisplay = formatTime(r.timeSeconds, zone),
            )
        }

    // ---------- Step 5: formatting ----------

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)

    internal fun formatTime(
        timeSeconds: Long,
        zone: ZoneId,
    ): String = Instant.ofEpochSecond(timeSeconds).atZone(zone).format(timeFormatter)

    internal fun formatStateDurationLabel(
        durationSeconds: Long,
        isCurrent: Boolean,
        isOpenState: Boolean,
    ): String {
        val duration = formatStateDuration(durationSeconds)
        return when {
            isCurrent -> "$duration and counting"
            isOpenState -> "Open for $duration"
            else -> "Closed for $duration"
        }
    }

    /**
     * Format an open/closed-state duration. Bias toward casual readability —
     * drop seconds at minute scale, drop minutes at day scale.
     */
    internal fun formatStateDuration(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0L)
        if (safe < 60) return "$safe sec"
        val totalMin = safe / 60
        if (totalMin < 60) return "$totalMin min"
        val hours = totalMin / 60
        val mins = totalMin % 60
        if (hours < 24) {
            return if (mins == 0L) "$hours hr" else "$hours hr $mins min"
        }
        val days = hours / 24
        val remHours = hours % 24
        return if (remHours == 0L) "$days day" else "$days day $remHours hr"
    }

    /**
     * Format a transit (OPENING / CLOSING) duration. Transits are typically
     * seconds, occasionally minutes for `_TOO_LONG` cases. We keep the
     * seconds suffix at minute scale (transits are short, the precision
     * helps), but drop seconds at hour scale to stay readable on the rare
     * inputs where the data is malformed.
     */
    internal fun formatTransitDuration(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0L)
        if (safe < 60) return "$safe sec"
        val totalMin = safe / 60
        val secs = safe % 60
        if (totalMin < 60) {
            return if (secs == 0L) "$totalMin min" else "$totalMin min $secs sec"
        }
        val hours = totalMin / 60
        val mins = totalMin % 60
        return if (mins == 0L) "$hours hr" else "$hours hr $mins min"
    }

    // ---------- Step 6: group by day ----------

    internal fun formatDayLabel(
        date: LocalDate,
        today: LocalDate,
    ): String =
        when (today.toEpochDay() - date.toEpochDay()) {
            0L -> "Today"
            1L -> "Yesterday"
            else -> date.format(dateFormatter)
        }

    /**
     * Group [WithDuration] records (newest-first) by local date and format
     * each into a [HistoryEntry]. We do this in one pass so the raw epoch
     * second from [MergedRecord] is available for day bucketing without
     * round-tripping through display strings.
     */
    private fun groupByDay(
        records: List<WithDuration>,
        now: Instant,
        zone: ZoneId,
    ): List<HistoryDay> {
        if (records.isEmpty()) return emptyList()
        val today = now.atZone(zone).toLocalDate()
        val groups = LinkedHashMap<LocalDate, MutableList<HistoryEntry>>()
        for (rec in records) {
            val date = Instant.ofEpochSecond(rec.record.timeSeconds).atZone(zone).toLocalDate()
            groups.getOrPut(date) { mutableListOf() }.add(rec.toHistoryEntry(zone))
        }
        return groups.map { (date, list) ->
            HistoryDay(label = formatDayLabel(date, today), entries = list)
        }
    }
}
