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

package com.chriscartland.garage.wear.ui.theme

import androidx.compose.ui.graphics.Color
import com.chriscartland.garage.domain.model.DoorAnimation
import com.chriscartland.garage.domain.model.DoorColorState
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.GarageDoorPalette

/**
 * Door fill colors for the Wear app, sourced from the shared Tier-1
 * `GarageDoorPalette` (`:domain`). Wear renders on an always-dark canvas,
 * so only the `_DARK` scheme variants are used. Check-in staleness is not
 * surfaced on the watch yet, so only the `FRESH` variants apply
 * (docs/WEAR_OS.md lists staleness as a follow-up).
 */
object WearDoorColors {
    val closed = Color(GarageDoorPalette.CLOSED_FRESH_DARK)
    val open = Color(GarageDoorPalette.OPEN_FRESH_DARK)
    val unknown = Color(GarageDoorPalette.UNKNOWN_FRESH_DARK)

    fun forPosition(doorPosition: DoorPosition): Color =
        when (DoorAnimation.colorStateFor(doorPosition)) {
            DoorColorState.CLOSED -> closed
            DoorColorState.OPEN -> open
            DoorColorState.UNKNOWN -> unknown
        }
}
