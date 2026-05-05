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

import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.ButtonHealth
import com.chriscartland.garage.domain.model.ButtonHealthState
import com.chriscartland.garage.domain.model.LoadingResult

/**
 * Pure derivation: combine the user's auth state and the current
 * button-health snapshot into a single [ButtonHealthDisplay].
 *
 * Wrapped in a named `object` per ADR-009 (`checkNoBareTopLevelFunctions`).
 *
 * Auth pre-condition is a guard clause; the inner `when (health)` and
 * nested `when (health.data.state)` are exhaustive over sealed types
 * and enums respectively — the Kotlin compiler enforces completeness.
 *
 * TODO: when a server-side button-health allowlist is added to
 * FeatureAllowlist, gate Unauthorized on that too. Currently any
 * signed-in user reaches the Online/Offline/Unknown/Loading branches
 * — a non-allowlisted user would see Loading-then-Loading from the
 * cold-start 403, which renders as no pill (functionally correct,
 * but doesn't tell the manager to stop subscribing).
 */
object ButtonHealthDisplayLogic {
    fun compute(
        auth: AuthState,
        health: LoadingResult<ButtonHealth>,
        nowSeconds: Long,
    ): ButtonHealthDisplay {
        // Pre-condition: signed-out users never see the indicator.
        if (auth !is AuthState.Authenticated) return ButtonHealthDisplay.Unauthorized

        // Sealed-type exhaustive when on LoadingResult — compiler-enforced.
        return when (health) {
            is LoadingResult.Loading -> ButtonHealthDisplay.Loading
            is LoadingResult.Error -> ButtonHealthDisplay.Loading // transient; retry will fix
            is LoadingResult.Complete -> {
                val data = health.data
                if (data == null) {
                    ButtonHealthDisplay.Loading
                } else {
                    when (data.state) {
                        ButtonHealthState.UNKNOWN -> ButtonHealthDisplay.Unknown
                        ButtonHealthState.ONLINE -> ButtonHealthDisplay.Online
                        ButtonHealthState.OFFLINE ->
                            ButtonHealthDisplay.Offline(
                                durationLabel = ButtonHealthDurationFormatter.formatAgo(
                                    data.stateChangedAtSeconds,
                                    nowSeconds,
                                ),
                            )
                    }
                }
            }
        }
    }
}
