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

import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.FetchError
import com.chriscartland.garage.domain.model.SnoozeState
import com.chriscartland.garage.domain.repository.SnoozeRepository

/**
 * Fetches the current snooze status from the server.
 *
 * The success value is also written to [SnoozeRepository.snoozeState] so
 * subscribers see the update via the flow. Returns [AppResult] so callers
 * can react to the typed failure path without observing an unrelated flow.
 */
class FetchSnoozeStatusUseCase(
    private val snoozeRepository: SnoozeRepository,
) {
    suspend operator fun invoke(): AppResult<SnoozeState, FetchError> = snoozeRepository.fetchSnoozeStatus()
}
