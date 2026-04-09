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

package com.chriscartland.garage.domain.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Current snooze status from the server. Always visible in the UI.
 *
 * Only [Loading] on the very first fetch (app launch). Subsequent poll
 * failures silently keep the last known state — stale data is better
 * than error noise on a 60-second poll.
 */
sealed interface SnoozeState {
    /** First fetch in progress (app launch). */
    data object Loading : SnoozeState

    /** No active snooze. */
    data object NotSnoozing : SnoozeState

    /** Notifications are snoozed until [untilEpochSeconds]. */
    data class Snoozing(
        val untilEpochSeconds: Long,
    ) : SnoozeState
}

/**
 * Result of the last user-initiated snooze save. Overlays on top of
 * [SnoozeState] — the current snooze status is always visible underneath.
 *
 * [Succeeded] carries the new snooze time for optimistic UI updates
 * and auto-resets to [Idle] after a timeout.
 */
sealed interface SnoozeAction {
    /** No pending action. */
    data object Idle : SnoozeAction

    /** Request in flight — show spinner. */
    data object Sending : SnoozeAction

    /** Save succeeded — auto-resets to [Idle] after timeout. */
    sealed interface Succeeded : SnoozeAction {
        /** Snooze cleared (user selected "Do not snooze"). */
        data object Cleared : Succeeded

        /** Snoozing until [untilEpochSeconds] (optimistic). */
        data class Set(
            val untilEpochSeconds: Long,
        ) : Succeeded
    }

    /** Save failed with a specific, actionable reason. */
    sealed interface Failed : SnoozeAction {
        /** User must sign in before snoozing. */
        data object NotAuthenticated : Failed

        /** No door event available (needed for snooze timestamp). */
        data object MissingData : Failed

        /** Server unreachable or returned an error. */
        data object NetworkError : Failed
    }
}

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

fun SnoozeDurationUIOption.toServer(): SnoozeDurationServerOption =
    when (this) {
        SnoozeDurationUIOption.None -> SnoozeDurationServerOption.HOURS_0
        SnoozeDurationUIOption.OneHour -> SnoozeDurationServerOption.HOURS_1
        SnoozeDurationUIOption.FourHours -> SnoozeDurationServerOption.HOURS_4
        SnoozeDurationUIOption.EightHours -> SnoozeDurationServerOption.HOURS_8
        SnoozeDurationUIOption.TwelveHours -> SnoozeDurationServerOption.HOURS_12
    }

/**
 * Full list of values accepted by the server.
 */
@Suppress("unused")
enum class SnoozeDurationServerOption(
    val duration: String,
) {
    HOURS_0("0h"),
    HOURS_1("1h"),
    HOURS_2("2h"),
    HOURS_3("3h"),
    HOURS_4("4h"),
    HOURS_5("5h"),
    HOURS_6("6h"),
    HOURS_7("7h"),
    HOURS_8("8h"),
    HOURS_9("9h"),
    HOURS_10("10h"),
    HOURS_11("11h"),
    HOURS_12("12h"),
}
