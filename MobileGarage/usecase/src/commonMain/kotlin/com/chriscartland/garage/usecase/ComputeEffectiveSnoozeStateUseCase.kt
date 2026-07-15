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

import com.chriscartland.garage.domain.model.SnoozeState
import com.chriscartland.garage.domain.repository.SnoozeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Pure expiry flip: a snooze whose end time has passed reads as
 * NotSnoozing. Named object per ADR-009.
 */
object SnoozeStateExpiry {
    fun effective(
        state: SnoozeState,
        nowEpochSeconds: Long,
    ): SnoozeState =
        if (state is SnoozeState.Snoozing && state.untilEpochSeconds <= nowEpochSeconds) {
            SnoozeState.NotSnoozing
        } else {
            state
        }
}

/**
 * The snooze state as the USER should see it: the repository's
 * authoritative value with the expiry flip applied live against
 * [LiveClock] (STATUS_CACHE_PLAN.md D3).
 *
 * Why this exists: the repository only recomputes `Snoozing` vs
 * `NotSnoozing` at fetch/submit/hydration time. The deleted Android
 * 60s poll was papering over that on one platform; iOS never had it,
 * so an on-screen "Snoozing until 3 PM" survived past 3 PM until the
 * next manual refresh. Deriving the flip in commonMain fixes both
 * platforms with zero platform code.
 *
 * Same shape as [ComputeButtonHealthDisplayUseCase]:
 * `stateIn(applicationScope, Eagerly)` so a fresh subscriber reads a
 * synchronously-correct `.value`; the initial value applies the same
 * flip to the repo's current value so there is no wrong first frame.
 * The provider must be singleton-scoped so the eager combine runs once
 * per process (`ComponentGraphTest`/`NativeComponentTest` pin it).
 */
class ComputeEffectiveSnoozeStateUseCase(
    snoozeRepository: SnoozeRepository,
    liveClock: LiveClock,
    applicationScope: CoroutineScope,
) {
    private val state: StateFlow<SnoozeState> =
        combine(
            snoozeRepository.snoozeState,
            liveClock.nowEpochSeconds,
        ) { snooze, now ->
            SnoozeStateExpiry.effective(snooze, now)
        }.stateIn(
            scope = applicationScope,
            started = SharingStarted.Eagerly,
            initialValue = SnoozeStateExpiry.effective(
                snoozeRepository.snoozeState.value,
                liveClock.nowEpochSeconds.value,
            ),
        )

    operator fun invoke(): StateFlow<SnoozeState> = state
}
