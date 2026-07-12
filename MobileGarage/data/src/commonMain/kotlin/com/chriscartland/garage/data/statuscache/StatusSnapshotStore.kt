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

import kotlinx.serialization.KSerializer

/**
 * Persisted last-known-value cache for server-fetched statuses
 * (see `MobileGarage/docs/STATUS_CACHE_PLAN.md`).
 *
 * Repositories hydrate their in-memory `StateFlow` from here at init
 * (instant last-known display) and write through on accepted server
 * results. This store is a dumb persistence layer: freshness policy,
 * write arbitration, and hydration ordering belong to the consuming
 * repository.
 *
 * **The API never throws.** Callers run on the
 * `CoroutineExceptionHandler`-less `applicationScope`, where an
 * uncaught exception crashes the process — and a bad on-disk snapshot
 * would crash it on EVERY launch. So:
 *  - [read] returns null for missing entries AND for every failure
 *    (envelope parse, schema-version mismatch, payload decode, storage
 *    IO); decode-level failures also delete the corrupt entry so the
 *    cache self-heals.
 *  - [write] and [clear] swallow-and-log storage failures. A lost
 *    write costs one redundant fetch on the next cold start — always
 *    preferable to a crash loop.
 */
interface StatusSnapshotStore {
    /**
     * Returns the persisted snapshot for [key], or null when the entry
     * is missing, was written with a different [schemaVersion], or
     * cannot be decoded (in which case the corrupt entry is deleted).
     */
    suspend fun <T : Any> read(
        key: StatusCacheKey,
        schemaVersion: Int,
        payloadSerializer: KSerializer<T>,
    ): StatusSnapshot<T>?

    /**
     * Persists [snapshot] for [key], replacing any existing entry.
     * Storage failures are swallowed (see interface KDoc).
     */
    suspend fun <T : Any> write(
        key: StatusCacheKey,
        schemaVersion: Int,
        payloadSerializer: KSerializer<T>,
        snapshot: StatusSnapshot<T>,
    )

    /**
     * Removes the entries for [keys]. No-op for keys with no entry.
     * Storage failures are swallowed (see interface KDoc).
     */
    suspend fun clear(keys: Set<StatusCacheKey>)
}
