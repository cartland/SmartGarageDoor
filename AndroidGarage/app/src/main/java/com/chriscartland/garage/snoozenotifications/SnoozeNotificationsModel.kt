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

package com.chriscartland.garage.snoozenotifications

import com.chriscartland.garage.internet.SnoozeDurationParameter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Limited set of options available in the Android app.
 */
enum class SnoozeDurationUIOption(
    val duration: Duration,
) {
    None(0.hours),
    OneHour(1.hours),
    FourHours(4.hours),
    EightHours(8.hours),
    TwelveHours(12.hours),
}

fun SnoozeDurationUIOption.toServer(): SnoozeDurationServerOption {
    return when (this) {
        SnoozeDurationUIOption.None -> SnoozeDurationServerOption.Hours_0
        SnoozeDurationUIOption.OneHour -> SnoozeDurationServerOption.Hours_1
        SnoozeDurationUIOption.FourHours -> SnoozeDurationServerOption.Hours_4
        SnoozeDurationUIOption.EightHours -> SnoozeDurationServerOption.Hours_8
        SnoozeDurationUIOption.TwelveHours -> SnoozeDurationServerOption.Hours_12
    }
}

/**
 * Full list of values accepted by the server.
 */
@Suppress("unused")
enum class SnoozeDurationServerOption(val duration: String) {
    Hours_0("0h"),
    Hours_1("1h"),
    Hours_2("2h"),
    Hours_3("3h"),
    Hours_4("4h"),
    Hours_5("5h"),
    Hours_6("6h"),
    Hours_7("7h"),
    Hours_8("8h"),
    Hours_9("9h"),
    Hours_10("10h"),
    Hours_11("11h"),
    Hours_12("12h"),
}

/**
 * Type-safe way to get the string parameter to the server.
 */
fun SnoozeDurationServerOption.toParam() = SnoozeDurationParameter(this.duration)
