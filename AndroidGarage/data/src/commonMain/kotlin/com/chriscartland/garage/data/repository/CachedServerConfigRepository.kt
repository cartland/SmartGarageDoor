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

import co.touchlab.kermit.Logger
import com.chriscartland.garage.data.NetworkConfigDataSource
import com.chriscartland.garage.data.NetworkResult
import com.chriscartland.garage.domain.model.ServerConfig
import com.chriscartland.garage.domain.repository.ServerConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Caches the server config in a [StateFlow] owned by the repository
 * (ADR-022). An always-on fetch runs on [externalScope] at construction;
 * callers read [serverConfig].value for the current cached value and call
 * [fetchServerConfig] to refresh.
 *
 * Network errors are swallowed (the flow stays null) — the repository is a
 * cache, not an I/O surface. Transient data-source exceptions are caught so
 * a flaky network on startup doesn't cascade into a crashed externalScope.
 */
class CachedServerConfigRepository(
    private val networkConfigDataSource: NetworkConfigDataSource,
    private val serverConfigKey: String,
    externalScope: CoroutineScope,
) : ServerConfigRepository {
    private val _serverConfig = MutableStateFlow<ServerConfig?>(null)
    override val serverConfig: StateFlow<ServerConfig?> = _serverConfig

    private val fetchMutex: Mutex = Mutex()

    init {
        externalScope.launch { fetchServerConfig() }
    }

    override suspend fun fetchServerConfig(): ServerConfig? {
        // withLock coalesces concurrent fetch attempts: the second caller
        // waits for the in-flight fetch, then reads the cached result
        // instead of issuing a duplicate network call.
        return fetchMutex.withLock {
            val cached = _serverConfig.value
            if (cached != null) return@withLock cached
            val config = try {
                when (val result = networkConfigDataSource.fetchServerConfig(serverConfigKey)) {
                    is NetworkResult.Success -> result.data
                    is NetworkResult.HttpError -> null
                    NetworkResult.ConnectionFailed -> null
                }
            } catch (e: Exception) {
                Logger.e(e) { "Server config fetch threw — leaving cache null" }
                null
            }
            if (config != null) {
                _serverConfig.value = config
                Logger.i { "serverConfig <- cached (source=GET)" }
            }
            config
        }
    }
}
