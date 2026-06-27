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

package com.chriscartland.garage.presentation

import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition

/**
 * Typed warning surfaced for stuck or anomalous door states.
 *
 * Shared presentation model (ADR-031): the shared layer emits a *typed*
 * value; each UI resolves it to a localized string at render time (Android
 * Compose `stringResource(...)`; SwiftUI per-locale formatters). Keeping it
 * typed lets the mapping be a pure, platform-free, unit-testable function in
 * `commonTest` — tests assert on type, so a copy revision doesn't break them
 * — and it unblocks localization on both platforms.
 *
 * Two shapes:
 * - [ServerMessage]: server-supplied warning text — passed through verbatim
 *   because the server's own message is intended to be more specific than any
 *   hardcoded fallback.
 * - The four fallback `data object` variants: used when the server provides no
 *   message for a known anomalous state. Each UI maps the variant to its own
 *   localized string.
 */
sealed interface DoorWarning {
    /** Server-supplied warning text — render verbatim, no localization. */
    data class ServerMessage(
        val text: String,
    ) : DoorWarning

    /** Fallback for [DoorPosition.OPENING_TOO_LONG]. */
    data object OpeningTooLong : DoorWarning

    /** Fallback for [DoorPosition.CLOSING_TOO_LONG]. */
    data object ClosingTooLong : DoorWarning

    /** Fallback for [DoorPosition.OPEN_MISALIGNED]. */
    data object OpenMisaligned : DoorWarning

    /** Fallback for [DoorPosition.ERROR_SENSOR_CONFLICT]. */
    data object SensorConflict : DoorWarning
}

/**
 * Pure mapping from a [DoorEvent] to the typed [DoorWarning] to surface in the
 * Home status card. Prefers the server-supplied message
 * ([DoorWarning.ServerMessage]); falls back to a typed enum case per
 * [DoorPosition] so each UI can render a localized string when the server
 * sends nothing for an anomalous state.
 *
 * Moved out of `androidApp`'s `HomeMapper` in the presentation-model
 * realization (ADR-031) so iOS and Android share one source of truth.
 */
object DoorWarningMapper {
    fun forEvent(event: DoorEvent?): DoorWarning? {
        if (event == null) return null
        val message = event.message?.takeIf { it.isNotBlank() }
        return when (event.doorPosition) {
            DoorPosition.OPENING_TOO_LONG ->
                message?.let(DoorWarning::ServerMessage) ?: DoorWarning.OpeningTooLong
            DoorPosition.CLOSING_TOO_LONG ->
                message?.let(DoorWarning::ServerMessage) ?: DoorWarning.ClosingTooLong
            DoorPosition.OPEN_MISALIGNED ->
                message?.let(DoorWarning::ServerMessage) ?: DoorWarning.OpenMisaligned
            DoorPosition.ERROR_SENSOR_CONFLICT ->
                message?.let(DoorWarning::ServerMessage) ?: DoorWarning.SensorConflict
            DoorPosition.UNKNOWN -> message?.let(DoorWarning::ServerMessage)
            else -> null
        }
    }
}
