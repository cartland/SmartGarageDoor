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

package com.chriscartland.garage.data.statuscache

/**
 * Identifies one entry in the status-snapshot cache
 * (see `MobileGarage/docs/STATUS_CACHE_PLAN.md`).
 *
 * [storageKey] is the on-disk preferences key. Renaming a storage key
 * orphans the old entry (it is never read again and never cleaned up),
 * so treat storage keys as append-only; to change an entry's shape,
 * bump the schema version passed to [StatusSnapshotStore] instead.
 */
data class StatusCacheKey(
    val storageKey: String,
)

/**
 * Registry of status-cache keys with cross-cutting behavior.
 *
 * Each repository that persists a snapshot defines its own
 * [StatusCacheKey] next to its DTO; this object only aggregates the
 * keys that shared infrastructure needs to know about.
 */
object StatusCacheKeys {
    /**
     * Entries cleared when the user signs out (consumed by the
     * sign-out manager via `UserScopedCache`). A PR that persists a
     * per-user value MUST add its key here in the same change —
     * anything derived from the signed-in account (feature allowlist,
     * auth-gated statuses) belongs in this set.
     */
    val CLEARED_ON_SIGN_OUT: Set<StatusCacheKey> = setOf(
        // Auth-gated status: the display is signed-in-only and the fetch
        // requires an allowlisted account, so the verdict doesn't belong
        // to a signed-out device (STATUS_CACHE_PLAN.md D2).
        ButtonHealthSnapshot.KEY,
        // Household state, but surfaced on a signed-in-only settings row;
        // clearing on sign-out keeps the cache free of anything the
        // signed-out UI can't show (STATUS_CACHE_PLAN.md D3).
        SnoozeSnapshot.KEY,
        // Genuinely per-user: Developer/Function-List access must not
        // leak across accounts. The clear here is best-effort; the
        // account-keyed hydration is the guarantee (STATUS_CACHE_PLAN.md D4).
        AllowlistSnapshot.KEY,
    )
}
