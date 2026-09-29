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
import com.chriscartland.garage.domain.model.DoorEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * When the door the app knows about changes, tell the surfaces the SYSTEM
 * renders — a home-screen widget, a tile, a watch-face complication.
 *
 * Those surfaces are repainted on the platform's schedule (thirty minutes for
 * a widget, ten for a complication), and nothing about a push arriving or a
 * fetch landing tells them anything. So the phone could know the door had
 * moved while its own home screen kept saying otherwise for half an hour
 * (2026-09-28 cross-surface audit, finding 3.2). This closes that: the app
 * observes its own door and asks the surfaces to redraw.
 *
 * The platform cadence stays the FLOOR; this is the ceiling. A request is
 * only made on a change, so a surface that redraws by re-fetching cannot loop
 * — a re-fetch that lands the same event does not emit (the repository's
 * StateFlow conflates equal values), and one that lands a newer event is a
 * real change worth one more redraw.
 *
 * The value present at [start] is skipped: it is the seed or the disk
 * hydration, and a surface that has just been asked to render already reads
 * the same cache. What matters is what changes AFTER the app is up.
 *
 * ADR-015 manager shape: app-scoped, idempotent [start]. Each app supplies
 * its own [refresh] — the shared part is only "when", never "how".
 */
class SystemSurfaceRefresher(
    private val doorEvents: StateFlow<DoorEvent?>,
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
            doorEvents.drop(1).collect {
                Logger.d { "SystemSurfaceRefresher: door changed, refreshing system surfaces" }
                refresh()
            }
        }
    }
}
