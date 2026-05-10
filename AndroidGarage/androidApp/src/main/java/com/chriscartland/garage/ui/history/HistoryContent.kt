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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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

/**
 * One entry in the history list.
 *
 * Open and Closed are separate rows (no merging across the open/close
 * boundary). Each row carries the duration of *that* state (until the next
 * opposite-state event), not the duration of the prior state.
 *
 * Display strings (times, durations, transit warnings) are pre-formatted by
 * the caller so the Composable stays purely presentational and screenshot
 * tests stay deterministic — Phase 2 of the redesign carries no live time
 * math.
 */
sealed interface HistoryEntry {
    /**
     * The door was opened.
     *
     * @param timeDisplay e.g. "9:47 AM"
     * @param durationDisplay e.g. "Open for 6 min" or "12 min and counting"
     * @param isCurrent true when this is the most recent event and the door
     *   is still open. Drives "Open · Since X" wording instead of past-tense
     *   "Opened at X".
     * @param transitWarning non-null when an `OPENING_TOO_LONG` was merged
     *   into this Opened row. Example: "Took 4 min to open, longer than
     *   expected." Rendered as a warning tag below the duration.
     */
    data class Opened(
        val timeDisplay: String,
        val durationDisplay: String,
        val isCurrent: Boolean = false,
        val transitWarning: String? = null,
        val misaligned: Boolean = false,
    ) : HistoryEntry

    /** The door was closed. Mirrors [Opened]. */
    data class Closed(
        val timeDisplay: String,
        val durationDisplay: String,
        val isCurrent: Boolean = false,
        val transitWarning: String? = null,
    ) : HistoryEntry

    /**
     * Errors and unresolved transitions: sensor conflict, unknown,
     * misalignment, stuck opening/closing where the door never reached its
     * terminal state.
     *
     * @param doorPosition drives the GarageIcon leading visual (e.g. an
     *   `OPEN_MISALIGNED` anomaly shows the open door art).
     */
    data class Anomaly(
        val doorPosition: DoorPosition,
        val title: String,
        val timeDisplay: String,
    ) : HistoryEntry
}

/** A single day's worth of entries, newest-first. */
data class HistoryDay(
    val label: String,
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
                item { HistoryEmptyState() }
            } else {
                days.forEach { day ->
                    item(key = day.label) {
                        HistoryDaySection(day = day)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryDaySection(day: HistoryDay) {
    Column {
        Text(
            text = day.label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(
                start = Spacing.SectionHeaderStart,
                top = Spacing.SectionHeaderTop,
                bottom = Spacing.SectionHeaderBottom,
            ),
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
                    HistoryEntryRow(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun HistoryEntryRow(entry: HistoryEntry) {
    when (entry) {
        is HistoryEntry.Opened -> HistoryStateRow(
            // When misaligned, render the OPEN_MISALIGNED door art so the
            // misalignment is visible even on a row that's just an "Opened"
            // event with the misalignment property set.
            doorPosition = if (entry.misaligned) DoorPosition.OPEN_MISALIGNED else DoorPosition.OPEN,
            headline = when {
                entry.isCurrent && entry.misaligned -> "Open (misaligned)"
                entry.isCurrent -> "Open"
                else -> "Opened at ${entry.timeDisplay}"
            },
            supporting = if (entry.isCurrent) {
                "Since ${entry.timeDisplay} · ${entry.durationDisplay}"
            } else {
                entry.durationDisplay
            },
            warnings = listOfNotNull(
                entry.transitWarning,
                // For past Opened rows, surface misalignment as a tag below
                // the duration. When isCurrent, the headline already says
                // "Open (misaligned)" — no need for a duplicate tag.
                if (entry.misaligned && !entry.isCurrent) "Door was misaligned" else null,
            ),
        )
        is HistoryEntry.Closed -> HistoryStateRow(
            doorPosition = DoorPosition.CLOSED,
            headline = if (entry.isCurrent) "Closed" else "Closed at ${entry.timeDisplay}",
            supporting = if (entry.isCurrent) {
                "Since ${entry.timeDisplay} · ${entry.durationDisplay}"
            } else {
                entry.durationDisplay
            },
            warnings = listOfNotNull(entry.transitWarning),
        )
        is HistoryEntry.Anomaly -> HistoryStateRow(
            doorPosition = entry.doorPosition,
            headline = entry.title,
            supporting = entry.timeDisplay,
            warnings = emptyList(),
        )
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

@Composable
private fun HistoryEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp, start = 32.dp, end = 32.dp),
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
            text = "No events yet",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(ParagraphSpacing.TitleToBody))
        Text(
            text = "Open or close the garage and check back here.",
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
    HistoryContent(days = days)
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
    HistoryContent(days = days)
}

@Preview
@Composable
fun HistoryContentEmptyPreview() {
    HistoryContent(days = emptyList())
}
