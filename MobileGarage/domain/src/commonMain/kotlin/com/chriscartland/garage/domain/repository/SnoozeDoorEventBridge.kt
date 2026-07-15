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

package com.chriscartland.garage.domain.repository

/**
 * Nullable-handle bridge between the FCM door-event receive path and
 * the snooze repository (STATUS_CACHE_PLAN.md D3 door-event voiding
 * hook).
 *
 * Why a bridge instead of injecting `SnoozeRepository` into the FCM
 * path: the server voids a snooze on ANY door event, so the receive
 * path must be able to trigger a snooze refetch — but the snooze
 * repository is deliberately constructed lazily (first Settings
 * entry). A direct dependency would force-construct it (init fetch and
 * all) on every FCM-woken background process. With the bridge, the
 * repository registers its listener at construction; if it was never
 * constructed, [notifyDoorEvent] is a no-op and background wakes pay
 * nothing.
 *
 * The DI provider MUST be a singleton — the repository and the FCM
 * path must see the same instance or the registration is lost.
 * [register] races [notifyDoorEvent] benignly: a door event arriving
 * in the instant before registration is dropped, which only delays
 * voiding detection until the next trigger (screen-entry revalidate,
 * manual refresh, next door event).
 */
class SnoozeDoorEventBridge {
    private var listener: (() -> Unit)? = null

    /** Called once, by the snooze repository at construction. */
    fun register(onDoorEvent: () -> Unit) {
        listener = onDoorEvent
    }

    /**
     * Called by the FCM door-event receive path. Must be cheap and
     * non-suspending — the listener launches its own work.
     */
    fun notifyDoorEvent() {
        listener?.invoke()
    }
}
