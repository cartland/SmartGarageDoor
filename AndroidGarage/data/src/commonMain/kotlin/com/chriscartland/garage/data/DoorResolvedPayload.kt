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

package com.chriscartland.garage.data

/** Parsed content of a `door_open_v2-*` resolved-on-close FCM data payload. */
data class DoorResolvedContent(
    val openTimestampSeconds: Long,
    val closeTimestampSeconds: Long,
)

/**
 * Pure parser for the resolved-on-close FCM data payload sent by the server's
 * `ResolvedNotificationFCM` (kind `open_door_resolved`). The matching server
 * shape is pinned by `wire-contracts/openDoorResolved/payload_resolved.json`.
 *
 * Strict: returns null unless `kind == open_door_resolved` and both timestamps
 * are present and numeric. The `kind` gate is forward-compatible — the v2 topic
 * may later also carry an `open_door_warning` kind (Phase 2), which this parser
 * deliberately ignores.
 */
object DoorResolvedPayload {
    const val KIND_RESOLVED = "open_door_resolved"

    fun parse(data: Map<String, String>): DoorResolvedContent? {
        if (data["kind"] != KIND_RESOLVED) return null
        val open = data["openTimestampSeconds"]?.toLongOrNull() ?: return null
        val close = data["closeTimestampSeconds"]?.toLongOrNull() ?: return null
        return DoorResolvedContent(openTimestampSeconds = open, closeTimestampSeconds = close)
    }
}
