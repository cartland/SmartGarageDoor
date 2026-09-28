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

import androidx.annotation.StringRes
import com.chriscartland.garage.R
import com.chriscartland.garage.presentation.DoorHeadline
import com.chriscartland.garage.presentation.StatusHeadline

/**
 * The widget's words (ADR-035: shared decides, the platform words it).
 *
 * **Every door word here is the Home screen's own string resource, deliberately.**
 * The widget and the app describe the same door from the same
 * [com.chriscartland.garage.presentation.GlanceStatus], so if they read
 * differently one of them is lying, and a second set of labels is how that
 * starts. The `home_` prefix is therefore load-bearing rather than untidy: it
 * says out loud that these are not the widget's words to change. Anything that
 * genuinely belongs only to the widget (the "since" wrapper, the liveness word)
 * gets a `widget_` string of its own.
 *
 * Kept as `@StringRes` ids rather than resolved strings so the mapping is a
 * plain JVM-testable function — a Composable `stringResource` call could only be
 * checked by rendering.
 */
object GarageWidgetWords {
    /** The door line. Exhaustive on both sealed cases and all six headlines. */
    @StringRes
    fun headline(headline: StatusHeadline): Int =
        when (headline) {
            is StatusHeadline.Door ->
                when (headline.headline) {
                    DoorHeadline.OPEN -> R.string.home_door_state_open
                    DoorHeadline.CLOSED -> R.string.home_door_state_closed
                    DoorHeadline.OPENING -> R.string.home_door_state_opening
                    DoorHeadline.CLOSING -> R.string.home_door_state_closing
                    DoorHeadline.UNKNOWN -> R.string.home_door_state_unknown
                    DoorHeadline.SENSOR_CONFLICT -> R.string.home_door_state_sensor_conflict
                }

            StatusHeadline.Connecting -> R.string.home_door_state_connecting
            StatusHeadline.NoSignal -> R.string.home_door_state_no_signal
        }
}
