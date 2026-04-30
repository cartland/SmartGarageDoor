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
import com.chriscartland.garage.usecase.ReceiveFcmDoorEventUseCase

/**
 * Handles FCM door event messages. Extracted from FCMService for testability.
 *
 * Parses the FCM data payload and routes the resulting DoorEvent to a UseCase
 * that owns persistence + logging — this thin handler is just the parser.
 * Returns true if the event was successfully parsed and handed off.
 */
class FcmMessageHandler(
    private val receiveFcmDoorEvent: ReceiveFcmDoorEventUseCase,
) {
    /**
     * Process an FCM data message. Returns true if a DoorEvent was parsed and
     * forwarded to [ReceiveFcmDoorEventUseCase].
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
        receiveFcmDoorEvent(doorEvent)
        return true
    }
}
