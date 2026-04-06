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
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.FetchError
import com.chriscartland.garage.domain.repository.DoorRepository

/**
 * Fetches the current door event from the network.
 *
 * Returns [AppResult] so callers can handle [FetchError.NotReady] and
 * [FetchError.NetworkFailed] explicitly with exhaustive `when`.
 */
class FetchCurrentDoorEventUseCase(
    private val doorRepository: DoorRepository,
) {
    suspend operator fun invoke(): AppResult<DoorEvent, FetchError> = doorRepository.fetchCurrentDoorEvent()
}

/**
 * Fetches recent door events from the network.
 *
 * Returns [AppResult] so callers can handle [FetchError.NotReady] and
 * [FetchError.NetworkFailed] explicitly with exhaustive `when`.
 */
class FetchRecentDoorEventsUseCase(
    private val doorRepository: DoorRepository,
) {
    suspend operator fun invoke(): AppResult<List<DoorEvent>, FetchError> = doorRepository.fetchRecentDoorEvents()
}
