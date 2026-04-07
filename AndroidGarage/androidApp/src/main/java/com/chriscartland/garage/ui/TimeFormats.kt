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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.chriscartland.garage.domain.model.FriendlyDuration
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.toKotlinDuration

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
 * Delegates to [FriendlyDuration.format] (shared KMP code) after converting
 * from java.time.Duration to kotlin.time.Duration.
 */
fun Duration.toFriendlyDuration(): String = FriendlyDuration.format(this.toKotlinDuration())

/**
 * Composable that updates periodically based on the duration since a specific time.
 */
@Composable
fun DurationSince(
    time: Instant?,
    defaultDuration: Duration = Duration.ZERO,
    updatePeriod: Duration = Duration.ofSeconds(1),
    content: @Composable (duration: Duration) -> Unit,
) {
    var checkInDuration by remember { mutableStateOf(defaultDuration) }
    LaunchedEffect(key1 = time) {
        while (true) {
            checkInDuration = if (time == null) {
                defaultDuration
            } else {
                Duration.between(time, Instant.now())
            }
            delay(updatePeriod.toMillis())
        }
    }
    content(checkInDuration)
}
