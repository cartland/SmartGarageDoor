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
import com.chriscartland.garage.domain.graph.DataGraph.Cadence
import com.chriscartland.garage.domain.graph.NodeCadence
import com.chriscartland.garage.domain.model.AppLoggerKeys
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * App-scoped manager for door check-in staleness (ADR-015, ADR-017).
 *
 * Computes whether the last door check-in is older than
 * [CHECK_IN_STALE_THRESHOLD_SECONDS]. Re-evaluates on every door event
 * change (reactive) AND on a fixed interval (in case clock time passes
 * the threshold without new events).
 *
 * An INTERFACE with a `Default*` implementation, per the repo's
 * "no concrete class for testability" rule and mirroring [LiveClock],
 * the other ViewModel-visible ADR-015 manager. A screen ViewModel needs
 * only the observable [isCheckInStale] boolean; before the split, every
 * ViewModel test had to construct the real manager with five
 * collaborators (an `ObserveDoorEventsUseCase` over a repository, a
 * `LogAppEventUseCase` over two more, a scope, a dispatcher, and a
 * clock) to obtain it. The interface is the seam that keeps that
 * lifecycle machinery out of tests that only care about the flag.
 *
 * Status is observable via [isCheckInStale]; [start] owns the jobs.
 */
interface CheckInStalenessManager {
    /**
     * Observable staleness. [StateFlow], not a plain flow
     * (DATA_CACHING_STRATEGY P3 / T1, ADR-015 amendment): this status
     * always has a value, and narrowing it destroyed `.value` — which
     * forced every consumer into a default-seeded mirror that rendered
     * a wrong first frame on each fresh screen entry. Consumers pass
     * the reference through (ADR-022); a pass-through cannot drift.
     */
    @NodeCadence(Cadence.CLOCK)
    val isCheckInStale: StateFlow<Boolean>

    /**
     * Start staleness evaluation. Idempotent — calling twice doesn't
     * create duplicate jobs. Typically called from `AppStartup`.
     */
    fun start()

    companion object {
        /**
         * 11 minutes — matches the previous OldLastCheckInBanner threshold.
         * Lives on the interface because it is part of the contract: the
         * presentation layer documents the same threshold
         * (`CheckInStatusMapper.STALE_THRESHOLD_SECONDS`), and the server's
         * `doorCommand` gate mirrors it.
         */
        const val CHECK_IN_STALE_THRESHOLD_SECONDS = 11L * 60

        /** Re-evaluate staleness every 30 seconds (catches clock drift past threshold). */
        const val STALE_CHECK_INTERVAL_MS = 30_000L
    }
}

/**
 * The real manager: owns the reactive + periodic evaluation jobs and
 * logs [AppLoggerKeys.EXCEEDED_EXPECTED_TIME_WITHOUT_FCM] /
 * [AppLoggerKeys.TIME_WITHOUT_FCM_IN_EXPECTED_RANGE] on transitions
 * (skipping the initial fresh emission).
 */
class DefaultCheckInStalenessManager(
    private val observeDoorEvents: ObserveDoorEventsUseCase,
    private val logAppEvent: LogAppEventUseCase,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val clock: AppClock,
    private val thresholdSeconds: Long = CheckInStalenessManager.CHECK_IN_STALE_THRESHOLD_SECONDS,
    private val intervalMillis: Long = CheckInStalenessManager.STALE_CHECK_INTERVAL_MS,
) : CheckInStalenessManager {
    private val staleFlow = MutableStateFlow(false)

    override val isCheckInStale: StateFlow<Boolean> = staleFlow

    /** Last-known check-in time, kept up-to-date by the reactive collector. */
    private val lastCheckInTime = MutableStateFlow<Long?>(null)

    private val jobs = mutableListOf<Job>()

    override fun start() {
        if (jobs.any { it.isActive }) {
            Logger.d { "CheckInStalenessManager: already running" }
            return
        }
        // Reactive: re-evaluate when door event changes.
        jobs += scope.launch(dispatcher) {
            observeDoorEvents.current().collect { event ->
                lastCheckInTime.value = event?.lastCheckInTimeSeconds
                staleFlow.value = computeStale(lastCheckInTime.value)
            }
        }
        // Periodic: re-evaluate even if no new event arrives (catches clock drift).
        jobs += scope.launch(dispatcher) {
            while (true) {
                delay(intervalMillis)
                staleFlow.value = computeStale(lastCheckInTime.value)
            }
        }
        // Log transitions (skip initial fresh emission).
        jobs += scope.launch(dispatcher) {
            var isFirstEmission = true
            staleFlow.collect { stale ->
                if (stale) {
                    logAppEvent(AppLoggerKeys.EXCEEDED_EXPECTED_TIME_WITHOUT_FCM)
                } else if (!isFirstEmission) {
                    logAppEvent(AppLoggerKeys.TIME_WITHOUT_FCM_IN_EXPECTED_RANGE)
                }
                isFirstEmission = false
            }
        }
    }

    private fun computeStale(checkInTimeSeconds: Long?): Boolean {
        if (checkInTimeSeconds == null) return false
        val age = clock.nowEpochSeconds() - checkInTimeSeconds
        return age > thresholdSeconds
    }
}
