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

import androidx.compose.ui.graphics.Color
import com.chriscartland.garage.door.DoorEvent
import com.chriscartland.garage.door.DoorPosition
import java.time.Duration
import java.time.Instant

/**
 * Color scheme selected by the app theme.
 * Usually this is a light or dark color scheme.
 */
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

/**
 * Color set for a specific door status.
 * Usually changes if the data is fresh or stale.
 */
data class DoorColorSet(
    val closedContainer: Color,
    val closedOnContainer: Color,
    val openContainer: Color,
    val openOnContainer: Color,
    val unknownContainer: Color,
    val unknownOnContainer: Color,
)

fun DoorStatusColorScheme.DoorColorSet(isStale: Boolean): DoorColorSet {
    return if (isStale) {
        DoorColorSet(
            closedContainer = closedContainerStale,
            closedOnContainer = closedOnContainerStale,
            openContainer = openContainerStale,
            openOnContainer = openOnContainerStale,
            unknownContainer = unknownContainerStale,
            unknownOnContainer = unknownOnContainerStale,
        )
    } else {
        DoorColorSet(
            closedContainer = closedContainerFresh,
            closedOnContainer = closedOnContainerFresh,
            openContainer = openContainerFresh,
            openOnContainer = openOnContainerFresh,
            unknownContainer = unknownContainerFresh,
            unknownOnContainer = unknownOnContainerFresh,
        )
    }
}

val doorStatusLightScheme = DoorStatusColorScheme(
    closedContainerFresh = closedContainerFreshLight,
    closedContainerStale = closedContainerStaleLight,
    closedOnContainerFresh = closedOnContainerFreshLight,
    closedOnContainerStale = closedOnContainerStaleLight,
    openContainerFresh = openContainerFreshLight,
    openContainerStale = openContainerStaleLight,
    openOnContainerFresh = openOnContainerFreshLight,
    openOnContainerStale = openOnContainerStaleLight,
    unknownContainerFresh = unknownContainerFreshLight,
    unknownContainerStale = unknownContainerStaleLight,
    unknownOnContainerFresh = unknownOnContainerFreshLight,
    unknownOnContainerStale = unknownOnContainerStaleLight,
)

val doorStatusDarkScheme = DoorStatusColorScheme(
    closedContainerFresh = closedContainerFreshDark,
    closedContainerStale = closedContainerStaleDark,
    closedOnContainerFresh = closedOnContainerFreshDark,
    closedOnContainerStale = closedOnContainerStaleDark,
    openContainerFresh = openContainerFreshDark,
    openContainerStale = openContainerStaleDark,
    openOnContainerFresh = openOnContainerFreshDark,
    openOnContainerStale = openOnContainerStaleDark,
    unknownContainerFresh = unknownContainerFreshDark,
    unknownContainerStale = unknownContainerStaleDark,
    unknownOnContainerFresh = unknownOnContainerFreshDark,
    unknownOnContainerStale = unknownOnContainerStaleDark,
)

enum class DoorColorState {
    OPEN, CLOSED, UNKNOWN
}

fun DoorEvent?.DoorColorState(): DoorColorState {
    return when (this?.doorPosition) {
        DoorPosition.UNKNOWN -> DoorColorState.UNKNOWN
        DoorPosition.ERROR_SENSOR_CONFLICT -> DoorColorState.UNKNOWN
        DoorPosition.CLOSED -> DoorColorState.CLOSED
        else -> DoorColorState.OPEN
    }
}

fun DoorEvent?.isStale(
    maxAge: Duration,
    now: Instant = Instant.now(),
): Boolean {
    return this?.lastCheckInTimeSeconds?.let { utcSeconds ->
        val limit = now.minusSeconds(maxAge.seconds)
        Instant.ofEpochSecond(utcSeconds).isBefore(limit)
    } == true
}
