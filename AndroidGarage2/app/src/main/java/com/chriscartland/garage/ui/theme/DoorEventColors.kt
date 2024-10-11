package com.chriscartland.garage.ui.theme

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardColors
import com.chriscartland.garage.model.DoorEvent
import com.chriscartland.garage.model.DoorPosition
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

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

fun doorButtonColors(doorColors: DoorStatusColorScheme, doorEvent: DoorEvent?): ButtonColors {
    val status = doorEvent.toColorStatus()
    val fresh = doorEvent.isFresh(Instant.now(), 15.minutes)
    return ButtonColors(
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
