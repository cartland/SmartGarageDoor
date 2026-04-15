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

import co.touchlab.kermit.Logger
import com.chriscartland.garage.data.FcmPayloadParser
import com.chriscartland.garage.domain.model.AppLoggerKeys
import com.chriscartland.garage.domain.repository.AppLoggerRepository
import com.chriscartland.garage.domain.repository.DoorRepository

/**
 * Handles FCM door event messages. Extracted from FCMService for testability.
 *
 * Parses the FCM data payload and inserts the resulting DoorEvent into the repository.
 * Returns true if the event was successfully parsed and inserted.
 */
class FcmMessageHandler(
    private val doorRepository: DoorRepository,
    private val appLoggerRepository: AppLoggerRepository,
) {
    /**
     * Process an FCM data message. Returns true if a DoorEvent was parsed and inserted.
     */
    suspend fun handleDoorMessage(data: Map<String, String>): Boolean {
        if (data.isEmpty()) {
            Logger.d { "Message data payload is empty" }
            return false
        }
        val doorEvent = FcmPayloadParser.parseDoorEvent(data) ?: run {
            Logger.e { "Failed to parse FCM payload: ${data.entries.joinToString()}" }
            return false
        }
        Logger.d { "DoorData: $doorEvent" }
        doorRepository.insertDoorEvent(doorEvent)
        appLoggerRepository.log(AppLoggerKeys.FCM_DOOR_RECEIVED)
        return true
    }
}
