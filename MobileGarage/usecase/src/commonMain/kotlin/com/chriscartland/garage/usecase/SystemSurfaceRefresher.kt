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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * When the app learns something, tell the surfaces the SYSTEM renders — a
 * home-screen widget, a tile, a watch-face complication.
 *
 * Those surfaces are repainted on the platform's schedule (thirty minutes for
 * a widget, ten for a complication), and nothing about a push arriving or a
 * fetch landing tells them anything. So the phone could know the door had
 * moved while its own home screen kept saying otherwise for half an hour
 * (2026-09-28 cross-surface audit, finding 3.2). This closes that: the app
 * watches what it knows and asks the surfaces to redraw.
 *
 * ## What counts as learning something
 *
 * Whatever a surface's verdict is made of, plus the two moments a person
 * looks. Each app hands in the [changes] it has:
 *
 *  - **the door event** — the door moved, or the garage checked in;
 *  - **a staleness verdict** (where the app keeps one) — the reading went
 *    quiet, which is a change produced by the clock and by no event at all,
 *    so an event-only trigger left the widget confident about a garage the
 *    app had already greyed;
 *  - **the app's visibility** — arriving and leaving. Opening the app is
 *    expected to bring its widgets up to date (maintainer, 2026-10-03).
 *    Leaving is the one that is SEEN: the launcher or the watch face is what
 *    replaces the app on screen, and it must not contradict the screen the
 *    user was reading a second earlier.
 *
 * The platform cadence stays the FLOOR; this is the ceiling. A request is
 * only made on a CHANGE, which is what keeps a surface that redraws by
 * re-fetching from looping: the re-fetch lands an equal event, a `StateFlow`
 * conflates equal values, nothing emits. A re-fetch that lands a newer event
 * is a real change worth one more redraw. The other two cannot loop either —
 * visibility moves only when a person does, and a staleness verdict flips at
 * most once per threshold crossing.
 *
 * The value each flow holds at [start] is skipped: it is the seed or the disk
 * hydration, and a surface that has just been asked to render already reads
 * the same cache. What matters is what changes AFTER the app is up. That is
 * also why the inputs are [StateFlow]s and not plain flows — "skip the first
 * emission" only means "skip the current value" for a flow that replays one.
 *
 * Changes that land while a refresh is still running collapse into one more
 * refresh rather than queueing one each: a push usually moves the door event
 * and the staleness verdict together, and the surfaces need to be told once.
 * That is a one-slot channel written out by hand, on purpose — the obvious
 * `merge().conflate()` does NOT conflate (the merge operator hands its
 * capacity to `produce` and drops the overflow policy, leaving a rendezvous
 * channel that delivers every queued change), which
 * `changesThatLandDuringARefreshCollapseIntoOneMore` caught.
 *
 * ADR-015 manager shape: app-scoped, idempotent [start]. Each app supplies
 * its own [refresh] — the shared part is only "when", never "how".
 */
class SystemSurfaceRefresher(
    private val changes: List<StateFlow<*>>,
    private val refresh: suspend () -> Unit,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null

    /** Idempotent: a second call while running is a no-op, not a second collector. */
    fun start() {
        if (job != null) {
            Logger.d { "SystemSurfaceRefresher: already started" }
            return
        }
        job = scope.launch {
            // One slot: "something changed since the last refresh began".
            // A send into a full conflated channel replaces what is there and
            // never suspends, so a slow refresh cannot back up its inputs.
            val somethingChanged = Channel<Unit>(Channel.CONFLATED)
            changes.forEach { flow ->
                launch { flow.drop(1).collect { somethingChanged.send(Unit) } }
            }
            for (change in somethingChanged) {
                Logger.d { "SystemSurfaceRefresher: the app learned something, refreshing system surfaces" }
                refresh()
            }
        }
    }
}
