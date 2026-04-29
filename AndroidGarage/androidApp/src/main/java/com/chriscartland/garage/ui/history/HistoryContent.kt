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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Schedule
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * One entry in the history list.
 *
 * Display strings (times, durations) are pre-formatted by the caller so the
 * Composable stays purely presentational and screenshot tests stay
 * deterministic — Phase 2 of the redesign carries no live time math.
 */
sealed interface HistoryEntry {
    /** A paired open→close cycle with a known duration. */
    data class Session(
        val openedDisplay: String,
        val closedDisplay: String,
        val durationDisplay: String,
    ) : HistoryEntry

    /** The door is currently open with no matching close yet. */
    data class StillOpen(
        val openedDisplay: String,
        val sinceDisplay: String,
    ) : HistoryEntry

    /** An anomaly: errors, "too long" warnings, sensor conflicts. */
    data class Anomaly(
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
 * surfaces, ListItem rows — matches the redesigned Settings tab aesthetic.
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
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
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
        is HistoryEntry.Session -> HistoryListItem(
            icon = Icons.Outlined.Schedule,
            headline = "Open and closed",
            supporting = "${entry.openedDisplay} → ${entry.closedDisplay} · ${entry.durationDisplay}",
        )
        is HistoryEntry.StillOpen -> HistoryListItem(
            icon = Icons.Outlined.LockOpen,
            headline = "Currently open",
            supporting = "Since ${entry.openedDisplay} · ${entry.sinceDisplay}",
        )
        is HistoryEntry.Anomaly -> HistoryListItem(
            icon = Icons.Outlined.ReportProblem,
            headline = entry.title,
            supporting = entry.timeDisplay,
        )
    }
}

@Composable
private fun HistoryListItem(
    icon: ImageVector,
    headline: String,
    supporting: String,
) {
    ListItem(
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        headlineContent = { Text(headline) },
        supportingContent = { Text(supporting) },
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
                    HistoryEntry.StillOpen(
                        openedDisplay = "10:15 AM",
                        sinceDisplay = "12 min ago",
                    ),
                    HistoryEntry.Session(
                        openedDisplay = "9:47 AM",
                        closedDisplay = "9:53 AM",
                        durationDisplay = "6 min",
                    ),
                ),
            ),
            HistoryDay(
                label = "Yesterday",
                entries = listOf(
                    HistoryEntry.Anomaly(
                        title = "Sensor conflict",
                        timeDisplay = "11:42 PM",
                    ),
                    HistoryEntry.Session(
                        openedDisplay = "6:30 PM",
                        closedDisplay = "8:30 PM",
                        durationDisplay = "2 hr 0 min",
                    ),
                    HistoryEntry.Session(
                        openedDisplay = "8:00 AM",
                        closedDisplay = "8:05 AM",
                        durationDisplay = "5 min",
                    ),
                ),
            ),
            HistoryDay(
                label = "Mon, Apr 27",
                entries = listOf(
                    HistoryEntry.Anomaly(
                        title = "Took too long opening",
                        timeDisplay = "5:30 PM",
                    ),
                    HistoryEntry.Session(
                        openedDisplay = "7:15 AM",
                        closedDisplay = "7:18 AM",
                        durationDisplay = "3 min",
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

@Preview
@Composable
fun HistorySessionRowPreview() {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        HistoryEntryRow(
            entry = HistoryEntry.Session(
                openedDisplay = "6:30 PM",
                closedDisplay = "8:30 PM",
                durationDisplay = "2 hr 0 min",
            ),
        )
    }
}

@Preview
@Composable
fun HistoryStillOpenRowPreview() {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        HistoryEntryRow(
            entry = HistoryEntry.StillOpen(
                openedDisplay = "10:15 AM",
                sinceDisplay = "12 min ago",
            ),
        )
    }
}

@Preview
@Composable
fun HistoryAnomalyRowPreview() {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        HistoryEntryRow(
            entry = HistoryEntry.Anomaly(
                title = "Sensor conflict",
                timeDisplay = "11:42 PM",
            ),
        )
    }
}
