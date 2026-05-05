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

import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.ButtonHealth
import com.chriscartland.garage.domain.model.ButtonHealthError
import com.chriscartland.garage.domain.model.LoadingResult
import kotlinx.coroutines.flow.StateFlow

/**
 * Owner of the authoritative [StateFlow] for the remote-button device's
 * online/offline state.
 *
 * State flow (ADR-022): the repository owns the flow; callers (UseCases,
 * ViewModels) expose it by reference — no mirrors.
 *
 * Updates flow in from two sources:
 *  - [fetchButtonHealth] — explicit cold-start fetch (called by the
 *    subscription manager after sign-in + allowlist check).
 *  - [applyFcmUpdate] — server-pushed transitions via FCM data message.
 *
 * The repository's implementation enforces the FCM-vs-fetch ordering
 * rule (only overwrite if the incoming value's stateChangedAtSeconds
 * is strictly newer than the current value's). UNKNOWN is treated as
 * oldest — any non-UNKNOWN value wins over a current UNKNOWN.
 */
interface ButtonHealthRepository {
    val buttonHealth: StateFlow<LoadingResult<ButtonHealth>>

    /**
     * Force-refresh the cached state from the server's cold-start endpoint.
     *
     * The caller (typically [ButtonHealthFcmSubscriptionManager]) provides
     * the current Firebase ID token. Returns [AppResult.Success] with the
     * freshly-fetched value (also written to [buttonHealth]) or
     * [AppResult.Error] with the typed failure mode.
     */
    suspend fun fetchButtonHealth(idToken: String): AppResult<ButtonHealth, ButtonHealthError>

    /**
     * Apply a server-pushed update from an FCM data message.
     *
     * Implementation MUST enforce the timestamp gate: drop the update
     * if the current value is already at least as fresh.
     */
    fun applyFcmUpdate(update: ButtonHealth)
}
