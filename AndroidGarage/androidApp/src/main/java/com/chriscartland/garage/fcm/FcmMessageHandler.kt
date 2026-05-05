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
import com.chriscartland.garage.data.ButtonHealthFcmPayloadParser
import com.chriscartland.garage.data.FcmPayloadParser
import com.chriscartland.garage.usecase.ApplyButtonHealthFcmUseCase
import com.chriscartland.garage.usecase.ReceiveFcmDoorEventUseCase

/**
 * Handles FCM data messages. Extracted from FCMService for testability.
 *
 * Dispatches by topic prefix:
 *  - `buttonHealth-*` → [ApplyButtonHealthFcmUseCase]
 *  - everything else → door-event parser (default-preserving — the
 *    existing door FCM path stays exactly as it was)
 *
 * Each branch parses the data payload and routes the result to the
 * relevant UseCase. The handler itself owns no state.
 */
class FcmMessageHandler(
    private val receiveFcmDoorEvent: ReceiveFcmDoorEventUseCase,
    private val applyButtonHealthFcm: ApplyButtonHealthFcmUseCase,
) {
    /**
     * Process an FCM data message. Topic prefix determines which
     * payload parser + UseCase handles the message.
     *
     * @param topic the FCM topic the message was published to (without
     *   the `/topics/` prefix). Used to route between door and button-
     *   health channels.
     * @param data the FCM data payload (string key/value pairs).
     * @return true if the message was successfully parsed and handed off.
     */
    suspend fun handleMessage(
        topic: String,
        data: Map<String, String>,
    ): Boolean {
        if (data.isEmpty()) {
            Logger.d { "Message data payload is empty" }
            return false
        }
        return if (topic.startsWith("buttonHealth-")) {
            handleButtonHealthMessage(data)
        } else {
            handleDoorMessage(data)
        }
    }

    private suspend fun handleDoorMessage(data: Map<String, String>): Boolean {
        val doorEvent = FcmPayloadParser.parseDoorEvent(data) ?: run {
            Logger.e { "Failed to parse FCM door payload: ${data.entries.joinToString()}" }
            return false
        }
        Logger.d { "DoorData: $doorEvent" }
        receiveFcmDoorEvent(doorEvent)
        return true
    }

    private fun handleButtonHealthMessage(data: Map<String, String>): Boolean {
        val update = ButtonHealthFcmPayloadParser.parse(data) ?: run {
            Logger.e { "Failed to parse FCM button-health payload: ${data.entries.joinToString()}" }
            return false
        }
        Logger.d { "ButtonHealth FCM: $update" }
        applyButtonHealthFcm(update)
        return true
    }
}
