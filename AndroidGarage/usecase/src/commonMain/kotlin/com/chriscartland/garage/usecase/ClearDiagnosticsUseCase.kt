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

package com.chriscartland.garage.usecase

import com.chriscartland.garage.domain.repository.AppLoggerRepository
import com.chriscartland.garage.domain.repository.DiagnosticsCountersRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Wipes user-visible diagnostics state. Triggered by the "Clear all
 * diagnostics" action behind a confirmation dialog.
 *
 *  - Deletes every row from the Room app-event log (CSV export will
 *    be empty until new events are logged).
 *  - Zeros every counter in the Diagnostics DataStore (lifetime totals
 *    on the Diagnostics screen go to 0).
 *
 * Other Room tables (door history) and other DataStore preferences
 * (snooze, FCM topic, etc.) are untouched.
 *
 * Both writes run inside a [NonCancellable] block so an Activity
 * tear-down between the two suspends cannot leave the system
 * half-cleared (Room empty + DataStore counters intact). Without
 * this, a half-clear would interact badly with the seed-from-Room
 * recovery: next launch's seed reads empty Room, sets the seeded
 * flag without touching the stale counter values, and the user's
 * Clear silently fails. Pre-existing bug from PR #663; surfaced by
 * the seed in PR #665.
 */
class ClearDiagnosticsUseCase(
    private val appLoggerRepository: AppLoggerRepository,
    private val diagnosticsCounters: DiagnosticsCountersRepository,
) {
    suspend operator fun invoke() {
        withContext(NonCancellable) {
            appLoggerRepository.deleteAll()
            diagnosticsCounters.resetAll()
        }
    }
}
