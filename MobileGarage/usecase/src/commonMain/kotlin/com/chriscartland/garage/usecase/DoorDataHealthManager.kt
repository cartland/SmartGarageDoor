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
import com.chriscartland.garage.domain.coroutines.AppClock
import com.chriscartland.garage.domain.graph.DataGraph.Cadence
import com.chriscartland.garage.domain.graph.NodeCadence
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Whether the app's door data is UNHEALTHY: it has been failing to reach the
 * server for long enough that a screen should say so (ADR-015 app-scoped
 * manager).
 *
 * The maintainer's rule (2026-10-04): one failed fetch must not be announced,
 * but a small indicator belongs on screen once the failures pile up, or once
 * a failure has been followed by a recovery window that never recovered. So
 * the verdict is true when EITHER
 *
 *  - [FAILURES_TO_BE_UNHEALTHY] attempts in a row have failed, or
 *  - the oldest failure in the current run is [RECOVERY_WINDOW_SECONDS] old
 *    and nothing has succeeded since.
 *
 * It goes false the moment anything gets through — a successful fetch by any
 * surface, or a push — because both reset the repository's consecutive-failure
 * count (`DoorRepository.currentDoorFetchFailures`) to zero.
 *
 * **The timer is bounded by construction.** It runs only while a failure run
 * is open and has not yet crossed the window: one `delay` until the window
 * closes, cancelled the instant the count changes. There is no periodic tick,
 * so a healthy app costs nothing and an unhealthy one waits for news.
 *
 * Deliberately NOT a fetcher. It decides when the data is unhealthy; whether
 * anything retries inside the window is the running `DoorUpdateStrategy`'s
 * business (iOS polls and backs off; Android ships push-only and does not).
 */
interface DoorDataHealthManager {
    /**
     * True while the door data is unhealthy, per the rule above. A
     * [StateFlow] so a screen reads the current verdict on its first frame.
     * CLOCK, because the window closing is a change the clock produces with
     * no event behind it.
     */
    @NodeCadence(Cadence.CLOCK)
    val isDoorDataUnhealthy: StateFlow<Boolean>

    /** Start watching. Idempotent. Called from `AppStartup`. */
    fun start()

    companion object {
        /**
         * Three in a row. One is a blip and two can be one bad moment on a
         * cell edge; by a third the problem is not going to clear on its own
         * in the next few seconds. On iOS's 15-second poll with its backoff
         * the third failure lands about 45 seconds into an outage, just before
         * the window below would have said the same thing.
         */
        const val FAILURES_TO_BE_UNHEALTHY = 3

        /**
         * One minute. Long enough that a single failure followed by the next
         * poll's success never shows anything; short enough that a phone
         * which tried once and has heard nothing since says so within a
         * minute rather than leaving a confident screen up indefinitely.
         * Deliberately far below the 11-minute check-in staleness threshold:
         * this is about OUR requests, which fail fast, not the garage's
         * reports, which arrive on its own cadence.
         */
        const val RECOVERY_WINDOW_SECONDS = 60L

        /** The rule, pure, so both the manager and a test can state it. */
        fun isUnhealthy(
            consecutiveFailures: Int,
            secondsSinceFirstFailure: Long,
        ): Boolean =
            consecutiveFailures >= FAILURES_TO_BE_UNHEALTHY ||
                (consecutiveFailures > 0 && secondsSinceFirstFailure >= RECOVERY_WINDOW_SECONDS)
    }
}

/**
 * The real manager: watches the repository's consecutive-failure count and
 * holds the time the current run of failures began.
 */
class DefaultDoorDataHealthManager(
    private val observeDoorEvents: ObserveDoorEventsUseCase,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val clock: AppClock,
) : DoorDataHealthManager {
    private val unhealthy = MutableStateFlow(false)

    override val isDoorDataUnhealthy: StateFlow<Boolean> = unhealthy

    private var job: Job? = null

    override fun start() {
        if (job != null) {
            Logger.d { "DoorDataHealthManager: already started" }
            return
        }
        job = scope.launch(dispatcher) {
            var firstFailureAt: Long? = null
            var lastSeen = 0
            // collectLatest: a new count cancels a pending window wait, and
            // the verdict is re-judged from the same start of the run.
            observeDoorEvents.currentFetchFailures().collectLatest { failures ->
                // A count that went DOWN without passing through zero (a
                // success and a new failure conflated into one emission) is
                // a new run, not a continuation of the old one.
                if (failures == 0 || failures < lastSeen) firstFailureAt = null
                lastSeen = failures
                if (failures == 0) {
                    unhealthy.value = false
                    return@collectLatest
                }
                val now = clock.nowEpochSeconds()
                val runStartedAt = firstFailureAt ?: now.also { firstFailureAt = it }
                val elapsed = now - runStartedAt
                if (DoorDataHealthManager.isUnhealthy(failures, elapsed)) {
                    unhealthy.value = true
                    return@collectLatest
                }
                unhealthy.value = false
                // Not unhealthy YET: wait for the window to close. Cancelled
                // by any change in the count, which is exactly when the
                // verdict has to be judged again.
                delay((DoorDataHealthManager.RECOVERY_WINDOW_SECONDS - elapsed) * MILLIS_PER_SECOND)
                Logger.w { "DoorDataHealthManager: $failures failure(s) and no recovery within the window" }
                unhealthy.value = true
            }
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
