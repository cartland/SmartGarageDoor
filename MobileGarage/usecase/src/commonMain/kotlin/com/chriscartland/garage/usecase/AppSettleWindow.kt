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
 * The grace period the app gets, each time it comes on screen, to look
 * current before it is allowed to tell the user that something is wrong
 * (ADR-015 app-scoped manager).
 *
 * The problem it exists to solve: arriving is not the same event as failing,
 * but they used to render identically. A warm start whose cached door event
 * had aged past the check-in threshold went straight to the alarm
 * presentation — banner, Retry button — for the second or so it took the
 * return fetch to land. Every single launch showed an error that was gone
 * before the user could act on it, which teaches people to ignore the banner
 * for the one time it is real.
 *
 * So the app now waits [SETTLE_WINDOW_MILLIS] before it starts describing a
 * problem. Within the window it still shows the honest thing — the door is
 * rendered desaturated and dimmed, per `DataFreshness.SETTLING` — it just
 * does not put it into words yet.
 *
 * Keyed on visibility, not on a fetch, because the window belongs to the
 * *user's arrival*, not to any one request. A single return may involve a
 * cache read, a token refresh, and a fetch; what the user experiences is one
 * moment of opening the app, and they should get one grace period for it.
 *
 * An interface with a `Default*` implementation, matching [LiveClock] and
 * [CheckInStalenessManager] — the other two ViewModel-visible ADR-015
 * managers — so a ViewModel test can supply the flag directly instead of
 * standing up a real timer.
 */
interface AppSettleWindow {
    /**
     * True while the app is inside its grace window. [StateFlow] so a screen
     * entering mid-window reads the right answer on its first frame rather
     * than defaulting to "settled" and flashing a banner (DATA_CACHING P3 /
     * T1; ADR-022 says consumers pass the reference through).
     */
    @NodeCadence(Cadence.CLOCK)
    val isSettling: StateFlow<Boolean>

    /**
     * Begin watching visibility. Idempotent — calling twice does not create a
     * second job. Typically called from `AppStartup`.
     */
    fun start()

    companion object {
        /**
         * Five seconds. Long enough that a return fetch over a normal
         * connection lands inside it (the whole point — the banner the user
         * kept seeing was one that resolved itself), short enough that a
         * genuinely unreachable server is named while the user is still
         * looking at the screen.
         *
         * `RelayFallbackAuthBridge.DEFAULT_UNRESOLVED_GRACE_MILLIS` is the
         * same rule applied to Wear's identity lookup and is pinned to this
         * value by test.
         */
        const val SETTLE_WINDOW_MILLIS: Long = 5_000L
    }
}

/**
 * The real window: one job, restarted on every genuine return.
 *
 * Starts [isSettling] at `true` rather than `false`. At construction the app
 * is, definitionally, starting — and the platform's first
 * [AppVisibilityState.setVisible] call arrives a little after the first
 * composition. Seeding `false` would let one frame through with the full
 * alarm presentation, which is the exact flash this class exists to remove.
 */
class DefaultAppSettleWindow(
    private val appVisibilityState: AppVisibilityState,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val windowMillis: Long = AppSettleWindow.SETTLE_WINDOW_MILLIS,
) : AppSettleWindow {
    private val settling = MutableStateFlow(true)

    override val isSettling: StateFlow<Boolean> = settling

    private var job: Job? = null

    override fun start() {
        if (job?.isActive == true) {
            Logger.d { "AppSettleWindow: already running" }
            return
        }
        job = scope.launch(dispatcher) {
            // `collectLatest` on `Visibility` (which carries an epoch) is what
            // restarts the window: a genuine return is a distinct value even
            // when the boolean conflates, so the timer below is cancelled and
            // relaunched. See AppVisibilityState.Visibility for why the epoch
            // is there at all.
            appVisibilityState.visibility.collectLatest { visibility ->
                // Going away is NOT an answer. Leaving the flag alone here is
                // what lets the initial `true` survive the starting
                // `Visibility(isVisible = false, epoch = 0)` value that every
                // collector sees before the platform has reported anything.
                // Nothing is on screen while hidden, so a stuck `true` costs
                // nothing and the next return restarts the window anyway.
                if (!visibility.isVisible) return@collectLatest
                settling.value = true
                delay(windowMillis)
                settling.value = false
                Logger.d { "AppSettleWindow: settled (epoch=${visibility.epoch})" }
            }
        }
    }
}
