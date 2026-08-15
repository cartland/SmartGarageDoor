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

import co.touchlab.kermit.Logger
import com.chriscartland.garage.domain.repository.UserScopedCache

/**
 * [UserScopedCache] over the status-snapshot store: clears the
 * [userScopedKeys] set (DI passes [StatusCacheKeys.CLEARED_ON_SIGN_OUT])
 * when the sign-out manager fires, then runs every registered in-memory
 * reset in the same transition (DATA_CACHING_STRATEGY P8). Never throws
 * — [StatusSnapshotStore.clear] swallows storage failures, and each
 * reset is individually guarded so one failing reset cannot skip the
 * others.
 */
class DefaultUserScopedCache(
    private val statusSnapshotStore: StatusSnapshotStore,
    private val userScopedKeys: Set<StatusCacheKey>,
) : UserScopedCache {
    // Registration happens during the single-threaded DI construction
    // pass; the sign-out manager can only fire afterwards (it collects
    // an auth flow owned by an already-constructed repository), so
    // register and clear never actually contend. `toList()` in the
    // clear still snapshots defensively.
    private val inMemoryResets = mutableListOf<Pair<String, suspend () -> Unit>>()

    override fun registerInMemoryReset(
        name: String,
        reset: suspend () -> Unit,
    ) {
        inMemoryResets.add(name to reset)
        Logger.d { "UserScopedCache: registered in-memory reset `$name`" }
    }

    override suspend fun clearUserScopedEntries() {
        statusSnapshotStore.clear(userScopedKeys)
        inMemoryResets.toList().forEach { (name, reset) ->
            try {
                reset()
                Logger.i { "UserScopedCache: in-memory reset `$name` done" }
            } catch (t: Throwable) {
                // Never-throws contract; a failing reset must not skip
                // the remaining ones or break the sign-out transition.
                Logger.e(t) { "UserScopedCache: in-memory reset `$name` failed" }
            }
        }
    }
}
