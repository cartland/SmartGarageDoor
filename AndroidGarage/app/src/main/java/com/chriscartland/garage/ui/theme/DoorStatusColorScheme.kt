package com.chriscartland.garage.ui.theme

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.CardColors
import androidx.compose.ui.graphics.Color
import com.chriscartland.garage.door.DoorEvent
import com.chriscartland.garage.door.DoorPosition
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class DoorStatusColorScheme(
    val doorClosedContainerFresh: Color,
    val doorClosedContainerStale: Color,
    val doorClosedOnContainerFresh: Color,
    val doorClosedOnContainerStale: Color,
    val doorOpenContainerFresh: Color,
    val doorOpenContainerStale: Color,
    val doorOpenOnContainerFresh: Color,
    val doorOpenOnContainerStale: Color,
    val doorUnknownContainerFresh: Color,
    val doorUnknownContainerStale: Color,
    val doorUnknownOnContainerFresh: Color,
    val doorUnknownOnContainerStale: Color,
)

fun DoorStatusColorScheme.select(
    status: DoorColorStatus,
    container: Boolean = true,
    fresh: Boolean = true,
): Color {
    return when (status) {
        DoorColorStatus.OPEN ->
            if (container) {
                if (fresh) doorOpenContainerFresh else doorOpenContainerStale
            } else {
                if (fresh) doorOpenOnContainerFresh else doorOpenOnContainerStale
            }
        DoorColorStatus.CLOSED ->
            if (container) {
                if (fresh) doorClosedContainerFresh else doorClosedContainerStale
            } else {
                if (fresh) doorClosedOnContainerFresh else doorClosedOnContainerStale
            }
        DoorColorStatus.UNKNOWN ->
            if (container) {
                if (fresh) doorUnknownContainerFresh else doorUnknownContainerStale
            } else {
                if (fresh) doorUnknownOnContainerFresh else doorUnknownOnContainerStale
            }
    }
}

enum class DoorColorStatus {
    OPEN,
    CLOSED,
    UNKNOWN,
}

val doorStatusLightScheme = DoorStatusColorScheme(
    doorClosedContainerFresh = doorClosedContainerFreshLightMediumContrast,
    doorClosedContainerStale = doorClosedContainerStaleLightMediumContrast,
    doorClosedOnContainerFresh = doorClosedOnContainerFreshLightMediumContrast,
    doorClosedOnContainerStale = doorClosedOnContainerStaleLightMediumContrast,
    doorOpenContainerFresh = doorOpenContainerFreshLightMediumContrast,
    doorOpenContainerStale = doorOpenContainerStaleLightMediumContrast,
    doorOpenOnContainerFresh = doorOpenOnContainerFreshLightMediumContrast,
    doorOpenOnContainerStale = doorOpenOnContainerStaleLightMediumContrast,
    doorUnknownContainerFresh = doorUnknownContainerFreshLightMediumContrast,
    doorUnknownContainerStale = doorUnknownContainerStaleLightMediumContrast,
    doorUnknownOnContainerFresh = doorUnknownOnContainerFreshLightMediumContrast,
    doorUnknownOnContainerStale = doorUnknownOnContainerStaleLightMediumContrast,
)

val doorStatusDarkScheme = DoorStatusColorScheme(
    doorClosedContainerFresh = doorClosedContainerFreshDarkMediumContrast,
    doorClosedContainerStale = doorClosedContainerStaleDarkMediumContrast,
    doorClosedOnContainerFresh = doorClosedOnContainerFreshDarkMediumContrast,
    doorClosedOnContainerStale = doorClosedOnContainerStaleDarkMediumContrast,
    doorOpenContainerFresh = doorOpenContainerFreshDarkMediumContrast,
    doorOpenContainerStale = doorOpenContainerStaleDarkMediumContrast,
    doorOpenOnContainerFresh = doorOpenOnContainerFreshDarkMediumContrast,
    doorOpenOnContainerStale = doorOpenOnContainerStaleDarkMediumContrast,
    doorUnknownContainerFresh = doorUnknownContainerFreshDarkMediumContrast,
    doorUnknownContainerStale = doorUnknownContainerStaleDarkMediumContrast,
    doorUnknownOnContainerFresh = doorUnknownOnContainerFreshDarkMediumContrast,
    doorUnknownOnContainerStale = doorUnknownOnContainerStaleDarkMediumContrast,
)

val doorStatusMediumContrastLightColorScheme = DoorStatusColorScheme(
    doorClosedContainerFresh = doorClosedContainerFreshLightMediumContrast,
    doorClosedContainerStale = doorClosedContainerStaleLightMediumContrast,
    doorClosedOnContainerFresh = doorClosedOnContainerFreshLightMediumContrast,
    doorClosedOnContainerStale = doorClosedOnContainerStaleLightMediumContrast,
    doorOpenContainerFresh = doorOpenContainerFreshLightMediumContrast,
    doorOpenContainerStale = doorOpenContainerStaleLightMediumContrast,
    doorOpenOnContainerFresh = doorOpenOnContainerFreshLightMediumContrast,
    doorOpenOnContainerStale = doorOpenOnContainerStaleLightMediumContrast,
    doorUnknownContainerFresh = doorUnknownContainerFreshLightMediumContrast,
    doorUnknownContainerStale = doorUnknownContainerStaleLightMediumContrast,
    doorUnknownOnContainerFresh = doorUnknownOnContainerFreshLightMediumContrast,
    doorUnknownOnContainerStale = doorUnknownOnContainerStaleLightMediumContrast,
)

val doorStatusHighContrastLightColorScheme = DoorStatusColorScheme(
    doorClosedContainerFresh = doorClosedContainerFreshLightHighContrast,
    doorClosedContainerStale = doorClosedContainerStaleLightHighContrast,
    doorClosedOnContainerFresh = doorClosedOnContainerFreshLightHighContrast,
    doorClosedOnContainerStale = doorClosedOnContainerStaleLightHighContrast,
    doorOpenContainerFresh = doorOpenContainerFreshLightHighContrast,
    doorOpenContainerStale = doorOpenContainerStaleLightHighContrast,
    doorOpenOnContainerFresh = doorOpenOnContainerFreshLightHighContrast,
    doorOpenOnContainerStale = doorOpenOnContainerStaleLightHighContrast,
    doorUnknownContainerFresh = doorUnknownContainerFreshLightHighContrast,
    doorUnknownContainerStale = doorUnknownContainerStaleLightHighContrast,
    doorUnknownOnContainerFresh = doorUnknownOnContainerFreshLightHighContrast,
    doorUnknownOnContainerStale = doorUnknownOnContainerStaleLightHighContrast,
)

val doorStatusMediumContrastDarkColorScheme = DoorStatusColorScheme(
    doorClosedContainerFresh = doorClosedContainerFreshDarkMediumContrast,
    doorClosedContainerStale = doorClosedContainerStaleDarkMediumContrast,
    doorClosedOnContainerFresh = doorClosedOnContainerFreshDarkMediumContrast,
    doorClosedOnContainerStale = doorClosedOnContainerStaleDarkMediumContrast,
    doorOpenContainerFresh = doorOpenContainerFreshDarkMediumContrast,
    doorOpenContainerStale = doorOpenContainerStaleDarkMediumContrast,
    doorOpenOnContainerFresh = doorOpenOnContainerFreshDarkMediumContrast,
    doorOpenOnContainerStale = doorOpenOnContainerStaleDarkMediumContrast,
    doorUnknownContainerFresh = doorUnknownContainerFreshDarkMediumContrast,
    doorUnknownContainerStale = doorUnknownContainerStaleDarkMediumContrast,
    doorUnknownOnContainerFresh = doorUnknownOnContainerFreshDarkMediumContrast,
    doorUnknownOnContainerStale = doorUnknownOnContainerStaleDarkMediumContrast,
)

val doorStatusHighContrastDarkColorScheme = DoorStatusColorScheme(
    doorClosedContainerFresh = doorClosedContainerFreshDarkHighContrast,
    doorClosedContainerStale = doorClosedContainerStaleDarkHighContrast,
    doorClosedOnContainerFresh = doorClosedOnContainerFreshDarkHighContrast,
    doorClosedOnContainerStale = doorClosedOnContainerStaleDarkHighContrast,
    doorOpenContainerFresh = doorOpenContainerFreshDarkHighContrast,
    doorOpenContainerStale = doorOpenContainerStaleDarkHighContrast,
    doorOpenOnContainerFresh = doorOpenOnContainerFreshDarkHighContrast,
    doorOpenOnContainerStale = doorOpenOnContainerStaleDarkHighContrast,
    doorUnknownContainerFresh = doorUnknownContainerFreshDarkHighContrast,
    doorUnknownContainerStale = doorUnknownContainerStaleDarkHighContrast,
    doorUnknownOnContainerFresh = doorUnknownOnContainerFreshDarkHighContrast,
    doorUnknownOnContainerStale = doorUnknownOnContainerStaleDarkHighContrast,
)

fun DoorEvent?.toColorStatus(): DoorColorStatus {
    return when {
        this == null -> DoorColorStatus.UNKNOWN
        this.doorPosition == DoorPosition.CLOSED -> DoorColorStatus.CLOSED
        else -> DoorColorStatus.OPEN
    }
}

fun DoorEvent?.isFresh(now: Instant, age: Duration): Boolean {
    return this?.lastCheckInTimeSeconds?.let { utcSeconds ->
        val limit = now.minusSeconds(age.inWholeSeconds)
        Instant.ofEpochSecond(utcSeconds).isAfter(limit)
    } ?: false
}

fun doorCardColors(doorColors: DoorStatusColorScheme, doorEvent: DoorEvent?): CardColors {
    val status = doorEvent.toColorStatus()
    val fresh = doorEvent.isFresh(Instant.now(), 15.minutes)
    return CardColors(
        containerColor = doorColors
            .select(status = status, container = true, fresh = fresh),
        contentColor = doorColors
            .select(status = status, container = false, fresh = fresh),
        disabledContainerColor = doorColors
            .select(status = status, container = true, fresh = false),
        disabledContentColor = doorColors
            .select(status = status, container = false, fresh = false),
    )
}
