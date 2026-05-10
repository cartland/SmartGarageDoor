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

import co.touchlab.kermit.Logger
import com.chriscartland.garage.domain.model.ButtonHealth
import com.chriscartland.garage.domain.model.ButtonHealthState

/**
 * Parses button-health FCM data payloads into [ButtonHealth].
 *
 * Wire shape (data-only, see ButtonHealthFCM.ts on the server):
 *   buttonState: "ONLINE" | "OFFLINE"     // UNKNOWN never sent over FCM
 *   stateChangedAtSeconds: "<number>"     // FCM data values are strings
 *   buildTimestamp: "<original buildTimestamp>"
 *   lastPollAtSeconds: "<number>"         // optional — omitted by server when null
 *
 * Forward-compat: unrecognized server-side state strings (e.g. a
 * future `MAINTENANCE`) deserialize to [ButtonHealthState.UNKNOWN]
 * rather than throwing. Old clients keep working when the server adds
 * new states. Missing `lastPollAtSeconds` (older server) decodes to
 * `ButtonHealth.lastPollAtSeconds = null`.
 */
object ButtonHealthFcmPayloadParser {
    fun parse(data: Map<String, String>): ButtonHealth? {
        try {
            val state = data["buttonState"]?.toButtonHealthState() ?: return null
            val stateChangedAtSeconds = data["stateChangedAtSeconds"]?.toLongOrNull()
                ?: return null
            val lastPollAtSeconds = data["lastPollAtSeconds"]?.toLongOrNull()
            return ButtonHealth(
                state = state,
                stateChangedAtSeconds = stateChangedAtSeconds,
                lastPollAtSeconds = lastPollAtSeconds,
            )
        } catch (e: Exception) {
            Logger.e { "Error parsing button-health FCM payload: $e" }
            return null
        }
    }
}

private fun String.toButtonHealthState(): ButtonHealthState =
    when (this) {
        "ONLINE" -> ButtonHealthState.ONLINE
        "OFFLINE" -> ButtonHealthState.OFFLINE
        "UNKNOWN" -> ButtonHealthState.UNKNOWN
        else -> ButtonHealthState.UNKNOWN
    }
