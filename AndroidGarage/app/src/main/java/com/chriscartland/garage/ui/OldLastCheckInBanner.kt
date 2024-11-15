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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chriscartland.garage.R
import java.time.Duration
import java.time.Instant

val OLD_DURATION_FOR_DOOR_CHECK_IN = Duration.ofMinutes(11)

@Composable
fun CheckInRow(lastCheckIn: Instant?) {
    DurationSince(lastCheckIn) { duration ->
        if (lastCheckIn != null) {
            Text(
                text = ("${duration.toFriendlyDuration()} ago"),
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        val isOld = lastCheckIn != null && duration > OLD_DURATION_FOR_DOOR_CHECK_IN
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
        Spacer(modifier = Modifier.width(16.dp))
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
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        ErrorCard(
            text = "Not receiving updates from server",
            buttonText = "Retry",
            onClick = { action() },
            modifier = Modifier.fillMaxWidth(),
        )
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
