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

/**
 * Trims the diagnostics log table so that no individual `eventKey`
 * retains more than [perKeyLimit] rows. One-shot startup cleanup for
 * databases that grew past the cap before the per-write cap was
 * introduced. The per-write cap (in [LogAppEventUseCase]) keeps
 * steady-state size bounded going forward.
 */
class PruneDiagnosticsLogUseCase(
    private val appLoggerRepository: AppLoggerRepository,
) {
    suspend operator fun invoke(perKeyLimit: Int) {
        appLoggerRepository.pruneToLimit(perKeyLimit)
    }
}
