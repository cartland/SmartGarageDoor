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

package com.chriscartland.garage.presentation

import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition

/**
 * Fixed timestamp for deterministic previews and screenshot tests.
 * Never use Clock/Instant.now() in preview data — it causes screenshot diffs on every run.
 *
 * Equivalent to Instant.parse("2026-01-15T12:00:00Z").epochSecond
 */
private const val DEMO_TIMESTAMP = 1768507200L

val demoDoorEvents = generateDoorEventDemoData()

fun generateDoorEventDemoData(numEvents: Int = 10): List<DoorEvent> {
    val positions = listOf(
        DoorPosition.CLOSED,
        DoorPosition.OPEN,
        DoorPosition.OPENING,
        DoorPosition.CLOSING,
        DoorPosition.CLOSED,
        DoorPosition.OPEN_MISALIGNED,
        DoorPosition.OPENING_TOO_LONG,
        DoorPosition.CLOSING_TOO_LONG,
        DoorPosition.ERROR_SENSOR_CONFLICT,
        DoorPosition.UNKNOWN,
    )

    return (0 until numEvents).map { index ->
        val doorPosition = positions[index % positions.size]
        val message =
            when (doorPosition) {
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
            lastCheckInTimeSeconds = DEMO_TIMESTAMP - (index + 1) * 600L,
            lastChangeTimeSeconds = DEMO_TIMESTAMP - (index + 1) * 300L,
        )
    }
}
