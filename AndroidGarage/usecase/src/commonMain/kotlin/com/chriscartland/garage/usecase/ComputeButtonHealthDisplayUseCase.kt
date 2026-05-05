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

import com.chriscartland.garage.domain.repository.AuthRepository
import com.chriscartland.garage.domain.repository.ButtonHealthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Combines auth state, the button-health snapshot, and the LiveClock
 * tick into a single [ButtonHealthDisplay] flow.
 *
 * Returns [Flow] (not [kotlinx.coroutines.flow.StateFlow]) per ADR-022:
 * the consumer (Composable) collects via `collectAsStateWithLifecycle`
 * with an initial value, avoiding `stateIn(viewModelScope, ...)`.
 *
 * Pure derivation lives in [ButtonHealthDisplayLogic.compute]; this
 * UseCase is the wiring + clock-tick combine.
 */
class ComputeButtonHealthDisplayUseCase(
    private val authRepository: AuthRepository,
    private val buttonHealthRepository: ButtonHealthRepository,
    private val liveClock: LiveClock,
) {
    operator fun invoke(): Flow<ButtonHealthDisplay> =
        combine(
            authRepository.authState,
            buttonHealthRepository.buttonHealth,
            liveClock.nowEpochSeconds,
        ) { auth, health, now ->
            ButtonHealthDisplayLogic.compute(auth, health, now)
        }
}
