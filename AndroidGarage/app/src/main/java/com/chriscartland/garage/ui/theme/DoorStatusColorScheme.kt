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

package com.chriscartland.garage.ui.theme

import androidx.compose.material3.ButtonColors
import androidx.compose.ui.graphics.Color
import com.chriscartland.garage.door.DoorEvent
import java.time.Instant
import kotlin.time.Duration

data class DoorStatusColorScheme(
    val closedContainerFresh: Color,
    val closedContainerStale: Color,
    val closedOnContainerFresh: Color,
    val closedOnContainerStale: Color,
    val openContainerFresh: Color,
    val openContainerStale: Color,
    val openOnContainerFresh: Color,
    val openOnContainerStale: Color,
    val unknownContainerFresh: Color,
    val unknownContainerStale: Color,
    val unknownOnContainerFresh: Color,
    val unknownOnContainerStale: Color,
)

enum class DoorColorStatus {
    OPEN, CLOSED, UNKNOWN,
}

val doorStatusLightScheme = DoorStatusColorScheme(
    closedContainerFresh = doorClosedContainerFreshLight,
    closedContainerStale = doorClosedContainerStaleLight,
    closedOnContainerFresh = doorClosedOnContainerFreshLight,
    closedOnContainerStale = doorClosedOnContainerStaleLight,
    openContainerFresh = doorOpenContainerFreshLight,
    openContainerStale = doorOpenContainerStaleLight,
    openOnContainerFresh = doorOpenOnContainerFreshLight,
    openOnContainerStale = doorOpenOnContainerStaleLight,
    unknownContainerFresh = doorUnknownContainerFreshLight,
    unknownContainerStale = doorUnknownContainerStaleLight,
    unknownOnContainerFresh = doorUnknownOnContainerFreshLight,
    unknownOnContainerStale = doorUnknownOnContainerStaleLight,
)

val doorStatusDarkScheme = DoorStatusColorScheme(
    closedContainerFresh = doorClosedContainerFreshDark,
    closedContainerStale = doorClosedContainerStaleDark,
    closedOnContainerFresh = doorClosedOnContainerFreshDark,
    closedOnContainerStale = doorClosedOnContainerStaleDark,
    openContainerFresh = doorOpenContainerFreshDark,
    openContainerStale = doorOpenContainerStaleDark,
    openOnContainerFresh = doorOpenOnContainerFreshDark,
    openOnContainerStale = doorOpenOnContainerStaleDark,
    unknownContainerFresh = doorUnknownContainerFreshDark,
    unknownContainerStale = doorUnknownContainerStaleDark,
    unknownOnContainerFresh = doorUnknownOnContainerFreshDark,
    unknownOnContainerStale = doorUnknownOnContainerStaleDark,
)

fun DoorEvent?.isStale(now: Instant, age: Duration): Boolean {
    return this?.lastCheckInTimeSeconds?.let { utcSeconds ->
        val limit = now.minusSeconds(age.inWholeSeconds)
        Instant.ofEpochSecond(utcSeconds).isAfter(limit)
    } != true
}

fun doorButtonColors(doorColors: DoorStatusColorScheme): ButtonColors {
    // We're hacking a bit by borrowing some of the door colors and using them with the button.
    // Goal: The greens and reds should come from the same palette, but the logic is different.
    // If the button is active, use the "green" color that matches the "closed" door status.
    // If the button is inactive, use the "red" color that matches the "open" door status.
    return ButtonColors(
        containerColor = doorColors.closedContainerFresh,
        contentColor = doorColors.closedOnContainerFresh,
        disabledContainerColor = doorColors.openContainerFresh,
        disabledContentColor = doorColors.openOnContainerFresh,
    )
}
