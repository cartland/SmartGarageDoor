/*
 * Copyright 2024 Chris Cartland. All rights reserved.
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

import com.chriscartland.garage.domain.model.FeatureAllowlist
import kotlinx.coroutines.flow.StateFlow

/**
 * Per-user feature allowlist owner.
 *
 * Per ADR-022, the repository owns the authoritative
 * `StateFlow<FeatureAllowlist?>` — an always-on collector observes the
 * auth state and refreshes after sign-in (and clears to null on
 * sign-out so a stale "yes" from a previous user can't leak across
 * accounts).
 *
 * `null` = unknown (haven't fetched, fetch failed, or signed out). UI
 * gates closed on null so users never see a flash-of-allowed before
 * the first fetch resolves.
 */
interface FeatureAllowlistRepository {
    /** Observation: latest cached allowlist (null until first fetch succeeds). */
    val allowlist: StateFlow<FeatureAllowlist?>

    /**
     * Force-refresh the allowlist for the currently-signed-in user.
     * On success, writes the result into [allowlist] and returns it.
     * Returns null on any failure (the flow is left unchanged on
     * non-auth-change errors).
     */
    suspend fun fetchAllowlist(): FeatureAllowlist?
}
