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

package com.chriscartland.garage.ui.history

/**
 * Typed kind of door-history anomaly. Replaces the previous
 * `title: String` field on `HistoryEntry.Anomaly`.
 *
 * Phase 2E of the string-resource migration plan
 * (`AndroidGarage/docs/PENDING_FOLLOWUPS.md` item #1) — `HistoryMapper`
 * emits a typed `kind`, the Composable resolves to a localized string
 * via `stringResource(...)` at render time.
 */
sealed interface AnomalyKind {
    /** [com.chriscartland.garage.domain.model.DoorPosition.ERROR_SENSOR_CONFLICT]. */
    data object SensorConflict : AnomalyKind

    /** [com.chriscartland.garage.domain.model.DoorPosition.UNKNOWN]. */
    data object UnknownState : AnomalyKind

    /** `OPENING_TOO_LONG` that never reached its terminal `OPEN`. */
    data object StuckOpening : AnomalyKind

    /** `CLOSING_TOO_LONG` that never reached its terminal `CLOSED`. */
    data object StuckClosing : AnomalyKind

    /**
     * `OPEN_MISALIGNED` arriving without a prior `Opening` and with no
     * recent `Opened` to attach to — surfaced as a standalone anomaly
     * so the data isn't dropped. Distinct from the `misaligned` flag
     * on a normal `Opened` row.
     */
    data object OpenMisaligned : AnomalyKind
}
