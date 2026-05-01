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

import co.touchlab.kermit.Logger
import com.chriscartland.garage.domain.coroutines.AppClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * App-scoped live clock that emits the current wall-clock time at a fixed
 * cadence. ViewModels combine this with their domain flows to drive
 * "Since X · Y" / "N seconds ago" durations, replacing per-Composable
 * tickers (`rememberLiveNow()`) that lived in the UI layer.
 *
 * Why a UseCase: time-driven UI updates are a side effect with a lifecycle.
 * The previous pattern spawned a `produceState` coroutine inside each
 * `@Composable` and was duplicated across files (Home, History). Centralizing
 * the tick keeps the clock consistent across screens, lets tests advance time
 * deterministically (via [AppClock] + a test dispatcher), and removes
 * `LaunchedEffect`-shaped concerns from the UI layer.
 *
 * Per ADR-019, the ticker runs on `externalScope` so it survives
 * configuration changes and screen pauses.
 *
 * The default tick interval is 1s. The "Since X · Y" line and the device
 * heartbeat row both display single-second granularity in the leading
 * bucket ("12 sec ago", "Since 9:47 AM · 3 sec"); a 10s tick made those
 * counters jump in 10-second steps and felt frozen. Mappers produce stable
 * strings, so 1s ticks that don't change the string are no-ops in Compose
 * (StateFlow's equality dedup).
 */
interface LiveClock {
    /**
     * Current wall-clock time as epoch seconds, ticking on the configured
     * cadence. Always emits the most recent value to new subscribers.
     */
    val nowEpochSeconds: StateFlow<Long>

    /**
     * Begin ticking. Idempotent — calling twice is a no-op. Typically called
     * from `AppStartup` so the clock runs for the lifetime of the process.
     */
    fun start()
}

class DefaultLiveClock(
    private val clock: AppClock,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val intervalMillis: Long = DEFAULT_TICK_INTERVAL_MS,
) : LiveClock {
    private val _nowEpochSeconds = MutableStateFlow(clock.nowEpochSeconds())
    override val nowEpochSeconds: StateFlow<Long> = _nowEpochSeconds

    private var tickJob: Job? = null

    override fun start() {
        if (tickJob?.isActive == true) {
            Logger.d { "LiveClock: already running" }
            return
        }
        tickJob = scope.launch(dispatcher) {
            while (true) {
                delay(intervalMillis)
                _nowEpochSeconds.value = clock.nowEpochSeconds()
            }
        }
    }

    companion object {
        /** 1 second — matches the per-Composable tickers it replaced. */
        const val DEFAULT_TICK_INTERVAL_MS = 1_000L
    }
}
