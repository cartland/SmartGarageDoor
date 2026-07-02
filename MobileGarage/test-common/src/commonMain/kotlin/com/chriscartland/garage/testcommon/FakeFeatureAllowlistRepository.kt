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

package com.chriscartland.garage.testcommon

import com.chriscartland.garage.domain.model.FeatureAllowlist
import com.chriscartland.garage.domain.repository.FeatureAllowlistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Fake [FeatureAllowlistRepository] for unit tests. Tracks calls to
 * `fetchAllowlist` for assertions; tests configure the response via
 * [setNextFetchResult] and the observable state via [setAllowlist].
 */
class FakeFeatureAllowlistRepository : FeatureAllowlistRepository {
    private val _allowlist = MutableStateFlow<FeatureAllowlist?>(null)
    override val allowlist: StateFlow<FeatureAllowlist?> = _allowlist

    private var fetchCount = 0
    val fetchCallCount: Int get() = fetchCount

    private var nextFetchResult: FeatureAllowlist? = null

    fun setAllowlist(value: FeatureAllowlist?) {
        _allowlist.value = value
    }

    fun setNextFetchResult(value: FeatureAllowlist?) {
        nextFetchResult = value
    }

    override suspend fun fetchAllowlist(): FeatureAllowlist? {
        fetchCount++
        nextFetchResult?.let { _allowlist.value = it }
        return nextFetchResult
    }
}
