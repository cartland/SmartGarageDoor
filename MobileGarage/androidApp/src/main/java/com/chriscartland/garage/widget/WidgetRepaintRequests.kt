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

package com.chriscartland.garage.widget

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * How a repaint request reaches a widget that is ALREADY being rendered.
 *
 * Glance renders a widget in a session that stays alive for about 45 seconds,
 * and `update` / `updateAll` do not restart it: "`update` and `updateAll` do
 * not restart `provideGlance` if it is already running" (the library's own
 * KDoc on `GlanceAppWidget.provideGlance`). So asking Glance to update is only
 * half of a repaint — it starts a session where there is none and does nothing
 * observable where there is one.
 *
 * This is the other half. Every request is numbered, and a live session
 * observes the number ([WidgetGlanceStatus.observe]), so a request always ends
 * in the verdict being judged again against the present — which is what a
 * request made because TIME passed (the garage went quiet, the user left the
 * app) needs, since no value on disk changed.
 *
 * A `@Singleton` because the request is made by the application and heard by a
 * session the system started: two instances would be two counters, and the
 * session would be listening to the one nobody increments.
 */
class WidgetRepaintRequests {
    private val _count = MutableStateFlow(0L)

    /** How many repaints have been asked for. Only ever compared for change. */
    val count: StateFlow<Long> = _count

    /** Ask every live session to judge its verdict again. */
    fun request() {
        _count.update { it + 1 }
    }
}
