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

package com.chriscartland.garage.ui

import com.chriscartland.garage.door.DoorEvent
import com.chriscartland.garage.door.DoorPosition
import java.time.Instant
import kotlin.random.Random

val demoDoorEvents = generateDoorEventDemoData()

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
