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

package com.chriscartland.garage.domain.model

/**
 * How long the garage may go without reporting before a reading is STALE.
 *
 * ONE number, read by everything that judges staleness:
 *
 * - `CheckInStatusMapper.STALE_THRESHOLD_SECONDS` (presentation-model) — the
 *   pill, the settle window's verdict, every glance surface.
 * - `CheckInStalenessManager.CHECK_IN_STALE_THRESHOLD_SECONDS` (usecase) — the
 *   phone's reactive staleness flag.
 * - The server's `doorCommand` gate (`DoorCommandGate.ts`) and the shared
 *   fixture `wire-contracts/doorCommand/verdict_table.json`
 *   (`staleThresholdSeconds`), which `CheckInStalenessThresholdTest` reads.
 *
 * It lives in `:domain` rather than `presentation-model` because `:usecase`
 * does not depend on `presentation-model`; before strategy 4.3 the Kotlin
 * copies agreed by coincidence and nothing bound either to the fixture.
 */
object CheckInStaleness {
    const val THRESHOLD_SECONDS: Long = 11L * 60
}
