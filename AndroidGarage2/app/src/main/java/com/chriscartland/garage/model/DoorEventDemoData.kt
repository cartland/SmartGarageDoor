package com.chriscartland.garage.model

import java.time.Instant
import kotlin.random.Random

fun generateDoorEventDemoData(numEvents: Int = 10): List<DoorEvent> {
    val currentTimeSeconds = Instant.now().epochSecond

    return (1..numEvents).map { index ->
        val doorPosition = DoorPosition.entries.toTypedArray().random()
        val message = when (doorPosition) {
            DoorPosition.UNKNOWN -> "Unknown event"
            DoorPosition.CLOSED -> "The door is closed."
            DoorPosition.OPENING -> "The door is opening."
            DoorPosition.OPENING_TOO_LONG -> "The door has been opening too long."
            DoorPosition.OPEN -> "The door is open."
            DoorPosition.OPEN_MISALIGNED -> "The door is open but misaligned."
            DoorPosition.CLOSING -> "The door is closing."
            DoorPosition.CLOSING_TOO_LONG -> "The door has been closing too long."
            DoorPosition.ERROR_SENSOR_CONFLICT -> "Sensor conflict detected."
        }

        DoorEvent(
            doorPosition = doorPosition,
            message = message,
            lastCheckInTimeSeconds = currentTimeSeconds - Random.nextLong(
                1000,
                10000
            ), // Random time in the past
            lastChangeTimeSeconds = currentTimeSeconds - Random.nextLong(
                500,
                5000
            ), // Usually slightly before check-in
        )
    }
}