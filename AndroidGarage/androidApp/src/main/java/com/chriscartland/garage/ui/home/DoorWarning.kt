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

package com.chriscartland.garage.ui.home

/**
 * Typed warning surfaced inside the Home tab's Status card for stuck or
 * anomalous door states. Replaces the previous `String?` field on
 * [HomeStatusDisplay] (Phase 2A of the string-resource migration plan
 * in `AndroidGarage/docs/PENDING_FOLLOWUPS.md` item #1).
 *
 * The mapper produces a typed value; the Composable resolves it to a
 * localized string at render time via `stringResource(...)`. This keeps
 * [HomeMapper] pure-function unit-testable without `Context` /
 * `Resources`, lets tests assert on type rather than text (so a copy
 * revision doesn't break the mapper test), and unblocks future
 * localization.
 *
 * Two shapes:
 * - [ServerMessage]: server-supplied warning text — passed through
 *   verbatim because the server's own message is intended to be more
 *   specific than any hardcoded fallback.
 * - The four fallback `data object` variants: used when the server
 *   provides no message for a known anomalous state. Each maps to a
 *   string resource via [warningTextRes].
 */
sealed interface DoorWarning {
    /** Server-supplied warning text — render verbatim, no localization. */
    data class ServerMessage(
        val text: String,
    ) : DoorWarning

    /** Fallback for [com.chriscartland.garage.domain.model.DoorPosition.OPENING_TOO_LONG]. */
    data object OpeningTooLong : DoorWarning

    /** Fallback for [com.chriscartland.garage.domain.model.DoorPosition.CLOSING_TOO_LONG]. */
    data object ClosingTooLong : DoorWarning

    /** Fallback for [com.chriscartland.garage.domain.model.DoorPosition.OPEN_MISALIGNED]. */
    data object OpenMisaligned : DoorWarning

    /** Fallback for [com.chriscartland.garage.domain.model.DoorPosition.ERROR_SENSOR_CONFLICT]. */
    data object SensorConflict : DoorWarning
}
