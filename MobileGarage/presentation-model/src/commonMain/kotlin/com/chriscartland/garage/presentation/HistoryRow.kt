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

/**
 * Which headline a history row shows.
 *
 * Six variants across three entry kinds. The arms carry the instant rather than
 * a formatted time, because clock formatting is locale work each platform
 * already does its own way.
 */
sealed interface HistoryHeadline {
    /** The door is open right now. */
    data object OpenNow : HistoryHeadline

    /** Open right now, and reported misaligned. The one headline that mentions
     *  the anomaly itself — which is why the tag is suppressed for it. */
    data object OpenNowMisaligned : HistoryHeadline

    /** A past open, named by when it happened. */
    data class OpenedAt(
        val timeSeconds: Long,
    ) : HistoryHeadline

    /** The door is closed right now. */
    data object ClosedNow : HistoryHeadline

    /** A past close, named by when it happened. */
    data class ClosedAt(
        val timeSeconds: Long,
    ) : HistoryHeadline

    /** An anomaly row, named by its kind. */
    data class Anomaly(
        val kind: AnomalyKind,
    ) : HistoryHeadline
}

/** The row's second line. */
sealed interface HistorySupporting {
    /**
     * An ongoing span: "Since 9:47 AM · 2 hr 14 min and counting".
     *
     * Carries both the start instant and the span, because the line names both.
     */
    data class SinceWithSpan(
        val timeSeconds: Long,
        val span: StateSpanDisplay,
    ) : HistorySupporting

    /** A completed span: "Open for 6 min". */
    data class Span(
        val span: StateSpanDisplay,
    ) : HistorySupporting

    /** Just a clock time. Anomaly rows have no span to report. */
    data class ClockTime(
        val timeSeconds: Long,
    ) : HistorySupporting
}

/** A caution tag rendered under the row's supporting line. */
sealed interface HistoryTag {
    /** The transit took longer than expected. */
    data class Transit(
        val warning: TransitWarning,
    ) : HistoryTag

    /** The open state was reported misaligned. */
    data object Misaligned : HistoryTag
}

/**
 * One history row, as decisions rather than text.
 *
 * @param doorPosition drives the leading door art.
 * @param headline which of the six headlines applies.
 * @param supporting the second line.
 * @param tags caution tags, in display order. May be empty.
 */
data class HistoryRowDisplay(
    val doorPosition: DoorPosition,
    val headline: HistoryHeadline,
    val supporting: HistorySupporting,
    val tags: List<HistoryTag>,
)

/**
 * Resolves a [HistoryEntry] into the four decisions a row makes.
 *
 * All four were being made twice — once in Android's `HistoryEntryRow` `when`,
 * once in iOS's `HistoryViewModelWrapper.resolve`:
 *
 *  1. **Door art.** A misaligned open renders the `OPEN_MISALIGNED` art, so the
 *     misalignment is visible on a row that is otherwise just "Opened".
 *  2. **Headline variant.** Three ways for an open, two for a close, one for an
 *     anomaly.
 *  3. **Supporting variant.** An ongoing span names its start; a completed one
 *     does not.
 *  4. **Tag composition, including the suppression rule** — see below.
 *
 * The suppression rule is the subtle one and the reason this is worth sharing:
 * a misaligned tag is emitted only when the headline does *not* already say
 * "(misaligned)". Written out on each platform, it reads as an incidental
 * `&& !isCurrent` that looks like it could be simplified away. Stated once, with
 * a test, it is a rule: **never say the same thing twice in one row.**
 */
object HistoryRowMapper {
    fun forEntry(entry: HistoryEntry): HistoryRowDisplay =
        when (entry) {
            is HistoryEntry.Opened -> openedRow(entry)
            is HistoryEntry.Closed -> closedRow(entry)
            is HistoryEntry.Anomaly -> HistoryRowDisplay(
                doorPosition = entry.doorPosition,
                headline = HistoryHeadline.Anomaly(entry.kind),
                supporting = HistorySupporting.ClockTime(entry.timeSeconds),
                tags = emptyList(),
            )
        }

    private fun openedRow(entry: HistoryEntry.Opened): HistoryRowDisplay {
        val headline = when {
            entry.isCurrent && entry.misaligned -> HistoryHeadline.OpenNowMisaligned
            entry.isCurrent -> HistoryHeadline.OpenNow
            else -> HistoryHeadline.OpenedAt(entry.timeSeconds)
        }
        return HistoryRowDisplay(
            // Show the misaligned art even on a plain "Opened" row, so the
            // anomaly is visible without reading the tags.
            doorPosition = if (entry.misaligned) DoorPosition.OPEN_MISALIGNED else DoorPosition.OPEN,
            headline = headline,
            supporting = supportingFor(
                entry.timeSeconds,
                entry.durationSeconds,
                entry.isCurrent,
                isOpen = true,
            ),
            tags = buildList {
                entry.transitWarning?.let { add(HistoryTag.Transit(it)) }
                // Suppressed when the headline already says "(misaligned)".
                // Two statements of the same fact in one row is noise, and the
                // headline is the more prominent of the two.
                if (entry.misaligned && headline != HistoryHeadline.OpenNowMisaligned) {
                    add(HistoryTag.Misaligned)
                }
            },
        )
    }

    private fun closedRow(entry: HistoryEntry.Closed): HistoryRowDisplay =
        HistoryRowDisplay(
            doorPosition = DoorPosition.CLOSED,
            headline = if (entry.isCurrent) {
                HistoryHeadline.ClosedNow
            } else {
                HistoryHeadline.ClosedAt(entry.timeSeconds)
            },
            supporting = supportingFor(
                entry.timeSeconds,
                entry.durationSeconds,
                entry.isCurrent,
                isOpen = false,
            ),
            tags = buildList {
                entry.transitWarning?.let { add(HistoryTag.Transit(it)) }
            },
        )

    private fun supportingFor(
        timeSeconds: Long,
        durationSeconds: Long,
        isCurrent: Boolean,
        isOpen: Boolean,
    ): HistorySupporting {
        val span = HistoryDurationMapper.stateSpan(
            seconds = durationSeconds,
            isCurrent = isCurrent,
            isOpen = isOpen,
        )
        // An ongoing span names when it started; a finished one is identified by
        // its headline's timestamp already, so repeating it would be redundant.
        return if (isCurrent) {
            HistorySupporting.SinceWithSpan(timeSeconds, span)
        } else {
            HistorySupporting.Span(span)
        }
    }
}
