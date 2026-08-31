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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the user can currently see the app — reported BY the platform,
 * read by shared code (ADR-015 app-scoped state).
 *
 * The direction is deliberate and matches how door events already arrive:
 * `FCMService` / `AppDelegate` call `ReceiveFcmDoorEventUseCase`, they are
 * not polled by it. Lifecycle is the same shape of fact — only the
 * platform knows it, and only the platform knows when it changed — so the
 * contract here is a sink the platform writes to
 * ([setVisible]), never an `expect`/`actual` or a bridge interface shared
 * code has to call out through.
 *
 * That choice is also the test seam. There is no fake and no interface:
 * a test constructs the real object and calls [setVisible], which is a
 * more honest simulation of backgrounding than any fake would be.
 *
 * **Not a data-graph node.** It drives *when* the app fetches, not what
 * any node's value is; nothing derives from it. It is a concrete class
 * (not a `:usecase` interface) so the C1 flow sweep does not enumerate it
 * as a candidate input.
 */
class AppVisibilityState {
    /**
     * Visibility plus a count of how many times it has become true.
     *
     * The epoch is what makes "the user came back" survive conflation. A
     * plain `StateFlow<Boolean>` cannot express it reliably: the platform
     * writes `false` then `true` synchronously in its callbacks, but a
     * collector that was suspended for both writes (a fast lock/unlock;
     * the iOS process suspending before the IO dispatcher ran) compares
     * conflated `true` against its last-seen `true`, sees no change, and
     * never wakes — so the return fetch silently doesn't happen and the
     * user stares at a stale screen for up to a full poll interval.
     * `epoch` differs on every genuine return, so the conflated value is
     * still distinct and the collector always restarts. Proven by
     * `DoorUpdateStrategyTest.aReturnThatConflatesWithTheDepartureStillFetchesImmediately`,
     * which fails against the Boolean version.
     */
    data class Visibility(
        val isVisible: Boolean,
        val epoch: Long,
    )

    private val _visibility = MutableStateFlow(Visibility(isVisible = false, epoch = 0))

    /**
     * Current visibility. Starts not-visible with epoch 0: the app has
     * not told us it is visible yet, and claiming visibility we were
     * never told about is the failure that would keep a poll running in
     * the background.
     */
    val visibility: StateFlow<Visibility> = _visibility

    /**
     * Report a visibility change. Idempotent — repeated identical values
     * return early, so a platform that over-reports (Android fires
     * per-Activity; a configuration change stops one and starts another)
     * neither costs anything nor inflates the epoch. Only a real
     * `false -> true` transition counts as a return.
     */
    fun setVisible(visible: Boolean) {
        val current = _visibility.value
        if (current.isVisible == visible) return
        Logger.d { "AppVisibilityState: visible=$visible" }
        _visibility.value = Visibility(
            isVisible = visible,
            epoch = if (visible) current.epoch + 1 else current.epoch,
        )
    }
}
