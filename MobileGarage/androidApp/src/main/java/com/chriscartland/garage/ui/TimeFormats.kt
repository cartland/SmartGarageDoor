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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalInspectionMode
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Convert Unix timestamp seconds to a human-readable date string.
 */
fun Long.toFriendlyDate(): String =
    Instant
        .ofEpochSecond(this)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

/**
 * Convert Unix timestamp seconds to a human-readable time string.
 */
fun Long.toFriendlyTime(): String? =
    Instant
        .ofEpochSecond(this)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM))

fun Long.toFriendlyTimeShort(): String? =
    Instant
        .ofEpochSecond(this)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

/**
 * Convert a [java.time.Duration] to a human-readable string.
 *
 * Android-side on purpose. This used to live in `:domain` as `FriendlyDuration`,
 * whose KDoc claimed "no locale or platform dependencies" while it built
 * `"2 days, 3h 4m 5s"` — English words and English pluralization ("day" plus an
 * "s") compiled into shared code that iOS would have inherited. The shared layer
 * was not deciding anything here that a platform cannot decide from the
 * [Duration] it already holds, so the whole thing belongs on this side of the
 * boundary.
 */
fun Duration.toFriendlyDuration(): String {
    val totalSeconds = this.seconds
    val days = totalSeconds / 86_400
    val hours = (totalSeconds % 86_400) / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return when {
        days > 0 -> "$days day${if (days > 1) "s" else ""}, ${hours}h ${minutes}m ${seconds}s"
        hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

/**
 * Returns a [State] holding the live [Duration] since [time], updated every second.
 *
 * Replaces the old `DurationSince` content-lambda composable. Callers read
 * the value directly instead of being wrapped in a lambda, which fixes blank
 * screenshots and keeps layout trees flat.
 */
@Composable
fun rememberDurationSince(time: Instant?): State<Duration> {
    if (LocalInspectionMode.current) {
        // Screenshot tests / IDE previews don't run LaunchedEffect long enough
        // to update the duration past Duration.ZERO, which makes preview UIs
        // show "0s". Substitute a fixed non-zero duration so previews look
        // realistic without affecting production.
        return remember { mutableStateOf(Duration.ofMinutes(5).plusSeconds(23)) }
    }
    val duration = remember { mutableStateOf(Duration.ZERO) }
    LaunchedEffect(time) {
        while (true) {
            duration.value = if (time != null) {
                Duration.between(time, Instant.now())
            } else {
                Duration.ZERO
            }
            delay(1_000L)
        }
    }
    return duration
}
