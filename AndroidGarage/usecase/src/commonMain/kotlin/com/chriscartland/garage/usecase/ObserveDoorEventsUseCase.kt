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

import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.repository.DoorRepository
import kotlinx.coroutines.flow.Flow

/**
 * Observes the current and recent door events from the repository.
 *
 * Wraps [DoorRepository] flows so ViewModels can depend on this UseCase
 * instead of the repository directly.
 */
class ObserveDoorEventsUseCase(
    private val doorRepository: DoorRepository,
) {
    fun current(): Flow<DoorEvent?> = doorRepository.currentDoorEvent

    fun recent(): Flow<List<DoorEvent>> = doorRepository.recentDoorEvents

    /** Stream of door position changes — needed by ButtonStateMachine. */
    fun position(): Flow<DoorPosition> = doorRepository.currentDoorPosition
}
