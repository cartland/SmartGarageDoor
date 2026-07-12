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

import com.chriscartland.garage.domain.repository.UserScopedCache

/**
 * [UserScopedCache] over the status-snapshot store: clears the
 * [userScopedKeys] set (DI passes [StatusCacheKeys.CLEARED_ON_SIGN_OUT])
 * when the sign-out manager fires. Never throws — [StatusSnapshotStore.clear]
 * swallows storage failures.
 */
class DefaultUserScopedCache(
    private val statusSnapshotStore: StatusSnapshotStore,
    private val userScopedKeys: Set<StatusCacheKey>,
) : UserScopedCache {
    override suspend fun clearUserScopedEntries() {
        statusSnapshotStore.clear(userScopedKeys)
    }
}
