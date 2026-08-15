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
 */

package com.chriscartland.garage.viewmodel

import com.chriscartland.garage.usecase.CheckInStalenessManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Staleness as a settable flag (ADR-017 fake conventions).
 *
 * A screen ViewModel consumes only the observable boolean, so its tests
 * should not have to assemble the manager's lifecycle machinery — before
 * `CheckInStalenessManager` became an interface, each of these tests
 * constructed the real manager with an `ObserveDoorEventsUseCase` over a
 * repository, a `LogAppEventUseCase` over two more, a scope, a
 * dispatcher, and a clock, purely to obtain one `Boolean`.
 *
 * Tests that assert on the real staleness DERIVATION (thresholds,
 * transition logging, periodic re-evaluation) belong in
 * `CheckInStalenessManagerTest` against `DefaultCheckInStalenessManager`,
 * not here.
 */
class FakeCheckInStalenessManager(
    initiallyStale: Boolean = false,
) : CheckInStalenessManager {
    private val flow = MutableStateFlow(initiallyStale)

    override val isCheckInStale: StateFlow<Boolean> = flow

    /** True once [start] has been called — pins the AppStartup contract. */
    var started: Boolean = false
        private set

    override fun start() {
        started = true
    }

    /** Flip staleness the way the real manager would on a tick or a new event. */
    fun setStale(stale: Boolean) {
        flow.value = stale
    }
}
