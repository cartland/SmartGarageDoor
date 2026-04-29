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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.ui.GarageIcon
import com.chriscartland.garage.ui.theme.DoorColorState
import com.chriscartland.garage.ui.theme.LocalDoorStatusColorScheme
import com.chriscartland.garage.ui.theme.doorColorSet
import com.chriscartland.garage.ui.theme.doorColorState

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
     *   into this Opened row — e.g. "Took 4 min to open — longer than
     *   expected." Rendered as a warning tag below the duration.
     */
    data class Opened(
        val timeDisplay: String,
        val durationDisplay: String,
        val isCurrent: Boolean = false,
        val transitWarning: String? = null,
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
 */
@Composable
fun HistoryContent(
    days: List<HistoryDay>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun HistoryDaySection(day: HistoryDay) {
    Column {
        Text(
            text = day.label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
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
                        HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
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
            doorPosition = DoorPosition.OPEN,
            headline = if (entry.isCurrent) "Open" else "Opened at ${entry.timeDisplay}",
            supporting = if (entry.isCurrent) {
                "Since ${entry.timeDisplay} · ${entry.durationDisplay}"
            } else {
                entry.durationDisplay
            },
            transitWarning = entry.transitWarning,
        )
        is HistoryEntry.Closed -> HistoryStateRow(
            doorPosition = DoorPosition.CLOSED,
            headline = if (entry.isCurrent) "Closed" else "Closed at ${entry.timeDisplay}",
            supporting = if (entry.isCurrent) {
                "Since ${entry.timeDisplay} · ${entry.durationDisplay}"
            } else {
                entry.durationDisplay
            },
            transitWarning = entry.transitWarning,
        )
        is HistoryEntry.Anomaly -> HistoryStateRow(
            doorPosition = entry.doorPosition,
            headline = entry.title,
            supporting = entry.timeDisplay,
            transitWarning = null,
        )
    }
}

@Composable
private fun HistoryStateRow(
    doorPosition: DoorPosition,
    headline: String,
    supporting: String,
    transitWarning: String?,
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
                if (transitWarning != null) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = transitWarning,
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
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Open or close the garage and check back here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------- Preview helpers ----------

@Preview
@Composable
fun HistoryContentMultiDayPreview() {
    HistoryContent(
        days = listOf(
            HistoryDay(
                label = "Today",
                entries = listOf(
                    HistoryEntry.Opened(
                        timeDisplay = "10:15 AM",
                        durationDisplay = "12 min and counting",
                        isCurrent = true,
                    ),
                    HistoryEntry.Closed(
                        timeDisplay = "9:53 AM",
                        durationDisplay = "Closed for 22 min",
                    ),
                    HistoryEntry.Opened(
                        timeDisplay = "9:47 AM",
                        durationDisplay = "Open for 6 min",
                        transitWarning = "Took 4 min to open — longer than expected",
                    ),
                ),
            ),
            HistoryDay(
                label = "Yesterday",
                entries = listOf(
                    HistoryEntry.Anomaly(
                        doorPosition = DoorPosition.ERROR_SENSOR_CONFLICT,
                        title = "Sensor conflict",
                        timeDisplay = "11:42 PM",
                    ),
                    HistoryEntry.Closed(
                        timeDisplay = "8:30 PM",
                        durationDisplay = "Closed for 13 hr 17 min",
                    ),
                    HistoryEntry.Opened(
                        timeDisplay = "6:30 PM",
                        durationDisplay = "Open for 2 hr",
                    ),
                    HistoryEntry.Closed(
                        timeDisplay = "8:05 AM",
                        durationDisplay = "Closed for 10 hr 25 min",
                    ),
                    HistoryEntry.Opened(
                        timeDisplay = "8:00 AM",
                        durationDisplay = "Open for 5 min",
                    ),
                ),
            ),
            HistoryDay(
                label = "Mon, Apr 27",
                entries = listOf(
                    HistoryEntry.Anomaly(
                        doorPosition = DoorPosition.OPENING_TOO_LONG,
                        title = "Stuck opening",
                        timeDisplay = "5:30 PM",
                    ),
                    HistoryEntry.Closed(
                        timeDisplay = "7:18 AM",
                        durationDisplay = "Closed for 10 hr 12 min",
                        transitWarning = "Took 3 min to close — longer than expected",
                    ),
                    HistoryEntry.Opened(
                        timeDisplay = "7:15 AM",
                        durationDisplay = "Open for 3 min",
                    ),
                ),
            ),
        ),
    )
}

@Preview
@Composable
fun HistoryContentEmptyPreview() {
    HistoryContent(days = emptyList())
}
