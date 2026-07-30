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

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.chriscartland.garage.R
import com.chriscartland.garage.usecase.ButtonHealthDisplay
import com.chriscartland.garage.usecase.ButtonOfflineAge
import com.chriscartland.garage.usecase.ButtonOfflineAgeSource

/**
 * Android wording for the shared [ButtonOfflineAge].
 *
 * The shared layer picks the bucket; this picks the words. It used to build the
 * whole phrase in `commonMain`, which meant the remote-button pill could not be
 * translated on either platform and `"day" + if (n == 1) "" else "s"` was the
 * app's pluralization rule for every language.
 *
 * Counts go through `plurals.xml` rather than `"%d min ago"`, because languages
 * disagree about how many plural forms there are — Android already knows the
 * rule per locale and picking the form by hand throws that away.
 */
object RemoteOfflineText {
    /** "Last seen 11 min ago" / "11 min ago", per the shared source discriminator. */
    @Composable
    fun label(display: ButtonHealthDisplay.Offline): String {
        val age = ageText(display.age)
        return when (display.source) {
            ButtonOfflineAgeSource.LAST_SEEN -> stringResource(R.string.remote_offline_last_seen, age)
            ButtonOfflineAgeSource.STATE_CHANGED -> age
        }
    }

    @Composable
    private fun ageText(age: ButtonOfflineAge): String =
        when (age) {
            ButtonOfflineAge.Unknown -> stringResource(R.string.remote_offline_age_unknown)
            ButtonOfflineAge.JustNow -> stringResource(R.string.remote_offline_age_just_now)
            is ButtonOfflineAge.Seconds ->
                pluralStringResource(R.plurals.remote_offline_age_seconds, age.seconds, age.seconds)
            is ButtonOfflineAge.Minutes ->
                pluralStringResource(R.plurals.remote_offline_age_minutes, age.minutes, age.minutes)
            is ButtonOfflineAge.Hours ->
                pluralStringResource(R.plurals.remote_offline_age_hours, age.hours, age.hours)
            is ButtonOfflineAge.Days ->
                pluralStringResource(R.plurals.remote_offline_age_days, age.days, age.days)
        }
}
