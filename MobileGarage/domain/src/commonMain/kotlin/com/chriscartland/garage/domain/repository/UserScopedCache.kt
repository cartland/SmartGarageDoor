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
 * Locally cached data scoped to the signed-in user.
 *
 * Implemented by the status-snapshot cache in `:data`
 * (see `MobileGarage/docs/STATUS_CACHE_PLAN.md`). This narrow domain
 * interface exists so the sign-out manager in `:usecase` (which
 * depends only on `:domain`) can clear per-user cache entries without
 * a `:usecase` → `:data` dependency — the same layering as the other
 * repository interfaces here.
 */
interface UserScopedCache {
    /**
     * Clears every cache entry registered as user-scoped — the DISK
     * tier AND every in-memory reset registered via
     * [registerInMemoryReset]. Called when the user signs out so cached
     * per-user values (e.g. the feature allowlist) cannot leak across
     * accounts. Must not throw.
     */
    suspend fun clearUserScopedEntries()

    /**
     * Registers an in-memory reset that runs in the SAME sign-out
     * transition as the disk clear (DATA_CACHING_STRATEGY P8: sign-out
     * clears memory and disk, or it clears nothing). State-owning
     * repositories call this at construction, so a repository that was
     * never built has nothing to clear, and "did you clear memory
     * too?" is answered structurally by one call site.
     *
     * [name] is for logging; [reset] must be idempotent and must not
     * assume any ordering relative to other resets. Motivating defect:
     * the previous session's button-health verdict survived sign-out in
     * the singleton, and snooze's `lastFetchedAtSeconds` suppressed the
     * next session's revalidation for up to the fetch TTL.
     */
    fun registerInMemoryReset(
        name: String,
        reset: suspend () -> Unit,
    )
}
