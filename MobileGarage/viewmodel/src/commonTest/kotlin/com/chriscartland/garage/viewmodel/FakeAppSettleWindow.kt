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

package com.chriscartland.garage.viewmodel

import com.chriscartland.garage.usecase.AppSettleWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The settle window as a settable flag (ADR-017 fake conventions), the twin
 * of [FakeCheckInStalenessManager].
 *
 * Defaults to `false` — SETTLED — which is the opposite of the real
 * implementation's initial value, and deliberately so. A ViewModel test
 * asserting on banners or on freshness wants to describe a settled app unless
 * it says otherwise; defaulting to "still settling" would silently suppress
 * every freshness verdict and turn a broken gate into a passing suite. Tests
 * that care about the window opt into it explicitly with [setSettling].
 *
 * Tests of the window's own DERIVATION — that it opens on a return, closes
 * after five seconds, and survives the starting not-visible value — belong in
 * `AppSettleWindowTest` against `DefaultAppSettleWindow`, not here.
 */
class FakeAppSettleWindow(
    initiallySettling: Boolean = false,
) : AppSettleWindow {
    private val flow = MutableStateFlow(initiallySettling)

    override val isSettling: StateFlow<Boolean> = flow

    /** True once [start] has been called — pins the AppStartup contract. */
    var started: Boolean = false
        private set

    override fun start() {
        started = true
    }

    /** Open or close the window the way a return or its expiry would. */
    fun setSettling(settling: Boolean) {
        flow.value = settling
    }
}
