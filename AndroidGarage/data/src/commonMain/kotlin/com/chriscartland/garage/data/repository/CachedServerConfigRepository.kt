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

package com.chriscartland.garage.data.repository

import com.chriscartland.garage.data.NetworkConfigDataSource
import com.chriscartland.garage.data.NetworkResult
import com.chriscartland.garage.domain.model.ServerConfig
import com.chriscartland.garage.domain.repository.ServerConfigRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CachedServerConfigRepository(
    private val networkConfigDataSource: NetworkConfigDataSource,
    private val serverConfigKey: String,
) : ServerConfigRepository {
    private var serverConfig: ServerConfig? = null

    private val mutex: Mutex = Mutex()

    override suspend fun getServerConfigCached(): ServerConfig? {
        serverConfig?.let { return it }
        // withLock guarantees unlock on any exit path including cancellation.
        // The bare lock()/unlock() form leaked the mutex on any exception from
        // fetchServerConfig(), permanently blocking every future caller.
        return mutex.withLock { serverConfig ?: fetchServerConfig() }
    }

    override suspend fun fetchServerConfig(): ServerConfig? {
        val config = when (val result = networkConfigDataSource.fetchServerConfig(serverConfigKey)) {
            is NetworkResult.Success -> result.data
            is NetworkResult.HttpError -> null
            NetworkResult.ConnectionFailed -> null
        }
        if (config != null) {
            serverConfig = config
        }
        return config
    }
}
