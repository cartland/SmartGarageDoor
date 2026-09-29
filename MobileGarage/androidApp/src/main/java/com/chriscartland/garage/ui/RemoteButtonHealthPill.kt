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
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chriscartland.garage.R
import com.chriscartland.garage.ui.home.RemoteOfflineText
import com.chriscartland.garage.ui.theme.PreviewComponentSurface
import com.chriscartland.garage.ui.theme.Spacing
import com.chriscartland.garage.usecase.ButtonHealthDisplay
import com.chriscartland.garage.usecase.ButtonOfflineAge
import com.chriscartland.garage.usecase.ButtonOfflineAgeSource

/**
 * Pill that renders every VERDICT arm of [ButtonHealthDisplay] — and
 * renders NOTHING for [ButtonHealthDisplay.Hidden] (hidden-until-verdict,
 * STATUS_CACHE_PLAN.md D2: no "Checking…" UI while unresolved).
 *
 * This is the production pill on both phones (strategy 2.6): every label
 * is a string resource, and the info sheet it opens explains each one.
 * [RemoteOfflinePill] is its Offline-only ancestor, kept for its previews.
 *
 * Visual hierarchy: `Offline` keeps the error palette so it still screams;
 * the three other rendered arms use the neutral palette so they whisper.
 * The grammar matches [DeviceCheckInPill] (fresh = neutral, stale = error).
 *
 * Stateless: pass a pre-derived [ButtonHealthDisplay] (the live flow comes
 * from `HomeViewModel.buttonHealthDisplay`).
 *
 * Uses Material 24-viewport icons. Custom 960-viewport vectors inside
 * section header rows have been observed to silently drop the section from
 * screenshot capture (see CLAUDE.md "Layoutlib gotcha").
 */
@Composable
fun RemoteButtonHealthPill(
    display: ButtonHealthDisplay,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
) {
    // Hidden-until-verdict: no pill at all while there is nothing to say.
    val contents = RemoteButtonHealthPillContents.from(display) ?: return
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
    val (label, icon, iconDescription) = contents
    val baseModifier = modifier
        .background(color = backgroundColor, shape = pillShape)
    val tapModifier = if (onTap != null) {
        Modifier
            .clickable(onClickLabel = stringResource(R.string.button_health_pill_tap_label)) { onTap() }
    } else {
        Modifier
    }
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(
            modifier = baseModifier
                .then(tapModifier)
                .padding(start = 8.dp, end = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(modifier = Modifier.width(Spacing.Tight))
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
    /**
     * Null = render nothing (the no-verdict arm has no pill).
     *
     * `@Composable` because the offline arm reads string resources: the shared
     * layer hands over a typed age bucket and the words are looked up here.
     */
    @Composable
    fun from(display: ButtonHealthDisplay): PillContents? =
        when (display) {
            is ButtonHealthDisplay.Unauthorized -> PillContents(
                label = stringResource(R.string.button_health_unauthorized),
                icon = Icons.Outlined.Lock,
                iconDescription = stringResource(R.string.button_health_unauthorized_description),
            )
            is ButtonHealthDisplay.Hidden -> null
            is ButtonHealthDisplay.Unknown -> PillContents(
                label = stringResource(R.string.button_health_unknown),
                icon = Icons.Outlined.HelpOutline,
                iconDescription = stringResource(R.string.button_health_unknown_description),
            )
            is ButtonHealthDisplay.Online -> PillContents(
                label = stringResource(R.string.button_health_available),
                icon = Icons.Outlined.Sensors,
                iconDescription = stringResource(R.string.button_health_available_description),
            )
            is ButtonHealthDisplay.Offline -> {
                val ageText = RemoteOfflineText.label(display)
                PillContents(
                    label = stringResource(R.string.remote_offline_pill_label, ageText),
                    icon = Icons.Outlined.SensorsOff,
                    iconDescription = stringResource(R.string.remote_offline_pill_description, ageText),
                )
            }
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
            display = ButtonHealthDisplay.Offline(age = ButtonOfflineAge.Minutes(11), source = ButtonOfflineAgeSource.LAST_SEEN),
        )
    }
}
