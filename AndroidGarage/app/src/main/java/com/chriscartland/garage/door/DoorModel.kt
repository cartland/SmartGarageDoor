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

package com.chriscartland.garage.door

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class DoorEvent(
    val doorPosition: DoorPosition? = null,
    val message: String? = null,
    val lastCheckInTimeSeconds: Long? = null,
    val lastChangeTimeSeconds: Long? = null,
    @PrimaryKey val id: String =
        lastChangeTimeSeconds.toString() + ":" + (doorPosition ?: DoorPosition.UNKNOWN),
)

// DO NOT CHANGE NAMES
// Must match server strings
enum class DoorPosition {
    UNKNOWN,
    CLOSED,
    OPENING,
    OPENING_TOO_LONG,
    OPEN,
    OPEN_MISALIGNED,
    CLOSING,
    CLOSING_TOO_LONG,
    ERROR_SENSOR_CONFLICT
}

enum class FcmRegistrationStatus {
    UNKNOWN,
    REGISTERED,
    NOT_REGISTERED,
}