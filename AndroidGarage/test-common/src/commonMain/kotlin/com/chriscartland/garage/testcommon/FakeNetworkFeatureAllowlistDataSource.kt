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

import com.chriscartland.garage.data.NetworkFeatureAllowlistDataSource
import com.chriscartland.garage.data.NetworkResult
import com.chriscartland.garage.domain.model.FeatureAllowlist

/**
 * Fake [NetworkFeatureAllowlistDataSource] for unit tests. Tracks each
 * call's idToken via [fetchIdTokens] (ADR-017 Rule 5 — call-list pattern)
 * so tests can assert which token the cached repo passed in.
 */
class FakeNetworkFeatureAllowlistDataSource : NetworkFeatureAllowlistDataSource {
    private var fetchResult: NetworkResult<FeatureAllowlist> = NetworkResult.ConnectionFailed

    private val _fetchIdTokens = mutableListOf<String>()
    val fetchIdTokens: List<String> get() = _fetchIdTokens
    val fetchCount: Int get() = _fetchIdTokens.size

    fun setFetchResult(value: NetworkResult<FeatureAllowlist>) {
        fetchResult = value
    }

    override suspend fun fetchAllowlist(idToken: String): NetworkResult<FeatureAllowlist> {
        _fetchIdTokens.add(idToken)
        return fetchResult
    }
}
