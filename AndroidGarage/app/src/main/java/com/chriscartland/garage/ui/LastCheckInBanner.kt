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

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import java.time.Duration
import java.time.Instant

@Composable
fun LastCheckInBanner(lastCheckIn: Instant?) {
    DurationSince(lastCheckIn) { duration ->
        Text(
            text = ("Door broadcast ${duration.toFriendlyDuration()} ago"),
            style = MaterialTheme.typography.labelSmall,
        )
        if (duration > Duration.ofMinutes(15)) {
            Text(
                text = "Warning: Time since check-in is over 15 minutes",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
