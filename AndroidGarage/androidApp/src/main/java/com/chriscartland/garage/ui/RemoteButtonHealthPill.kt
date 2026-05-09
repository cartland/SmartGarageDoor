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
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.SensorsOff
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chriscartland.garage.ui.theme.PreviewComponentSurface
import com.chriscartland.garage.usecase.ButtonHealthDisplay

/**
 * Always-on debug pill that renders for every [ButtonHealthDisplay] arm.
 *
 * Sister to [RemoteOfflinePill]: same pill grammar (rounded-50, label + icon),
 * but the production-only `Offline` filter is removed so every state is
 * visible. Intended as a temporary diagnostic surface — [RemoteOfflinePill]
 * is still the long-term home for the user-facing "something is wrong"
 * signal.
 *
 * To revert to the production-only behavior, swap the call site back to
 * [RemoteOfflinePill] (which only renders for [ButtonHealthDisplay.Offline])
 * and delete this file.
 *
 * Visual hierarchy preserved: `Offline` keeps the error palette so it still
 * screams; the four other arms use the neutral palette so they whisper. The
 * grammar matches [TitleBarCheckInPill] (fresh = neutral, stale = error).
 *
 * Stateless: pass a pre-derived [ButtonHealthDisplay] (the live flow comes
 * from `RemoteButtonViewModel.buttonHealthDisplay`).
 *
 * Uses Material 24-viewport icons. Custom 960-viewport vectors inside
 * section header rows have been observed to silently drop the section from
 * screenshot capture (see CLAUDE.md "Layoutlib gotcha").
 */
@Composable
fun RemoteButtonHealthPill(
    display: ButtonHealthDisplay,
    modifier: Modifier = Modifier,
) {
    val pillShape = RoundedCornerShape(50)
    val isOffline = display is ButtonHealthDisplay.Offline
    val backgroundColor = if (isOffline) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isOffline) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val (label, icon, iconDescription) = RemoteButtonHealthPillContents.from(display)
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(
            modifier = modifier
                .background(color = backgroundColor, shape = pillShape)
                .padding(start = 8.dp, end = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    modifier = Modifier.size(17.dp),
                    imageVector = icon,
                    tint = LocalContentColor.current,
                    contentDescription = iconDescription,
                )
            }
        }
    }
}

private data class PillContents(
    val label: String,
    val icon: ImageVector,
    val iconDescription: String,
)

private object RemoteButtonHealthPillContents {
    fun from(display: ButtonHealthDisplay): PillContents =
        when (display) {
            is ButtonHealthDisplay.Unauthorized -> PillContents(
                label = "Unauthorized",
                icon = Icons.Outlined.Lock,
                iconDescription = "Remote button unauthorized (signed out or not on allowlist)",
            )
            is ButtonHealthDisplay.Loading -> PillContents(
                label = "Checking…",
                icon = Icons.Outlined.Sync,
                iconDescription = "Checking remote button status",
            )
            is ButtonHealthDisplay.Unknown -> PillContents(
                label = "Unknown",
                icon = Icons.Outlined.HelpOutline,
                iconDescription = "Remote button status unknown",
            )
            is ButtonHealthDisplay.Online -> PillContents(
                label = "Available",
                icon = Icons.Outlined.Sensors,
                iconDescription = "Remote button available",
            )
            is ButtonHealthDisplay.Offline -> PillContents(
                label = "Unavailable · ${display.durationLabel}",
                icon = Icons.Outlined.SensorsOff,
                iconDescription = "Remote button unavailable, last seen ${display.durationLabel}",
            )
        }
}

@Preview
@Composable
fun RemoteButtonHealthPillUnauthorizedPreview() {
    PreviewComponentSurface {
        RemoteButtonHealthPill(display = ButtonHealthDisplay.Unauthorized)
    }
}

@Preview
@Composable
fun RemoteButtonHealthPillLoadingPreview() {
    PreviewComponentSurface {
        RemoteButtonHealthPill(display = ButtonHealthDisplay.Loading)
    }
}

@Preview
@Composable
fun RemoteButtonHealthPillUnknownPreview() {
    PreviewComponentSurface {
        RemoteButtonHealthPill(display = ButtonHealthDisplay.Unknown)
    }
}

@Preview
@Composable
fun RemoteButtonHealthPillAvailablePreview() {
    PreviewComponentSurface {
        RemoteButtonHealthPill(display = ButtonHealthDisplay.Online)
    }
}

@Preview
@Composable
fun RemoteButtonHealthPillUnavailablePreview() {
    PreviewComponentSurface {
        RemoteButtonHealthPill(
            display = ButtonHealthDisplay.Offline(durationLabel = "11 min ago"),
        )
    }
}
