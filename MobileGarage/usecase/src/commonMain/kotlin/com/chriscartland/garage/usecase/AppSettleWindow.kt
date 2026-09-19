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
 * **Five seconds of SCREEN TIME, counted — not five seconds of wall clock,
 * timed.** This is the difference between a grace period and a silencer, and
 * it is the one thing about this class that must not be simplified away.
 *
 * The first implementation restarted a `delay(5_000)` on every return, which
 * meant the window only ever closed after five *uninterrupted* visible
 * seconds. Nothing guarantees the app gets five uninterrupted seconds:
 *
 * - On the watch, `onVisible`/`onHidden` are `ON_START`/`ON_STOP`, so every
 *   wrist-down is a departure. Someone glancing at the watch for three
 *   seconds at a time — the normal way people use a watch — would restart the
 *   countdown on every glance and **never** be told the garage was
 *   unreachable.
 * - On iOS a notification banner, Control Center, the app switcher or an
 *   incoming call all report `.inactive`, so each dismissal bought another
 *   five seconds of silence.
 *
 * That turned "delay the warning" into "suppress the warning indefinitely",
 * which is the one thing this feature promised it could never do. So the
 * countdown now *accumulates*: leaving PAUSES it, returning RESUMES it, and
 * it is only reset to a fresh five seconds by a return that arrives after the
 * window has already closed — which is exactly the warm start the grace
 * period is for. Two three-second glances therefore add up and the second one
 * speaks.
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
         * Five seconds of SCREEN TIME — see [AppSettleWindow] on why it is
         * counted rather than timed.
         *
         * Long enough that a return fetch over a normal connection lands
         * inside it (the whole point — the banner the user kept seeing was one
         * that resolved itself), short enough that a genuinely unreachable
         * server is named while the user is still looking at the screen.
         *
         * `RelayFallbackAuthBridge` applies the same rule to Wear's identity
         * lookup and reads this constant directly.
         */
        const val SETTLE_WINDOW_MILLIS: Long = 5_000L

        /**
         * How often the countdown is decremented while the app is visible.
         *
         * The countdown ticks rather than sleeping once, because a single
         * `delay(5_000)` loses everything it has waited when the app goes away
         * — which is the whole defect described in [AppSettleWindow]. At worst
         * one tick's worth of screen time is lost to a departure landing
         * mid-tick, so this is the granularity of the promise: 250 ms.
         */
        const val COUNTDOWN_TICK_MILLIS: Long = 250L
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

    /**
     * Screen time still owed before the window closes. Lives OUTSIDE the
     * collector so that a departure — which cancels the collector's block —
     * cannot take the countdown's progress with it. This variable is the
     * accumulation described in [AppSettleWindow]; it is the whole fix.
     *
     * Only ever touched from the single collector coroutine, so a plain `var`
     * is sufficient (and matches `WearHomeViewModel.voicePressAwaitingDoor`,
     * the other plain-state-in-a-coroutine case in the codebase).
     */
    private var remainingMillis: Long = windowMillis

    override fun start() {
        if (job?.isActive == true) {
            Logger.d { "AppSettleWindow: already running" }
            return
        }
        job = scope.launch(dispatcher) {
            // `collectLatest` on `Visibility` (which carries an epoch) is what
            // reacts to a return: a genuine return is a distinct value even
            // when the boolean conflates, so the block below is cancelled and
            // re-entered. See AppVisibilityState.Visibility for why the epoch
            // is there at all.
            appVisibilityState.visibility.collectLatest { visibility ->
                // Going away is NOT an answer, and it must not be a reset.
                // Returning early leaves both the flag and `remainingMillis`
                // alone, which is what (a) lets the initial `true` survive the
                // starting `Visibility(isVisible = false, epoch = 0)` every
                // collector sees before the platform has reported anything,
                // and (b) makes a departure a PAUSE rather than a rewind.
                if (!visibility.isVisible) return@collectLatest
                if (!settling.value) {
                    // The window had already closed, so this return is a real
                    // arrival — a warm start — and earns a fresh grace period.
                    remainingMillis = windowMillis
                    settling.value = true
                    Logger.d { "AppSettleWindow: reopened (epoch=${visibility.epoch})" }
                }
                // Tick the countdown down rather than sleeping it off in one
                // go. A single delay would lose everything it had waited when
                // the app went away; ticking means at most COUNTDOWN_TICK_MILLIS
                // of screen time is lost to a departure landing mid-tick.
                while (remainingMillis > 0) {
                    delay(AppSettleWindow.COUNTDOWN_TICK_MILLIS)
                    remainingMillis -= AppSettleWindow.COUNTDOWN_TICK_MILLIS
                }
                settling.value = false
                Logger.d { "AppSettleWindow: settled (epoch=${visibility.epoch})" }
            }
        }
    }
}
