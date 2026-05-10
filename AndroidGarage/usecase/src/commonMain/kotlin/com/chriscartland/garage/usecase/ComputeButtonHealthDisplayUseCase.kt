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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Combines auth state, the button-health snapshot, and the LiveClock
 * tick into a single [ButtonHealthDisplay] [StateFlow].
 *
 * Returns [StateFlow] so the Composable can read `.value` synchronously
 * on first composition (no `Loading` flicker on every fresh screen
 * entry). The `combine(...)` result is naturally a cold [Flow]; we
 * `stateIn(applicationScope, SharingStarted.Eagerly, Loading)` to
 * convert it into a hot [StateFlow] with a cached current value.
 *
 * `Eagerly` (not `WhileSubscribed`) is intentional: the upstream
 * sources are all `StateFlow`s + LiveClock ticks (cheap), so keeping
 * the combine running for the lifetime of the process costs nothing
 * meaningful and guarantees subsequent subscribers see the latest
 * value immediately. `WhileSubscribed(timeout)` would re-emit the
 * initial `Loading` value after the timeout window, reintroducing the
 * flicker on the next subscription.
 *
 * The provider must be `@Singleton`-scoped so the eager combine runs
 * exactly once per process — multiple instances would each spin up
 * their own combine and waste cycles. See `ComponentGraphTest` for the
 * `assertSame` identity check.
 *
 * Pure derivation lives in [ButtonHealthDisplayLogic.compute]; this
 * UseCase is the wiring + clock-tick combine + state caching.
 */
class ComputeButtonHealthDisplayUseCase(
    authRepository: AuthRepository,
    buttonHealthRepository: ButtonHealthRepository,
    liveClock: LiveClock,
    applicationScope: CoroutineScope,
) {
    private val state: StateFlow<ButtonHealthDisplay> =
        combine(
            authRepository.authState,
            buttonHealthRepository.buttonHealth,
            liveClock.nowEpochSeconds,
        ) { auth, health, now ->
            ButtonHealthDisplayLogic.compute(auth, health, now)
        }.stateIn(
            scope = applicationScope,
            started = SharingStarted.Eagerly,
            initialValue = ButtonHealthDisplay.Loading,
        )

    operator fun invoke(): StateFlow<ButtonHealthDisplay> = state
}
