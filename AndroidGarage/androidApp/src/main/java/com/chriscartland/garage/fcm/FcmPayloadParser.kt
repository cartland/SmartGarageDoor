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

package com.chriscartland.garage.fcm

import android.util.Log
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition

/**
 * Parses FCM data message payloads into domain objects.
 *
 * The server sends FCM data messages with string key/value pairs.
 * This parser converts them into typed domain objects.
 *
 * Required keys: "type", "timestampSeconds", "checkInTimestampSeconds"
 * Optional keys: "message" (defaults to "")
 */
object FcmPayloadParser {
    /**
     * Parse a door event from an FCM data payload.
     *
     * @param data The FCM data message payload (string key/value pairs)
     * @return The parsed [DoorEvent], or null if required fields are missing or malformed
     */
    fun parseDoorEvent(data: Map<String, String>): DoorEvent? {
        try {
            val type = data["type"] ?: return null
            val position = try {
                DoorPosition.valueOf(type)
            } catch (e: IllegalArgumentException) {
                DoorPosition.UNKNOWN
            }
            val message = data["message"] ?: ""
            val timestampSeconds = data["timestampSeconds"]?.toLongOrNull() ?: return null
            val checkInTimestampSeconds = data["checkInTimestampSeconds"]?.toLongOrNull()
                ?: return null
            return DoorEvent(
                doorPosition = position,
                message = message,
                lastChangeTimeSeconds = timestampSeconds,
                lastCheckInTimeSeconds = checkInTimestampSeconds,
            )
        } catch (e: Exception) {
            Log.e("FcmPayloadParser", "Error parsing DoorEvent: $e")
            return null
        }
    }
}
