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

package com.chriscartland.garage.datalocal

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition

/**
 * Room entity for storing door events in the local database.
 *
 * This is the persistence representation of [DoorEvent].
 * Use [toDomain] and [DoorEvent.toEntity] to convert between them.
 *
 * The table name "DoorEvent" matches the previous @Entity class name
 * to avoid a Room schema migration.
 */
@Entity(tableName = "DoorEvent")
data class DoorEventEntity(
    val doorPosition: DoorPosition? = null,
    val message: String? = null,
    val lastCheckInTimeSeconds: Long? = null,
    val lastChangeTimeSeconds: Long? = null,
    @PrimaryKey val id: String =
        lastChangeTimeSeconds.toString() + ":" + (doorPosition ?: DoorPosition.UNKNOWN),
)

fun DoorEventEntity.toDomain(): DoorEvent =
    DoorEvent(
        doorPosition = doorPosition,
        message = message,
        lastCheckInTimeSeconds = lastCheckInTimeSeconds,
        lastChangeTimeSeconds = lastChangeTimeSeconds,
    )

fun DoorEvent.toEntity(): DoorEventEntity =
    DoorEventEntity(
        doorPosition = doorPosition,
        message = message,
        lastCheckInTimeSeconds = lastCheckInTimeSeconds,
        lastChangeTimeSeconds = lastChangeTimeSeconds,
    )
