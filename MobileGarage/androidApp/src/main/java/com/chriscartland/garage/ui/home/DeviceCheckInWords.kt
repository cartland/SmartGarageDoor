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

package com.chriscartland.garage.ui.home

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.chriscartland.garage.R
import com.chriscartland.garage.presentation.CheckInAge

/**
 * Which resource says a heartbeat's age. Pure and JVM-tested
 * ([DeviceCheckInWordsTest]); the Composable half only resolves it.
 *
 * Mirrors `RemoteOfflineText` for the button's offline age, and exists for
 * the same reason: the words used to be Kotlin literals in a `String`-typed
 * function the Compose literal lint could not see (strategy 4.6 —
 * `UiObjectStringLiteralKonsistTest` now refuses that shape). A plural is a
 * per-locale rule; picking the form by hand throws that away.
 */
sealed interface CheckInAgeResource {
    data class Plain(
        @param:StringRes val id: Int,
    ) : CheckInAgeResource

    data class Plural(
        @param:PluralsRes val id: Int,
        val count: Int,
    ) : CheckInAgeResource

    data class TwoPart(
        @param:StringRes val id: Int,
        val first: Int,
        val second: Int,
    ) : CheckInAgeResource
}

object DeviceCheckInWords {
    /** The resource for an age: the decision, without a `Context`. */
    fun resource(age: CheckInAge): CheckInAgeResource =
        when (age) {
            CheckInAge.JustNow -> CheckInAgeResource.Plain(R.string.check_in_just_now)
            is CheckInAge.Seconds -> CheckInAgeResource.Plural(R.plurals.check_in_seconds_ago, age.seconds)
            is CheckInAge.Minutes ->
                if (age.seconds == 0) {
                    CheckInAgeResource.Plural(R.plurals.check_in_minutes_ago, age.minutes)
                } else {
                    CheckInAgeResource.TwoPart(R.string.check_in_minutes_seconds_ago, age.minutes, age.seconds)
                }
            is CheckInAge.Hours ->
                if (age.minutes == 0) {
                    CheckInAgeResource.Plural(R.plurals.check_in_hours_ago, age.hours)
                } else {
                    CheckInAgeResource.TwoPart(R.string.check_in_hours_minutes_ago, age.hours, age.minutes)
                }
            is CheckInAge.Days -> CheckInAgeResource.Plural(R.plurals.check_in_days_ago, age.days)
        }

    /** "Just now" / "30 sec ago" / "5 min 20 sec ago" / "1 hr ago" / "3 days ago". */
    @Composable
    fun label(age: CheckInAge): String =
        when (val resource = resource(age)) {
            is CheckInAgeResource.Plain -> stringResource(resource.id)
            is CheckInAgeResource.Plural -> pluralStringResource(resource.id, resource.count, resource.count)
            is CheckInAgeResource.TwoPart -> stringResource(resource.id, resource.first, resource.second)
        }
}
