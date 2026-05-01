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

package com.chriscartland.garage.ui.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.outlined.SignalWifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import com.chriscartland.garage.ui.theme.PreviewSurface

/**
 * Display state for the "Device" section row that appears on Home and
 * History tabs. Carries pre-formatted strings so the Composable stays
 * stateless and unit tests cover the formatting logic directly.
 *
 * @param durationLabel e.g. "Just now", "30 sec ago", "1 min 30 sec ago".
 * @param isStale true once the heartbeat is older than the staleness
 *   threshold (`CHECK_IN_STALE_THRESHOLD_SECONDS`, 11 min). Drives the
 *   icon flip + supporting copy.
 */
data class DeviceCheckInDisplay(
    val durationLabel: String,
    val isStale: Boolean,
)

/**
 * Pure-function formatter for the device check-in label. Driven by
 * [com.chriscartland.garage.usecase.LiveClock]'s 10s tick, so the seconds
 * component naturally updates on each tick.
 *
 * @param lastCheckInSeconds epoch-seconds of the most recent device
 *   heartbeat (`DoorEvent.lastCheckInTimeSeconds`). Null when no event
 *   has been received yet.
 * @param nowSeconds epoch-seconds of the current wall-clock — typically
 *   `LiveClock.nowEpochSeconds.value`.
 * @param staleThresholdSeconds heartbeat age past which the indicator
 *   flips to stale. Defaults to 11 minutes (matches
 *   `CheckInStalenessManager.CHECK_IN_STALE_THRESHOLD_SECONDS`).
 */
object DeviceCheckIn {
    fun format(
        lastCheckInSeconds: Long?,
        nowSeconds: Long,
        staleThresholdSeconds: Long = STALE_THRESHOLD_SECONDS,
    ): DeviceCheckInDisplay {
        if (lastCheckInSeconds == null) {
            return DeviceCheckInDisplay(durationLabel = "No data yet", isStale = false)
        }
        val age = (nowSeconds - lastCheckInSeconds).coerceAtLeast(0L)
        val label = when {
            age < SECONDS_PER_MIN -> if (age < 10) "Just now" else "$age sec ago"
            age < SECONDS_PER_HOUR -> {
                val minutes = age / SECONDS_PER_MIN
                val seconds = age % SECONDS_PER_MIN
                if (seconds == 0L) "$minutes min ago" else "$minutes min $seconds sec ago"
            }
            age < SECONDS_PER_DAY -> {
                val hours = age / SECONDS_PER_HOUR
                val minutes = (age % SECONDS_PER_HOUR) / SECONDS_PER_MIN
                if (minutes == 0L) "$hours hr ago" else "$hours hr $minutes min ago"
            }
            else -> {
                val days = age / SECONDS_PER_DAY
                if (days == 1L) "1 day ago" else "$days days ago"
            }
        }
        return DeviceCheckInDisplay(
            durationLabel = label,
            isStale = age > staleThresholdSeconds,
        )
    }

    private const val SECONDS_PER_MIN = 60L
    private const val SECONDS_PER_HOUR = 3_600L
    private const val SECONDS_PER_DAY = 86_400L

    /** Mirrors `CheckInStalenessManager.CHECK_IN_STALE_THRESHOLD_SECONDS`. */
    const val STALE_THRESHOLD_SECONDS = 11L * 60
}

/**
 * Sectioned-list row that surfaces the device's most recent heartbeat
 * age, separate from the door's last state-change. Always visible on
 * Home and History — fits the new M3 sectioned-list aesthetic
 * (`surfaceContainer` rounded surface, leading icon, ListItem row).
 *
 * Stateless — caller passes a pre-formatted [DeviceCheckInDisplay].
 */
@Composable
fun DeviceCheckInSection(
    display: DeviceCheckInDisplay,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        ListItem(
            headlineContent = { Text("Last contact") },
            supportingContent = { Text(display.durationLabel) },
            leadingContent = {
                Icon(
                    imageVector = if (display.isStale) {
                        Icons.Outlined.SignalWifiOff
                    } else {
                        Icons.Filled.SignalCellular4Bar
                    },
                    contentDescription = if (display.isStale) {
                        "Device offline"
                    } else {
                        "Device online"
                    },
                    tint = if (display.isStale) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            modifier = Modifier.clip(MaterialTheme.shapes.large),
        )
    }
}

@Preview
@Composable
fun DeviceCheckInSectionFreshPreview() {
    PreviewSurface {
        DeviceCheckInSection(
            display = DeviceCheckInDisplay(durationLabel = "30 sec ago", isStale = false),
        )
    }
}

@Preview
@Composable
fun DeviceCheckInSectionAgingPreview() {
    PreviewSurface {
        DeviceCheckInSection(
            display = DeviceCheckInDisplay(durationLabel = "5 min 20 sec ago", isStale = false),
        )
    }
}

@Preview
@Composable
fun DeviceCheckInSectionStalePreview() {
    PreviewSurface {
        DeviceCheckInSection(
            display = DeviceCheckInDisplay(durationLabel = "23 min ago", isStale = true),
        )
    }
}
