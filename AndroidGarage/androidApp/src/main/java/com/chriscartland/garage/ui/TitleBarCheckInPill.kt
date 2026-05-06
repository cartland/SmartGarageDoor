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

package com.chriscartland.garage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SignalWifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chriscartland.garage.R
import com.chriscartland.garage.ui.home.DeviceCheckInDisplay
import com.chriscartland.garage.ui.theme.PreviewComponentSurface

/**
 * Compact rounded-pill device-heartbeat indicator. Antenna icon + duration
 * text when fresh; disconnected icon + error coloring when stale (>11 min).
 *
 * Lives in the Home tab's "Status" section header (right-aligned, in-line with
 * the section label). The `TitleBar` prefix is historical — the pill briefly
 * occupied the TopAppBar's `actions` slot in 2.9.2 before moving to the Status
 * header.
 *
 * Stateless: pass a pre-formatted [DeviceCheckInDisplay]. The duration label
 * and `isStale` boolean both come from the LiveClock-driven flow in the
 * caller (typically the Home Composable).
 *
 * Uses neutral M3 tokens: `surfaceVariant` + `onSurfaceVariant` when fresh,
 * `errorContainer` + `onErrorContainer` when stale.
 */
@Composable
fun TitleBarCheckInPill(
    display: DeviceCheckInDisplay,
    modifier: Modifier = Modifier,
) {
    val pillShape = RoundedCornerShape(50)
    val backgroundColor = if (display.isStale) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (display.isStale) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val showText = display.durationLabel != "No data yet"
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(
            modifier = modifier
                .background(color = backgroundColor, shape = pillShape)
                .padding(start = 8.dp, end = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showText) {
                    Text(
                        text = display.durationLabel,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                if (display.isStale) {
                    Icon(
                        modifier = Modifier.size(17.dp),
                        imageVector = Icons.Outlined.SignalWifiOff,
                        tint = LocalContentColor.current,
                        contentDescription = "Device offline",
                    )
                } else {
                    Icon(
                        modifier = Modifier.size(17.dp),
                        painter = painterResource(id = R.drawable.baseline_cell_tower_24),
                        tint = LocalContentColor.current,
                        contentDescription = "Device online",
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun TitleBarCheckInPillFreshPreview() {
    PreviewComponentSurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TitleBarCheckInPill(
                display = DeviceCheckInDisplay(
                    durationLabel = "30 sec ago",
                    isStale = false,
                ),
            )
        }
    }
}

@Preview
@Composable
fun TitleBarCheckInPillAgingPreview() {
    PreviewComponentSurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TitleBarCheckInPill(
                display = DeviceCheckInDisplay(
                    durationLabel = "5 min 20 sec ago",
                    isStale = false,
                ),
            )
        }
    }
}

@Preview
@Composable
fun TitleBarCheckInPillStalePreview() {
    PreviewComponentSurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TitleBarCheckInPill(
                display = DeviceCheckInDisplay(
                    durationLabel = "23 min ago",
                    isStale = true,
                ),
            )
        }
    }
}

@Preview
@Composable
fun TitleBarCheckInPillNoDataPreview() {
    PreviewComponentSurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TitleBarCheckInPill(
                display = DeviceCheckInDisplay(
                    durationLabel = "No data yet",
                    isStale = false,
                ),
            )
        }
    }
}
