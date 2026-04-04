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

import com.chriscartland.garage.domain.repository.DoorRepository
import javax.inject.Inject

/**
 * Fetches the current door event from the network.
 *
 * Delegates to [DoorRepository.fetchCurrentDoorEvent]. The repository handles
 * server config lookup, network requests, and local database insertion.
 */
class FetchCurrentDoorEventUseCase
    @Inject
    constructor(
        private val doorRepository: DoorRepository,
    ) {
        suspend operator fun invoke() {
            doorRepository.fetchCurrentDoorEvent()
        }
    }

/**
 * Fetches recent door events from the network.
 *
 * Delegates to [DoorRepository.fetchRecentDoorEvents]. The repository handles
 * server config lookup, network requests, and local database replacement.
 */
class FetchRecentDoorEventsUseCase
    @Inject
    constructor(
        private val doorRepository: DoorRepository,
    ) {
        suspend operator fun invoke() {
            doorRepository.fetchRecentDoorEvents()
        }
    }
