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
    val closedFresh: Color,
    val onClosedFresh: Color,
    val closedStale: Color,
    val onClosedStale: Color,
    val openFresh: Color,
    val onOpenFresh: Color,
    val openStale: Color,
    val onOpenStale: Color,
    val unknownFresh: Color,
    val onUnknownFresh: Color,
    val unknownStale: Color,
    val onUnknownStale: Color,
)

/**
 * Color set for a specific door status.
 * Usually changes if the data is fresh or stale.
 */
data class DoorColorSet(
    val closed: Color,
    val onClosed: Color,
    val open: Color,
    val onOpen: Color,
    val unknown: Color,
    val onUnknown: Color,
)

fun DoorStatusColorScheme.doorColorSet(isStale: Boolean): DoorColorSet =
    if (isStale) {
        DoorColorSet(
            closed = closedStale,
            onClosed = onClosedStale,
            open = openStale,
            onOpen = onOpenStale,
            unknown = unknownStale,
            onUnknown = onUnknownStale,
        )
    } else {
        DoorColorSet(
            closed = closedFresh,
            onClosed = onClosedFresh,
            open = openFresh,
            onOpen = onOpenFresh,
            unknown = unknownFresh,
            onUnknown = onUnknownFresh,
        )
    }

val doorStatusLightScheme =
    DoorStatusColorScheme(
        closedFresh = closedFreshLight,
        onClosedFresh = onClosedFreshLight,
        closedStale = closedStaleLight,
        onClosedStale = onClosedStaleLight,
        openFresh = openFreshLight,
        onOpenFresh = onOpenFreshLight,
        openStale = openStaleLight,
        onOpenStale = onOpenStaleLight,
        unknownFresh = unknownFreshLight,
        onUnknownFresh = onUnknownFreshLight,
        unknownStale = unknownStaleLight,
        onUnknownStale = onUnknownStaleLight,
    )

val doorStatusDarkScheme =
    DoorStatusColorScheme(
        closedFresh = closedFreshDark,
        onClosedFresh = onClosedFreshDark,
        closedStale = closedStaleDark,
        onClosedStale = onClosedStaleDark,
        openFresh = openFreshDark,
        onOpenFresh = onOpenFreshDark,
        openStale = openStaleDark,
        onOpenStale = onOpenStaleDark,
        unknownFresh = unknownFreshDark,
        onUnknownFresh = onUnknownFreshDark,
        unknownStale = unknownStaleDark,
        onUnknownStale = onUnknownStaleDark,
    )

enum class DoorColorState {
    OPEN,
    CLOSED,
    UNKNOWN,
}

fun DoorEvent?.doorColorState(): DoorColorState =
    when (this?.doorPosition) {
        DoorPosition.UNKNOWN -> DoorColorState.UNKNOWN
        DoorPosition.ERROR_SENSOR_CONFLICT -> DoorColorState.UNKNOWN
        DoorPosition.CLOSED -> DoorColorState.CLOSED
        else -> DoorColorState.OPEN
    }

fun DoorEvent?.isStale(
    maxAge: Duration,
    now: Instant = Instant.now(),
): Boolean =
    this?.lastCheckInTimeSeconds?.let { utcSeconds ->
        val limit = now.minusSeconds(maxAge.seconds)
        Instant.ofEpochSecond(utcSeconds).isBefore(limit)
    } == true
