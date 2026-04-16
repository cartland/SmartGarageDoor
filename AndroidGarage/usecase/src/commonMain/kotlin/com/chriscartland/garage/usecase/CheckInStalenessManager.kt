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
import com.chriscartland.garage.domain.model.AppLoggerKeys
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * App-scoped manager for door check-in staleness (ADR-015, ADR-017).
 *
 * Computes whether the last door check-in is older than [thresholdSeconds].
 * Re-evaluates on every door event change (reactive) AND on a fixed
 * interval (in case clock time passes the threshold without new events).
 *
 * Logs transitions: emits [AppLoggerKeys.EXCEEDED_EXPECTED_TIME_WITHOUT_FCM]
 * when becoming stale, [AppLoggerKeys.TIME_WITHOUT_FCM_IN_EXPECTED_RANGE]
 * when becoming fresh (skipping the initial fresh emission).
 *
 * [start] is idempotent — calling twice doesn't create duplicate jobs.
 * Status is observable via [isCheckInStale].
 */
class CheckInStalenessManager(
    private val observeDoorEvents: ObserveDoorEventsUseCase,
    private val logAppEvent: LogAppEventUseCase,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val clock: AppClock,
    private val thresholdSeconds: Long = CHECK_IN_STALE_THRESHOLD_SECONDS,
    private val intervalMillis: Long = STALE_CHECK_INTERVAL_MS,
) {
    private val staleFlow = MutableStateFlow(false)

    /** Observable staleness. ViewModel collects this. */
    val isCheckInStale: Flow<Boolean> = staleFlow

    /** Last-known check-in time, kept up-to-date by the reactive collector. */
    private val lastCheckInTime = MutableStateFlow<Long?>(null)

    private val jobs = mutableListOf<Job>()

    /**
     * Start staleness evaluation. Idempotent — if already running, this is a no-op.
     */
    fun start() {
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

    companion object {
        /** 11 minutes — matches the previous OldLastCheckInBanner threshold. */
        const val CHECK_IN_STALE_THRESHOLD_SECONDS = 11L * 60

        /** Re-evaluate staleness every 30 seconds (catches clock drift past threshold). */
        const val STALE_CHECK_INTERVAL_MS = 30_000L
    }
}
