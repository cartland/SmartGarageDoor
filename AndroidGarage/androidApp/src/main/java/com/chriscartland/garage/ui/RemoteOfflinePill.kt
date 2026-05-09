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
import androidx.compose.material.icons.outlined.SensorsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chriscartland.garage.ui.theme.PreviewComponentSurface
import com.chriscartland.garage.ui.theme.Spacing
import com.chriscartland.garage.usecase.ButtonHealthDisplay

/**
 * Compact rounded-pill indicator that the remote-button device is offline.
 *
 * Lives in the Home tab's "Remote control" section header (right-aligned,
 * in-line with the section label). Mirrors [TitleBarCheckInPill]'s structure
 * so both indicators speak the same visual grammar.
 *
 * Stateless: pass a pre-formatted [ButtonHealthDisplay.Offline]. The
 * duration label comes from the LiveClock-driven flow in the caller
 * (typically `RemoteButtonViewModel.buttonHealthDisplay`).
 *
 * Always renders in the error palette: `errorContainer` background +
 * `onErrorContainer` content. The pill is only ever rendered when state
 * is OFFLINE — the four other [ButtonHealthDisplay] arms render no pill,
 * so this Composable doesn't carry a fresh/stale toggle.
 *
 * Uses Material 24-viewport `Icons.Outlined.SensorsOff` — purpose-built
 * for IoT device-availability indicators, matching the always-on
 * [RemoteButtonHealthPill]. Custom 960-viewport vectors inside section
 * header rows have been observed to silently drop the section from
 * screenshot capture (see CLAUDE.md "Layoutlib gotcha"); Material icons
 * avoid the issue.
 */
@Composable
fun RemoteOfflinePill(
    display: ButtonHealthDisplay.Offline,
    modifier: Modifier = Modifier,
) {
    val pillShape = RoundedCornerShape(50)
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onErrorContainer) {
        Box(
            modifier = modifier
                .background(color = MaterialTheme.colorScheme.errorContainer, shape = pillShape)
                .padding(start = 8.dp, end = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Unavailable · ${display.durationLabel}",
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(modifier = Modifier.width(Spacing.Tight))
                Icon(
                    modifier = Modifier.size(17.dp),
                    imageVector = Icons.Outlined.SensorsOff,
                    tint = LocalContentColor.current,
                    contentDescription = "Remote button unavailable, last seen ${display.durationLabel}",
                )
            }
        }
    }
}

@Preview
@Composable
fun RemoteOfflinePillFreshPreview() {
    PreviewComponentSurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RemoteOfflinePill(display = ButtonHealthDisplay.Offline(durationLabel = "1 min ago"))
        }
    }
}

@Preview
@Composable
fun RemoteOfflinePillAgingPreview() {
    PreviewComponentSurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RemoteOfflinePill(display = ButtonHealthDisplay.Offline(durationLabel = "11 min ago"))
        }
    }
}

@Preview
@Composable
fun RemoteOfflinePillStalePreview() {
    PreviewComponentSurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RemoteOfflinePill(display = ButtonHealthDisplay.Offline(durationLabel = "2 hr ago"))
        }
    }
}

@Preview
@Composable
fun RemoteOfflinePillVeryStalePreview() {
    PreviewComponentSurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RemoteOfflinePill(display = ButtonHealthDisplay.Offline(durationLabel = "3 days ago"))
        }
    }
}
