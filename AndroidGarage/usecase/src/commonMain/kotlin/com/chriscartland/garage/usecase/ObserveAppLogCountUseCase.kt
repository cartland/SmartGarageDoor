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

import com.chriscartland.garage.domain.repository.DiagnosticsCountersRepository
import kotlinx.coroutines.flow.Flow

/**
 * Observes the lifetime count of logged events for a given key.
 *
 * Backed by the Diagnostics DataStore (see
 * [DiagnosticsCountersRepository]) — a monotonic counter independent
 * of the Room app-event log's per-key row cap. The Diagnostics screen
 * reads from this so users see lifetime totals like "FCM received:
 * 47,231" instead of a number that plateaus at 1000 once the Room
 * buffer fills.
 */
class ObserveAppLogCountUseCase(
    private val diagnosticsCounters: DiagnosticsCountersRepository,
) {
    operator fun invoke(key: String): Flow<Long> = diagnosticsCounters.observeCount(key)
}
