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

import co.touchlab.kermit.Logger
import com.chriscartland.garage.domain.model.AppConfig
import com.chriscartland.garage.domain.model.DoorUpdateStrategyId
import com.chriscartland.garage.domain.repository.AppSettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * App-scoped owner of the one running [DoorUpdateStrategy] (ADR-015).
 *
 * Reads the developer override
 * ([AppSettingsRepository.doorUpdateStrategy]), resolves it against this
 * build's [AppConfig.defaultDoorUpdateStrategy], and runs the matching
 * strategy — swapping live when the setting changes. `collectLatest` is
 * the whole swap mechanism: a new value cancels the running strategy's
 * coroutine, and structured concurrency tears down its timers, its
 * in-flight fetch, and its visibility collector with it. There is no
 * `stop()` to forget to call and no window where two strategies race to
 * write the same cache.
 *
 * Every strategy is injected, not constructed here, so the set of them is
 * visible in one place per platform (`AppComponent` / `NativeComponent`)
 * and a test can substitute any of them.
 *
 * **Not a data-graph node, and not a new door-event writer.** The
 * strategies end at `DoorRepository.fetchCurrentDoorEvent()` — the same
 * app-initiated fetch `InitialDoorFetchManager` and pull-to-refresh
 * already make. `currentDoorEvent` keeps `Cadence.PUSH`: the annotation
 * names what makes a node change *behind the app's back*, and fetches
 * this app itself issues have never changed it (see `Cadence.USER_ACTION`
 * — "a user action or an app-initiated fetch"). Relabelling it `POLL`
 * would also be wrong mechanically: `POLL` in `DataGraph` means a
 * subscriber-held collection loop that gating can pause, and it would
 * force `Sharing.Gated` on every derived node downstream (G4) to pause a
 * loop that no subscriber holds open.
 */
class DoorUpdateManager(
    private val pushStrategy: PushDoorUpdateStrategy,
    private val pollStrategy: PollDoorUpdateStrategy,
    private val pushWithForegroundRefreshStrategy: PushWithForegroundRefreshDoorUpdateStrategy,
    private val appSettings: AppSettingsRepository,
    private val appConfig: AppConfig,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
) {
    private val _activeStrategy = MutableStateFlow(appConfig.defaultDoorUpdateStrategy)

    /**
     * Which strategy is running right now. Seeded with the build default
     * rather than a null/unknown so a reader never has to render "we don't
     * know yet" for the one frame before DataStore's first emission — the
     * seeded value is what runs unless an override says otherwise.
     *
     * Nothing renders this yet — the Settings picker shows the stored
     * OVERRIDE, which is the different question of what was chosen. This
     * is the resolved answer, and it is what a Diagnostics row should show
     * so "why isn't this updating?" can be read rather than inferred.
     */
    val activeStrategy: StateFlow<DoorUpdateStrategyId> = _activeStrategy

    private var job: Job? = null

    /**
     * Begin honoring the setting. Idempotent — a second call while the
     * first is running is a no-op, so `MainActivity.onCreate` firing again
     * (rotation, Activity restart) cannot start a second strategy.
     */
    fun start() {
        if (job?.isActive == true) {
            Logger.d { "DoorUpdateManager: already started" }
            return
        }
        job = scope.launch(dispatcher) {
            appSettings.doorUpdateStrategy.flow
                .map { it.resolve(appConfig.defaultDoorUpdateStrategy) }
                .distinctUntilChanged()
                .collectLatest { id ->
                    val strategy = strategyFor(id)
                    _activeStrategy.value = id
                    Logger.i { "doorUpdateStrategy <- $id" }
                    strategy.run()
                }
        }
    }

    private fun strategyFor(id: DoorUpdateStrategyId): DoorUpdateStrategy =
        when (id) {
            DoorUpdateStrategyId.PUSH -> pushStrategy
            DoorUpdateStrategyId.POLL -> pollStrategy
            DoorUpdateStrategyId.PUSH_WITH_FOREGROUND_REFRESH -> pushWithForegroundRefreshStrategy
        }
}
