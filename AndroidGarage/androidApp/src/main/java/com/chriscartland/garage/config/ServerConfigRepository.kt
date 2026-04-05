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

package com.chriscartland.garage.config

import com.chriscartland.garage.data.NetworkConfigDataSource
import com.chriscartland.garage.domain.model.ServerConfig
import kotlinx.coroutines.sync.Mutex

interface ServerConfigRepository {
    suspend fun getServerConfigCached(): ServerConfig?

    suspend fun fetchServerConfig(): ServerConfig?
}

class ServerConfigRepositoryImpl(
    private val networkConfigDataSource: NetworkConfigDataSource,
) : ServerConfigRepository {
    private var serverConfig: ServerConfig? = null

    private val mutex: Mutex = Mutex()

    override suspend fun getServerConfigCached(): ServerConfig? {
        if (serverConfig != null) {
            return serverConfig
        }
        mutex.lock()
        val result = serverConfig ?: fetchServerConfig()
        mutex.unlock()
        return result
    }

    override suspend fun fetchServerConfig(): ServerConfig? {
        val config = networkConfigDataSource.fetchServerConfig(APP_CONFIG.serverConfigKey)
        if (config != null) {
            serverConfig = config
        }
        return config
    }
}
