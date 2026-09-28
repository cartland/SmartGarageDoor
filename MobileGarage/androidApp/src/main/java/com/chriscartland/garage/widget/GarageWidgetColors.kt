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

package com.chriscartland.garage.widget

import androidx.glance.unit.ColorProvider
import com.chriscartland.garage.domain.model.DoorColorState
import com.chriscartland.garage.ui.theme.DoorColorSet
import com.chriscartland.garage.ui.theme.doorColorSet
import com.chriscartland.garage.ui.theme.doorStatusDarkScheme
import com.chriscartland.garage.ui.theme.doorStatusLightScheme
import androidx.glance.color.ColorProvider as dayNightColorProvider

/**
 * The widget's colours, taken from the app's own door palette.
 *
 * Nothing here defines a colour. It reads `doorStatusLightScheme` /
 * `doorStatusDarkScheme` — the same two schemes the Home card uses — so the
 * widget cannot drift to a different red than the app it belongs to, and the
 * `checkHardcodedColors` lint has nothing to object to.
 *
 * **The muting rule is inherited, not reimplemented.** `isMuted` comes straight
 * from the shared [com.chriscartland.garage.presentation.DataFreshness] verdict,
 * so a door the widget cannot vouch for greys by exactly the amount and on
 * exactly the condition every other surface greys — see CLAUDE.md § "The settle
 * window". A glance never settles, so in practice `isMuted` here means STALE.
 *
 * Dark mode is handled by Glance rather than by us: `ColorProvider(day, night)`
 * is resolved by the host at render time, which is also the only thing that
 * knows the launcher's configuration.
 */
object GarageWidgetColors {
    /** The card fill for this door state. */
    fun background(
        state: DoorColorState,
        isMuted: Boolean,
    ): ColorProvider =
        dayNightColorProvider(
            day = lightSet(isMuted).fillFor(state),
            night = darkSet(isMuted).fillFor(state),
        )

    /** The text colour that belongs on [background]. */
    fun onBackground(
        state: DoorColorState,
        isMuted: Boolean,
    ): ColorProvider =
        dayNightColorProvider(
            day = lightSet(isMuted).textFor(state),
            night = darkSet(isMuted).textFor(state),
        )

    private fun lightSet(isMuted: Boolean): DoorColorSet = doorStatusLightScheme.doorColorSet(isStale = isMuted)

    private fun darkSet(isMuted: Boolean): DoorColorSet = doorStatusDarkScheme.doorColorSet(isStale = isMuted)

    private fun DoorColorSet.fillFor(state: DoorColorState) =
        when (state) {
            DoorColorState.CLOSED -> closed
            DoorColorState.OPEN -> open
            DoorColorState.UNKNOWN -> unknown
        }

    private fun DoorColorSet.textFor(state: DoorColorState) =
        when (state) {
            DoorColorState.CLOSED -> onClosed
            DoorColorState.OPEN -> onOpen
            DoorColorState.UNKNOWN -> onUnknown
        }
}
