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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import java.time.Duration
import java.time.Instant

@Composable
fun LastCheckInBanner(
    lastCheckIn: Instant?,
    modifier: Modifier = Modifier,
    action: (() -> Unit)? = null,
    onOldCheckInChanged: (Boolean) -> Unit = {},
) {
    DurationSince(lastCheckIn) { duration ->
        Column(
            modifier = modifier,
            horizontalAlignment = CenterHorizontally,
        ) {
            Text(
                text = ("Door last checked in ${duration.toFriendlyDuration()} ago"),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            val isOld = duration > Duration.ofMinutes(11)
            if (isOld) {
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
            LaunchedEffect(isOld) {
                onOldCheckInChanged(isOld)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LastCheckInBannerPreview() {
    LastCheckInBanner(
        lastCheckIn = Instant.now().minus(Duration.ofMinutes(10)),
    )
}

@Preview(showBackground = true)
@Composable
fun LastCheckInBannerOldPreview() {
    LastCheckInBanner(
        lastCheckIn = Instant.now().minus(Duration.ofMinutes(20)),
    )
}

@Preview(showBackground = true)
@Composable
fun LastCheckInBannerOldWithActionPreview() {
    LastCheckInBanner(
        lastCheckIn = Instant.now().minus(Duration.ofMinutes(20)),
        action = {},
    )
}
