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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chriscartland.garage.R
import com.chriscartland.garage.ui.theme.LocalDoorStatusColorScheme
import java.time.Duration
import java.time.Instant

val OLD_DURATION_FOR_DOOR_CHECK_IN = Duration.ofMinutes(11)

data class PillColors(
    val backgroundColor: Color,
    val contentColor: Color,
)

@Composable
fun CheckInRow(
    lastCheckIn: Instant?,
    modifier: Modifier = Modifier,
    pillColors: PillColors = PillColors(
        backgroundColor = LocalDoorStatusColorScheme.current.unknownFresh,
        contentColor = LocalDoorStatusColorScheme.current.onUnknownFresh,
    ),
) {
    // This is called in a RowScope in TopAppBar.
    val pillShape = RoundedCornerShape(50)
    DurationSince(lastCheckIn) { duration ->
        val isOld = lastCheckIn != null && duration > OLD_DURATION_FOR_DOOR_CHECK_IN
        CompositionLocalProvider(LocalContentColor provides pillColors.contentColor) {
            Box(
                modifier = Modifier
                    .background(
                        color = pillColors.backgroundColor,
                        shape = pillShape
                    )
                    .padding(start = 8.dp, end = 4.dp),
            ) {
                Row(
                    modifier = modifier,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (lastCheckIn != null) {
                        Text(
                            text = ("${duration.toFriendlyDuration()} ago"),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Image(
                        modifier = Modifier.scale(0.7f),
                        painter = if (isOld) {
                            painterResource(id = R.drawable.outline_signal_disconnected_24)
                        } else {
                            painterResource(id = R.drawable.baseline_cell_tower_24)
                        },
                        colorFilter = ColorFilter.tint(LocalContentColor.current),
                        contentDescription = "Door Broadcast Icon",
                    )
                }
            }
        }
    }
}

@Composable
fun OldLastCheckInBanner(
    modifier: Modifier = Modifier,
    action: (() -> Unit)? = null,
) {
    if (action == null) {
        Text(
            text = "Warning: Not receiving updates from server",
            style = MaterialTheme.typography.labelSmall,
            modifier = modifier,
        )
    } else {
        ErrorCard(
            text = "Not receiving updates from server",
            buttonText = "Retry",
            onClick = { action() },
            modifier = modifier,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun LastCheckInRowPreview() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // This is called in a RowScope by TopAppBar.
        CheckInRow(Instant.now().minusSeconds(123))
    }
}

@Preview(showBackground = true)
@Composable
fun LastCheckInRowOldPreview() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // This is called in a RowScope by TopAppBar.
        CheckInRow(Instant.now().minusSeconds(1234))
    }
}

@Preview(showBackground = true)
@Composable
fun LastCheckInBannerPreview() {
    OldLastCheckInBanner()
}

@Preview(showBackground = true)
@Composable
fun LastCheckInBannerOldWithActionPreview() {
    OldLastCheckInBanner(
        action = {},
    )
}
