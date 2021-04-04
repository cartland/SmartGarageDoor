/*
 * Copyright 2021 Chris Cartland. All rights reserved.
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

package com.chriscartland.garage

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot

data class DoorData(
    val state: DoorState? = null,
    val message: String? = null,
    val lastCheckInTimeSeconds: Long? = null,
    val lastChangeTimeSeconds: Long? = null
)

enum class DoorState {
    UNKNOWN,
    CLOSED,
    OPENING,
    OPENING_TOO_LONG,
    OPEN,
    CLOSING,
    CLOSING_TOO_LONG,
    ERROR_SENSOR_CONFLICT
}

fun DocumentSnapshot.toDoorData(): DoorData {
    val data = this.data as? Map<*, *> ?: return DoorData()
    val currentEvent = data["currentEvent"] as? Map<*, *>
    val type = currentEvent?.get("type") as? String ?: ""
    val state = try {
        DoorState.valueOf(type)
    } catch (e: IllegalArgumentException) {
        DoorState.UNKNOWN
    }
    val message = currentEvent?.get("message") as? String ?: ""
    val timestampSeconds = currentEvent?.get("timestampSeconds") as? Long?
    val lastCheckInTime = data["FIRESTORE_databaseTimestampSeconds"] as? Long?
    return DoorData(
        state = state,
        message = message,
        lastChangeTimeSeconds = timestampSeconds,
        lastCheckInTimeSeconds = lastCheckInTime
    )
}

fun getStatusTitleAndColor(doorData: DoorData, context: Context): Pair<String, Int> {
    Log.d(MainActivity.TAG, "getStatusTitleAndColor")
    return when (doorData.state) {
        null -> Pair(
            "Unknown Status",
            context.getColor(R.color.color_door_error)
        )
        DoorState.UNKNOWN -> Pair(
            "Unknown Status",
            context.getColor(R.color.color_door_error)
        )
        DoorState.CLOSED -> Pair(
            "Door Closed",
            context.getColor(R.color.color_door_closed)
        )
        DoorState.OPENING -> Pair(
            "Opening...",
            context.getColor(R.color.color_door_moving)
        )
        DoorState.OPENING_TOO_LONG -> Pair(
            "Check door",
            context.getColor(R.color.color_door_error)
        )
        DoorState.OPEN -> Pair(
            "Door Open",
            context.getColor(R.color.color_door_open)
        )
        DoorState.CLOSING -> Pair(
            "Closing...",
            context.getColor(R.color.color_door_moving)
        )
        DoorState.CLOSING_TOO_LONG -> Pair(
            "Check door",
            context.getColor(R.color.color_door_error)
        )
        DoorState.ERROR_SENSOR_CONFLICT -> Pair(
            "Error",
            context.getColor(R.color.color_door_error)
        )
    }
}
