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
import kotlinx.coroutines.flow.StateFlow

/**
 * Observes the current and recent door events from the repository.
 *
 * Wraps [DoorRepository] flows so ViewModels can depend on this UseCase
 * instead of the repository directly.
 *
 * Per ADR-022 "match the upstream, never wrap": [current] returns
 * [StateFlow] because the repo's `currentDoorEvent` IS a [StateFlow]
 * — passing it through preserves the synchronous `.value` accessor
 * the VM needs to seed its initial loading-result with the cached
 * door event (otherwise the VM exposes `Loading(null)` for one frame
 * on first composition, the door icon maps that to UNKNOWN/MIDWAY,
 * and the next frame's `Complete(actualEvent)` triggers a visible
 * MIDWAY→actual door animation on every fresh screen entry).
 *
 * [recent] and [position] stay [Flow] — `recent` is a Room flow (not
 * a StateFlow upstream) and `position` does `.map.distinctUntilChanged()`
 * (a transformation, not a pass-through).
 */
class ObserveDoorEventsUseCase(
    private val doorRepository: DoorRepository,
) {
    fun current(): StateFlow<DoorEvent?> = doorRepository.currentDoorEvent

    fun recent(): Flow<List<DoorEvent>> = doorRepository.recentDoorEvents

    /** Stream of door position changes — needed by ButtonStateMachine. */
    fun position(): Flow<DoorPosition> = doorRepository.currentDoorPosition
}
