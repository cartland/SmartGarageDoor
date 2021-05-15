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

package com.chriscartland.garage.model

import android.content.Context
import android.util.Log
import com.chriscartland.garage.MainActivity
import com.chriscartland.garage.R
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

fun getStatusTitleColorMap(context: Context): Map<DoorState, Pair<String, Int>> {
    Log.d(MainActivity.TAG, "getStatusTitleColorMap")
    return mapOf(
        DoorState.UNKNOWN to Pair(
            context.getString(R.string.title_door_error),
            context.getColor(R.color.color_door_error)
        ),
        DoorState.CLOSED to Pair(
            context.getString(R.string.title_door_closed),
            context.getColor(R.color.color_door_closed)
        ),
        DoorState.OPENING to Pair(
            context.getString(R.string.title_door_opening),
            context.getColor(R.color.color_door_opening)
        ),
        DoorState.OPENING_TOO_LONG to Pair(
            context.getString(R.string.title_door_opening_too_long),
            context.getColor(R.color.color_door_error)
        ),
        DoorState.OPEN to Pair(
            context.getString(R.string.title_door_open),
            context.getColor(R.color.color_door_open)
        ),
        DoorState.CLOSING to Pair(
            context.getString(R.string.title_door_closing),
            context.getColor(R.color.color_door_closing)
        ),
        DoorState.CLOSING_TOO_LONG to Pair(
            context.getString(R.string.title_door_closing_too_long),
            context.getColor(R.color.color_door_error)
        ),
        DoorState.ERROR_SENSOR_CONFLICT to Pair(
            context.getString(R.string.title_door_sensor_conflict),
            context.getColor(R.color.color_door_error)
        )
    )
}
