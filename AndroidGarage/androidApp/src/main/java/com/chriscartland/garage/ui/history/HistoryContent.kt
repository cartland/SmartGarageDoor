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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chriscartland.garage.R
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.ui.GarageIcon
import com.chriscartland.garage.ui.theme.DividerInset
import com.chriscartland.garage.ui.theme.DoorColorState
import com.chriscartland.garage.ui.theme.LocalDoorStatusColorScheme
import com.chriscartland.garage.ui.theme.ParagraphSpacing
import com.chriscartland.garage.ui.theme.Spacing
import com.chriscartland.garage.ui.theme.doorColorSet
import com.chriscartland.garage.ui.theme.doorColorState
import com.chriscartland.garage.ui.theme.safeListContentPadding
import java.time.ZoneId

/**
 * One entry in the history list.
 *
 * Open and Closed are separate rows (no merging across the open/close
 * boundary). Each row carries the duration of *that* state (until the next
 * opposite-state event), not the duration of the prior state.
 *
 * Phase 2E of the string-resource migration plan
 * (`AndroidGarage/docs/PENDING_FOLLOWUPS.md` item #1) — no user-visible
 * strings live on this type. Times are raw epoch seconds, durations are
 * raw second counts, anomaly kinds + transit warnings are typed sealed
 * types ([AnomalyKind], [TransitWarning]). The Composable layer assembles
 * localized display strings via `stringResource` + `pluralStringResource`
 * at render time.
 */
sealed interface HistoryEntry {
    /**
     * The door was opened.
     *
     * @param timeSeconds epoch seconds of the open event (Composable formats).
     * @param durationSeconds how long the door stayed open after this event
     *   (until the next CLOSE), or "and counting" seconds when this is the
     *   most-recent terminal and the door is still open.
     * @param isCurrent true when this is the most recent event and the door
     *   is still open. Drives "Open · Since X" wording instead of past-tense
     *   "Opened at X".
     * @param transitWarning non-null ([TransitWarning.ToOpen]) when an
     *   `OPENING_TOO_LONG` was merged into this row.
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
     * @param doorPosition drives the GarageIcon leading visual (e.g. an
     *   `OPEN_MISALIGNED` anomaly shows the open door art).
     * @param kind typed anomaly kind; the Composable resolves to a
     *   localized title via `stringResource`. See [AnomalyKind].
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
 * Sectioned-list history. Day labels in primary uppercase, grouped Material 3
 * surfaces, ListItem rows with the [GarageIcon] door visual as leading art —
 * matches the redesigned Settings tab aesthetic and reuses the rest of the
 * app's door-state coloring.
 *
 * Stateless: callers pass already-grouped, already-formatted [HistoryDay]
 * instances. The pairing/grouping pipeline (DoorEvent → HistoryDay) is
 * Phase 3 production work.
 *
 * @param isRefreshing drives the M3 pull-to-refresh spinner. Production
 *   callers tie this to the `LoadingResult.Loading` flag of the underlying
 *   events flow.
 * @param onRefresh fires when the user completes a downward pull gesture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryContent(
    days: List<HistoryDay>,
    zone: ZoneId,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = safeListContentPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.BetweenItems),
        ) {
            if (days.isEmpty()) {
                // Wrap in a parent-filled Box so the empty state is
                // truly centered in the available LazyColumn height.
                // The empty state Composable itself owns no top gap
                // (parent-owns rule); the Box owns the centering.
                item {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        HistoryEmptyState()
                    }
                }
            } else {
                days.forEach { day ->
                    item(key = HistoryDayKey.forLabel(day.label)) {
                        HistoryDaySection(day = day, zone = zone)
                    }
                }
            }
        }
    }
}

/**
 * Stable keys for [LazyColumn]'s `item(key = ...)` slot. Wrapped in a
 * named object per ADR-009 (no bare top-level functions). `Today` /
 * `Yesterday` are stable singletons; `Date` uses the LocalDate which
 * is unique per day and stable across recompositions.
 */
private object HistoryDayKey {
    fun forLabel(label: DayLabel): Any =
        when (label) {
            DayLabel.Today -> "today"
            DayLabel.Yesterday -> "yesterday"
            is DayLabel.Date -> label.date.toString()
        }
}

@Composable
private fun HistoryDaySection(
    day: HistoryDay,
    zone: ZoneId,
) {
    // Outer Column owns the gap between header text and body card via
    // spacedBy (parent-owns-gaps rule). Header text has no vertical
    // padding — the parent LazyColumn owns the gap above the section.
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.SectionHeaderBottom)) {
        Text(
            text = dayLabelText(day.label).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = Spacing.SectionHeaderStart),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large),
        ) {
            Column {
                day.entries.forEachIndexed { index, entry ->
                    if (index > 0) {
                        HorizontalDivider(modifier = Modifier.padding(start = DividerInset.LargeLeading))
                    }
                    HistoryEntryRow(entry = entry, zone = zone)
                }
            }
        }
    }
}

@Composable
private fun HistoryEntryRow(
    entry: HistoryEntry,
    zone: ZoneId,
) {
    when (entry) {
        is HistoryEntry.Opened -> {
            val timeDisplay = remember(entry.timeSeconds, zone) {
                HistoryFormatter.formatTime(entry.timeSeconds, zone)
            }
            val durationDisplay = stateDurationDisplay(
                durationSeconds = entry.durationSeconds,
                isCurrent = entry.isCurrent,
                isOpenState = true,
            )
            HistoryStateRow(
                // When misaligned, render the OPEN_MISALIGNED door art so the
                // misalignment is visible even on a row that's just an "Opened"
                // event with the misalignment property set.
                doorPosition = if (entry.misaligned) DoorPosition.OPEN_MISALIGNED else DoorPosition.OPEN,
                headline = when {
                    entry.isCurrent && entry.misaligned ->
                        stringResource(R.string.history_headline_open_misaligned)
                    entry.isCurrent ->
                        stringResource(R.string.history_headline_open)
                    else ->
                        stringResource(R.string.history_headline_opened_at, timeDisplay)
                },
                supporting = if (entry.isCurrent) {
                    stringResource(R.string.history_supporting_since_format, timeDisplay, durationDisplay)
                } else {
                    durationDisplay
                },
                warnings = listOfNotNull(
                    entry.transitWarning?.let { transitWarningText(it) },
                    // For past Opened rows, surface misalignment as a tag below
                    // the duration. When isCurrent, the headline already says
                    // "Open (misaligned)" — no need for a duplicate tag.
                    if (entry.misaligned && !entry.isCurrent) {
                        stringResource(R.string.history_warning_misaligned)
                    } else {
                        null
                    },
                ),
            )
        }
        is HistoryEntry.Closed -> {
            val timeDisplay = remember(entry.timeSeconds, zone) {
                HistoryFormatter.formatTime(entry.timeSeconds, zone)
            }
            val durationDisplay = stateDurationDisplay(
                durationSeconds = entry.durationSeconds,
                isCurrent = entry.isCurrent,
                isOpenState = false,
            )
            HistoryStateRow(
                doorPosition = DoorPosition.CLOSED,
                headline = if (entry.isCurrent) {
                    stringResource(R.string.history_headline_closed)
                } else {
                    stringResource(R.string.history_headline_closed_at, timeDisplay)
                },
                supporting = if (entry.isCurrent) {
                    stringResource(R.string.history_supporting_since_format, timeDisplay, durationDisplay)
                } else {
                    durationDisplay
                },
                warnings = listOfNotNull(entry.transitWarning?.let { transitWarningText(it) }),
            )
        }
        is HistoryEntry.Anomaly -> {
            val timeDisplay = remember(entry.timeSeconds, zone) {
                HistoryFormatter.formatTime(entry.timeSeconds, zone)
            }
            HistoryStateRow(
                doorPosition = entry.doorPosition,
                headline = anomalyTitle(entry.kind),
                supporting = timeDisplay,
                warnings = emptyList(),
            )
        }
    }
}

/**
 * Resolve a [DayLabel] to a localized day-section header string.
 * `Today` / `Yesterday` map to resources; `Date` formats via
 * [HistoryFormatter.formatDate] (locale-aware DateTimeFormatter).
 */
@Composable
private fun dayLabelText(label: DayLabel): String =
    when (label) {
        DayLabel.Today -> stringResource(R.string.history_day_today)
        DayLabel.Yesterday -> stringResource(R.string.history_day_yesterday)
        is DayLabel.Date -> HistoryFormatter.formatDate(label.date)
    }

/** Resolve an [AnomalyKind] to its localized headline string. */
@Composable
private fun anomalyTitle(kind: AnomalyKind): String =
    when (kind) {
        AnomalyKind.SensorConflict -> stringResource(R.string.history_anomaly_sensor_conflict)
        AnomalyKind.UnknownState -> stringResource(R.string.history_anomaly_unknown_state)
        AnomalyKind.StuckOpening -> stringResource(R.string.history_anomaly_stuck_opening)
        AnomalyKind.StuckClosing -> stringResource(R.string.history_anomaly_stuck_closing)
        AnomalyKind.OpenMisaligned -> stringResource(R.string.history_anomaly_open_misaligned)
    }

/**
 * Assemble the localized "Open for X" / "Closed for X" / "X and counting"
 * string for a state-span duration, picking plural / single-unit / multi-unit
 * granularity based on [HistoryFormatter.stateDurationParts].
 */
@Composable
private fun stateDurationDisplay(
    durationSeconds: Long,
    isCurrent: Boolean,
    isOpenState: Boolean,
): String {
    val parts = remember(durationSeconds) { HistoryFormatter.stateDurationParts(durationSeconds) }
    val durationText = when {
        parts.days >= 1 -> {
            if (parts.hours == 0) {
                pluralStringResource(R.plurals.home_duration_days, parts.days, parts.days)
            } else {
                stringResource(R.string.history_state_duration_days_with_hours, parts.days, parts.hours)
            }
        }
        parts.hours >= 1 -> {
            if (parts.minutes == 0) {
                stringResource(R.string.history_state_duration_hours_only, parts.hours)
            } else {
                stringResource(R.string.home_duration_hours_minutes, parts.hours, parts.minutes)
            }
        }
        parts.minutes >= 1 ->
            pluralStringResource(R.plurals.home_duration_minutes, parts.minutes, parts.minutes)
        else ->
            pluralStringResource(R.plurals.home_duration_seconds, parts.seconds, parts.seconds)
    }
    return when {
        isCurrent -> stringResource(R.string.history_state_duration_and_counting, durationText)
        isOpenState -> stringResource(R.string.history_state_duration_open_for, durationText)
        else -> stringResource(R.string.history_state_duration_closed_for, durationText)
    }
}

/**
 * Assemble the localized "Took X to open/close, longer than expected"
 * tag for a [TransitWarning].
 */
@Composable
private fun transitWarningText(warning: TransitWarning): String {
    val parts = remember(warning.transitSeconds) {
        HistoryFormatter.transitDurationParts(warning.transitSeconds)
    }
    val durationText = when {
        parts.hours >= 1 -> {
            if (parts.minutes == 0) {
                stringResource(R.string.history_state_duration_hours_only, parts.hours)
            } else {
                stringResource(R.string.home_duration_hours_minutes, parts.hours, parts.minutes)
            }
        }
        parts.minutes >= 1 -> {
            if (parts.seconds == 0) {
                pluralStringResource(R.plurals.home_duration_minutes, parts.minutes, parts.minutes)
            } else {
                stringResource(R.string.history_transit_minutes_seconds, parts.minutes, parts.seconds)
            }
        }
        else ->
            pluralStringResource(R.plurals.home_duration_seconds, parts.seconds, parts.seconds)
    }
    return when (warning) {
        is TransitWarning.ToOpen ->
            stringResource(R.string.history_warning_transit_to_open, durationText)
        is TransitWarning.ToClose ->
            stringResource(R.string.history_warning_transit_to_close, durationText)
    }
}

@Composable
private fun HistoryStateRow(
    doorPosition: DoorPosition,
    headline: String,
    supporting: String,
    warnings: List<String>,
) {
    val colorSet = LocalDoorStatusColorScheme.current.doorColorSet(isStale = false)
    val doorColor = when (DoorEvent(doorPosition = doorPosition).doorColorState()) {
        DoorColorState.OPEN -> colorSet.open
        DoorColorState.CLOSED -> colorSet.closed
        DoorColorState.UNKNOWN -> colorSet.unknown
    }
    ListItem(
        leadingContent = {
            GarageIcon(
                doorPosition = doorPosition,
                static = true,
                color = doorColor,
                modifier = Modifier.size(40.dp),
            )
        },
        headlineContent = { Text(headline) },
        supportingContent = {
            Column {
                Text(supporting)
                warnings.forEach { warning ->
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(Spacing.Tight))
                        Text(
                            text = warning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

// Caller wraps this in a `Box(fillParentMaxSize, contentAlignment =
// Center)` so the empty state is vertically centered in the LazyColumn.
// This Composable owns only its internal shape (horizontal padding for
// readable measure); no self-claimed vertical gap (parent-owns rule).
@Composable
private fun HistoryEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.history_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(ParagraphSpacing.TitleToBody))
        Text(
            text = stringResource(R.string.history_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------- Preview helpers ----------
//
// Both flagship previews build raw DoorEvents and pipe them through
// HistoryMapper, so the screenshot reflects real mapper output (and the
// design implicitly tests the merge / format / grouping logic end-to-end
// via the eye). HistoryMapperTest covers the same paths for correctness.

private object HistoryPreviewData {
    val zone: java.time.ZoneId = java.time.ZoneOffset.UTC

    fun event(
        position: DoorPosition,
        isoTime: String,
    ): DoorEvent =
        DoorEvent(
            doorPosition = position,
            lastChangeTimeSeconds = java.time.Instant
                .parse(isoTime)
                .epochSecond,
        )
}

/**
 * Flagship #1 — covers: currently-open, past Opened/Closed pairs,
 * `_TOO_LONG` warning tag on an Opened row, sensor-conflict anomaly,
 * `_TOO_LONG` no-resolution stuck-opening anomaly, multi-day grouping.
 */
@Preview
@Composable
fun HistoryContentMultiDayPreview() {
    val events = listOf(
        // Today — currently open since 10:15 (mapper computes "12 min and counting" against 10:27).
        HistoryPreviewData.event(DoorPosition.OPENING_TOO_LONG, "2026-04-29T09:43:00Z"),
        HistoryPreviewData.event(DoorPosition.OPEN, "2026-04-29T09:47:00Z"),
        HistoryPreviewData.event(DoorPosition.CLOSING, "2026-04-29T09:53:00Z"),
        HistoryPreviewData.event(DoorPosition.CLOSED, "2026-04-29T09:53:06Z"),
        HistoryPreviewData.event(DoorPosition.OPENING, "2026-04-29T10:15:00Z"),
        HistoryPreviewData.event(DoorPosition.OPEN, "2026-04-29T10:15:08Z"),
        // Yesterday — sensor conflict anomaly + open/close pairs.
        HistoryPreviewData.event(DoorPosition.OPENING, "2026-04-28T08:00:00Z"),
        HistoryPreviewData.event(DoorPosition.OPEN, "2026-04-28T08:00:08Z"),
        HistoryPreviewData.event(DoorPosition.CLOSING, "2026-04-28T08:05:00Z"),
        HistoryPreviewData.event(DoorPosition.CLOSED, "2026-04-28T08:05:06Z"),
        HistoryPreviewData.event(DoorPosition.OPENING, "2026-04-28T18:30:00Z"),
        HistoryPreviewData.event(DoorPosition.OPEN, "2026-04-28T18:30:08Z"),
        HistoryPreviewData.event(DoorPosition.CLOSING, "2026-04-28T20:30:00Z"),
        HistoryPreviewData.event(DoorPosition.CLOSED, "2026-04-28T20:30:06Z"),
        HistoryPreviewData.event(DoorPosition.ERROR_SENSOR_CONFLICT, "2026-04-28T23:42:00Z"),
        // Mon Apr 27 — stuck-opening that the user gave up on (closed manually).
        // The CLOSING + CLOSED that follow are what flush the OPENING_TOO_LONG
        // as a "Stuck opening" anomaly via opposite-direction interruption.
        HistoryPreviewData.event(DoorPosition.OPENING, "2026-04-27T07:15:00Z"),
        HistoryPreviewData.event(DoorPosition.OPEN, "2026-04-27T07:15:08Z"),
        HistoryPreviewData.event(DoorPosition.CLOSING, "2026-04-27T07:18:00Z"),
        HistoryPreviewData.event(DoorPosition.CLOSED, "2026-04-27T07:18:06Z"),
        HistoryPreviewData.event(DoorPosition.OPENING_TOO_LONG, "2026-04-27T17:30:00Z"),
        HistoryPreviewData.event(DoorPosition.CLOSING, "2026-04-27T17:35:00Z"),
        HistoryPreviewData.event(DoorPosition.CLOSED, "2026-04-27T17:35:06Z"),
    )
    val days = HistoryMapper.toHistoryDays(
        events = events,
        now = java.time.Instant.parse("2026-04-29T10:27:00Z"),
        zone = HistoryPreviewData.zone,
    )
    HistoryContent(days = days, zone = HistoryPreviewData.zone)
}

/**
 * Flagship #2 — covers the unique UI not in flagship #1: currently-closed,
 * misaligned anomaly, unknown-state anomaly, stuck-closing anomaly,
 * `_TOO_LONG` warning tag on a Closed row.
 */
@Preview
@Composable
fun HistoryContentMultiDayClosedPreview() {
    val events = listOf(
        // Today — currently closed at 11:30 (mapper computes "47 min and counting" against 12:17).
        HistoryPreviewData.event(DoorPosition.OPENING, "2026-04-29T11:20:00Z"),
        HistoryPreviewData.event(DoorPosition.OPEN, "2026-04-29T11:20:08Z"),
        HistoryPreviewData.event(DoorPosition.CLOSING_TOO_LONG, "2026-04-29T11:27:00Z"),
        HistoryPreviewData.event(DoorPosition.CLOSED, "2026-04-29T11:30:00Z"),
        // Yesterday — open then misaligned anomaly while open, eventually closed.
        HistoryPreviewData.event(DoorPosition.OPENING, "2026-04-28T18:30:00Z"),
        HistoryPreviewData.event(DoorPosition.OPEN, "2026-04-28T18:30:08Z"),
        HistoryPreviewData.event(DoorPosition.OPEN_MISALIGNED, "2026-04-28T22:30:00Z"),
        HistoryPreviewData.event(DoorPosition.OPEN, "2026-04-28T22:35:00Z"),
        HistoryPreviewData.event(DoorPosition.CLOSING, "2026-04-28T23:00:00Z"),
        HistoryPreviewData.event(DoorPosition.CLOSED, "2026-04-28T23:00:06Z"),
        // Mon Apr 27 — unknown-state anomaly + stuck-closing.
        HistoryPreviewData.event(DoorPosition.UNKNOWN, "2026-04-27T11:00:00Z"),
        HistoryPreviewData.event(DoorPosition.CLOSING_TOO_LONG, "2026-04-27T16:00:00Z"),
    )
    val days = HistoryMapper.toHistoryDays(
        events = events,
        now = java.time.Instant.parse("2026-04-29T12:17:00Z"),
        zone = HistoryPreviewData.zone,
    )
    HistoryContent(days = days, zone = HistoryPreviewData.zone)
}

@Preview
@Composable
fun HistoryContentEmptyPreview() {
    HistoryContent(days = emptyList(), zone = HistoryPreviewData.zone)
}
