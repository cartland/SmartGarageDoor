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
 * Raw string storage backing [StatusSnapshotStore]. Implemented in
 * `:data-local` over a dedicated Preferences DataStore file
 * (`status_cache.preferences_pb`); this interface exists so envelope
 * encoding, decode-failure policy, and tests stay in `:data`
 * (`:data-local` depends on `:data`, so the typed store cannot live
 * there — see `MobileGarage/docs/STATUS_CACHE_PLAN.md` §D1).
 *
 * Unlike [StatusSnapshotStore], implementations MAY throw on IO
 * failure — [DefaultStatusSnapshotStore] owns the catch-and-degrade
 * policy in one place.
 */
interface StatusCacheStorage {
    suspend fun get(key: String): String?

    suspend fun put(
        key: String,
        value: String,
    )

    suspend fun remove(keys: Set<String>)
}
