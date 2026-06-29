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

package com.chriscartland.garage.usecase

import com.chriscartland.garage.domain.model.AppLogCsv
import com.chriscartland.garage.domain.repository.AppLoggerRepository
import kotlinx.coroutines.flow.firstOrNull

/**
 * Produces the app-log export CSV as a string — the current snapshot of logged
 * events (ordered ascending) rendered via the shared [AppLogCsv] builder.
 *
 * The ViewModel exposes this (ADR-033: the Diagnostics UI routes the export
 * through the VM, never reaching the repository directly); the platform UI then
 * does only the irreducible side-effect — Android writes the bytes to a chosen
 * content `Uri`, iOS shares the file. The format is identical on both platforms.
 */
class BuildAppLogCsvUseCase(
    private val appLoggerRepository: AppLoggerRepository,
) {
    suspend operator fun invoke(): String = AppLogCsv.build(appLoggerRepository.getAll().firstOrNull() ?: emptyList())
}
