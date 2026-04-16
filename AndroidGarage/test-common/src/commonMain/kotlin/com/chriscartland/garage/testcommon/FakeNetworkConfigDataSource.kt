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

import com.chriscartland.garage.data.NetworkConfigDataSource
import com.chriscartland.garage.data.NetworkResult
import com.chriscartland.garage.domain.model.ServerConfig

/**
 * Fake [NetworkConfigDataSource] for unit testing.
 *
 * Configure responses with `setX()` methods. Tracks each call via
 * `fetchServerConfigKeys` (ADR-017 Rule 5 — call-list pattern), so tests can
 * assert on the exact `serverConfigKey` passed. The `fetchCount` accessor is
 * a convenience read backed by the list.
 */
class FakeNetworkConfigDataSource : NetworkConfigDataSource {
    private var serverConfigResult: NetworkResult<ServerConfig> = NetworkResult.ConnectionFailed

    private val _fetchServerConfigKeys = mutableListOf<String>()
    val fetchServerConfigKeys: List<String> get() = _fetchServerConfigKeys
    val fetchCount: Int get() = _fetchServerConfigKeys.size

    fun setServerConfigResult(value: NetworkResult<ServerConfig>) {
        serverConfigResult = value
    }

    override suspend fun fetchServerConfig(serverConfigKey: String): NetworkResult<ServerConfig> {
        _fetchServerConfigKeys.add(serverConfigKey)
        return serverConfigResult
    }
}
