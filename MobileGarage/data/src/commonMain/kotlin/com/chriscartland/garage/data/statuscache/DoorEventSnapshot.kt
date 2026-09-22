/*
 * Copyright 2026 Chris Cartland. All rights reserved.
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

package com.chriscartland.garage.data.statuscache

import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import kotlinx.serialization.Serializable

/**
 * Persisted shape of the last-known [DoorEvent] (STATUS_CACHE_PLAN.md D1's
 * envelope, a new entry alongside D2–D4).
 *
 * `:domain` stays annotation-free, so the domain model gets a parallel
 * `@Serializable` DTO here. The position is stored by enum NAME and an
 * unrecognized stored name decodes to [DoorPosition.UNKNOWN] rather than
 * discarding the whole snapshot — the same forward-compat posture as the wire
 * decoder and as [ButtonHealthSnapshotDto].
 *
 * **Why the door needs a snapshot at all, when the phone has Room.** The
 * phone's door history IS this cache; the watch deliberately runs an
 * in-memory `LocalDoorDataSource` (no Room), so before this entry existed
 * every watch process started knowing nothing about the door. That is
 * invisible while the only reader is a screen the user just opened — a poll
 * lands within a second or two — and it is the whole problem for a reader
 * that renders when the app is NOT running, which is what a tile is.
 *
 * **[message] is deliberately not persisted.** It is the server's
 * human-readable note about the event, it is not rendered by any watch
 * surface, and a persisted free-text field is the one part of a door event
 * that could carry something unbounded onto disk. The three fields kept are
 * the ones a glance is made of: what the door was doing, when it last moved,
 * and when the garage last spoke.
 */
@Serializable
data class DoorEventSnapshotDto(
    val doorPosition: String? = null,
    val lastCheckInTimeSeconds: Long? = null,
    val lastChangeTimeSeconds: Long? = null,
) {
    fun toDomain(): DoorEvent =
        DoorEvent(
            doorPosition = doorPosition?.let { stored ->
                DoorPosition.entries.firstOrNull { it.name == stored } ?: DoorPosition.UNKNOWN
            },
            message = null,
            lastCheckInTimeSeconds = lastCheckInTimeSeconds,
            lastChangeTimeSeconds = lastChangeTimeSeconds,
        )

    companion object {
        fun fromDomain(doorEvent: DoorEvent): DoorEventSnapshotDto =
            DoorEventSnapshotDto(
                doorPosition = doorEvent.doorPosition?.name,
                lastCheckInTimeSeconds = doorEvent.lastCheckInTimeSeconds,
                lastChangeTimeSeconds = doorEvent.lastChangeTimeSeconds,
            )
    }
}

/** Cache identity + versioning for the last-known door event. */
object DoorEventSnapshot {
    val KEY = StatusCacheKey("doorEvent")

    /** Bump to deliberately invalidate all persisted door-event entries. */
    const val SCHEMA_VERSION: Int = 1
}
