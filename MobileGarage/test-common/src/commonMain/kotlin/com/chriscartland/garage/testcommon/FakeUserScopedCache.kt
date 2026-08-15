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
 */

package com.chriscartland.garage.testcommon

import com.chriscartland.garage.domain.repository.UserScopedCache

/**
 * In-memory [UserScopedCache] for repository tests: records
 * registrations and, on [clearUserScopedEntries], runs every registered
 * reset — the same memory-tier semantics as `DefaultUserScopedCache`
 * (without the disk tier, which is faked separately by the snapshot
 * store fakes).
 */
class FakeUserScopedCache : UserScopedCache {
    private val _registeredResetNames = mutableListOf<String>()
    val registeredResetNames: List<String> get() = _registeredResetNames

    private val resets = mutableListOf<suspend () -> Unit>()

    private var _clearCount = 0
    val clearCount: Int get() = _clearCount

    override fun registerInMemoryReset(
        name: String,
        reset: suspend () -> Unit,
    ) {
        _registeredResetNames.add(name)
        resets.add(reset)
    }

    override suspend fun clearUserScopedEntries() {
        _clearCount++
        resets.toList().forEach { it() }
    }
}
