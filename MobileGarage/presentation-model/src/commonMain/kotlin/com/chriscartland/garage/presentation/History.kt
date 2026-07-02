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

/*
 * Typed display state for the door-history screen (ADR-031 shared presentation
 * model). Moved out of `androidApp/.../ui/history/` so both Jetpack Compose and
 * SwiftUI render the same merged/grouped history from one source of truth.
 *
 * Everything here is typed, never a user-visible string: times are raw epoch
 * seconds, durations are raw second counts, anomaly kinds and transit warnings
 * are sealed types. Each UI assembles localized strings at render time (Compose
 * stringResource + pluralStringResource; SwiftUI formatters). The merge / dedup
 * / duration / grouping logic lives in [HistoryMapper].
 */

/**
 * One entry in the history list.
 *
 * Open and Closed are separate rows (no merging across the open/close boundary).
 * Each row carries the duration of *that* state (until the next opposite-state
 * event), not the duration of the prior state.
 */
sealed interface HistoryEntry {
    /**
     * The door was opened.
     *
     * @param timeSeconds epoch seconds of the open event (each UI formats).
     * @param durationSeconds how long the door stayed open after this event
     *   (until the next CLOSE), or "and counting" seconds when this is the
     *   most-recent terminal and the door is still open.
     * @param isCurrent true when this is the most recent event and the door is
     *   still open. Drives "Open · Since X" wording instead of past-tense
     *   "Opened at X".
     * @param transitWarning non-null ([TransitWarning.ToOpen]) when an
     *   `OPENING_TOO_LONG` was merged into this row.
     * @param misaligned true when the open state was/is reported misaligned.
     */
    data class Opened(
        val timeSeconds: Long,
        val durationSeconds: Long,
        val isCurrent: Boolean = false,
        val transitWarning: TransitWarning? = null,
        val misaligned: Boolean = false,
    ) : HistoryEntry

    /** The door was closed. Mirrors [Opened]. */
    data class Closed(
        val timeSeconds: Long,
        val durationSeconds: Long,
        val isCurrent: Boolean = false,
        val transitWarning: TransitWarning? = null,
    ) : HistoryEntry

    /**
     * Errors and unresolved transitions: sensor conflict, unknown,
     * misalignment, stuck opening/closing where the door never reached its
     * terminal state.
     *
     * @param doorPosition drives the leading door visual (e.g. an
     *   `OPEN_MISALIGNED` anomaly shows the open door art).
     * @param kind typed anomaly kind; each UI resolves to a localized title.
     *   See [AnomalyKind].
     * @param timeSeconds epoch seconds of the anomaly event.
     */
    data class Anomaly(
        val doorPosition: DoorPosition,
        val kind: AnomalyKind,
        val timeSeconds: Long,
    ) : HistoryEntry
}

/** A single day's worth of entries, newest-first. */
data class HistoryDay(
    val label: DayLabel,
    val entries: List<HistoryEntry>,
)

/**
 * Typed kind of door-history anomaly. Each UI resolves to a localized title at
 * render time.
 */
sealed interface AnomalyKind {
    /** [DoorPosition.ERROR_SENSOR_CONFLICT]. */
    data object SensorConflict : AnomalyKind

    /** [DoorPosition.UNKNOWN]. */
    data object UnknownState : AnomalyKind

    /** `OPENING_TOO_LONG` that never reached its terminal `OPEN`. */
    data object StuckOpening : AnomalyKind

    /** `CLOSING_TOO_LONG` that never reached its terminal `CLOSED`. */
    data object StuckClosing : AnomalyKind

    /**
     * `OPEN_MISALIGNED` arriving without a prior `Opening` and with no recent
     * `Opened` to attach to — surfaced as a standalone anomaly so the data
     * isn't dropped. Distinct from the `misaligned` flag on a normal `Opened`
     * row.
     */
    data object OpenMisaligned : AnomalyKind
}

/**
 * Typed "transit took longer than expected" tag for a [HistoryEntry]. The mapper
 * carries the raw seconds; each UI formats the duration and assembles the
 * localized "Took X to open/close, longer than expected" string at render time.
 */
sealed interface TransitWarning {
    val transitSeconds: Long

    /** `OPENING_TOO_LONG` carried into a successful `Opened` row. */
    data class ToOpen(
        override val transitSeconds: Long,
    ) : TransitWarning

    /** `CLOSING_TOO_LONG` carried into a successful `Closed` row. */
    data class ToClose(
        override val transitSeconds: Long,
    ) : TransitWarning
}

/**
 * Typed day-section label for a [HistoryDay]. [Today] / [Yesterday] map to each
 * UI's localized resource; [Date] carries the calendar date as primitive parts
 * (so the typed surface stays free of any date library) and each UI formats it
 * with its own locale-aware formatter (e.g. "Mon, Apr 27").
 */
sealed interface DayLabel {
    /** Today, in the user's local time zone. */
    data object Today : DayLabel

    /** One day before [Today]. */
    data object Yesterday : DayLabel

    /**
     * Two or more days ago. Carries the calendar date as primitive parts:
     * [year] (e.g. 2026), [monthNumber] (1-12), [dayOfMonth] (1-31).
     */
    data class Date(
        val year: Int,
        val monthNumber: Int,
        val dayOfMonth: Int,
    ) : DayLabel
}
