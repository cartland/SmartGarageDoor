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

import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider
import com.chriscartland.garage.domain.model.DoorColorState
import com.chriscartland.garage.presentation.DataFreshness
import com.chriscartland.garage.ui.theme.DoorColorSet
import com.chriscartland.garage.ui.theme.DoorMuting
import com.chriscartland.garage.ui.theme.DoorStatusColorScheme
import com.chriscartland.garage.ui.theme.FreshnessTint
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
    /**
     * The card fill: the SAME rule as the Home card and the tile — the fresh
     * family colour drained by luma and dimmed by the shared alpha when the
     * verdict is muted ([DoorMuting]). Until strategy 1.2 this read the
     * palette's partial `_STALE_` variant instead, so a stale open door stayed
     * brick-red here while the tile went grey.
     */
    fun background(
        state: DoorColorState,
        freshness: DataFreshness,
    ): ColorProvider =
        dayNightColorProvider(
            day = fill(doorStatusLightScheme, state, freshness),
            night = fill(doorStatusDarkScheme, state, freshness),
        )

    /**
     * One theme's fill. Exposed (internal) so the widget's use of the shared
     * rule is pinned on the JVM — a [ColorProvider] needs a Context to resolve,
     * which a plain unit test does not have.
     */
    internal fun fill(
        scheme: DoorStatusColorScheme,
        state: DoorColorState,
        freshness: DataFreshness,
    ): Color = DoorMuting.doorColor(scheme, state, freshness).withMutedAlpha(freshness)

    /** The text colour that belongs on [background], dimmed with it — the tile's `onDoorFill` rule. */
    fun onBackground(
        state: DoorColorState,
        freshness: DataFreshness,
    ): ColorProvider =
        dayNightColorProvider(
            day = lightSet.textFor(state).withMutedAlpha(freshness),
            night = darkSet.textFor(state).withMutedAlpha(freshness),
        )

    private val lightSet: DoorColorSet get() = doorStatusLightScheme.doorColorSet(isStale = false)

    private val darkSet: DoorColorSet get() = doorStatusDarkScheme.doorColorSet(isStale = false)

    private fun Color.withMutedAlpha(freshness: DataFreshness): Color = copy(alpha = FreshnessTint.alphaFor(freshness))

    private fun DoorColorSet.textFor(state: DoorColorState) =
        when (state) {
            DoorColorState.CLOSED -> onClosed
            DoorColorState.OPEN -> onOpen
            DoorColorState.UNKNOWN -> onUnknown
        }
}
