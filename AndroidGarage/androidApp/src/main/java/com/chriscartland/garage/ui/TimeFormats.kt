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
 * Returns a [State] holding the live [Duration] since [time], updated every second.
 *
 * Replaces the old `DurationSince` content-lambda composable. Callers read
 * the value directly instead of being wrapped in a lambda, which fixes blank
 * screenshots and keeps layout trees flat.
 */
@Composable
fun rememberDurationSince(time: Instant?): State<Duration> {
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
