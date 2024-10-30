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

import android.util.Log
import androidx.annotation.Keep
import com.chriscartland.garage.internet.GarageNetworkService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject

@Keep
interface ServerConfigRepository {
    suspend fun getServerConfigCached(): ServerConfig?
    suspend fun fetchServerConfig(): ServerConfig?
}

@Keep
class ServerConfigRepositoryImpl @Inject constructor(
    private val network: GarageNetworkService,
) : ServerConfigRepository {
    private var _serverConfig: ServerConfig? = null

    private val mutex: Mutex = Mutex()

    /**
     * Get server config.
     *
     * Multiple code paths will ask for the server configuration at startup.
     * We only want to fetch it once.
     */
    override suspend fun getServerConfigCached(): ServerConfig? {
        if (_serverConfig != null) {
            return _serverConfig
        }
        mutex.lock()
        val result = _serverConfig ?: fetchServerConfig()
        mutex.unlock()
        return result
    }

    /**
     * Fetch server config.
     *
     * Most callers should call serverConfigCached(). Only call this if the cached config
     * might be out of date. Callers are responsible for rate limiting this request.
     */
    override suspend fun fetchServerConfig(): ServerConfig? {
        try {
            Log.d(TAG, "Fetching server config")
            val response = network.getServerConfig(APP_CONFIG.serverConfigKey)
            if (response.code() != 200) {
                Log.e(TAG, "Response code is ${response.code()}")
                return null
            }
            val body = response.body()
            if (body == null) {
                Log.e(TAG, "Response body is null")
                return null
            }
            if (body.body == null) {
                Log.e(TAG, "body.body is null")
                return null
            }
            if (body.body.buildTimestamp.isNullOrEmpty()) {
                Log.e(TAG, "buildTimestamp is empty")
                return null
            }
            // remoteButtonBuildTimestamp uses a custom get() accessor so it cannot be smart cast
            // in the ServerConfig constructor. Storing in a local variable for the null check.
            val remoteButtonBuildTimestamp = body.body.remoteButtonBuildTimestamp
            if (remoteButtonBuildTimestamp.isNullOrEmpty()) {
                Log.e(TAG, "remoteButtonBuildTimestamp is empty")
                return null
            }
            if (body.body.remoteButtonPushKey.isNullOrEmpty()) {
                Log.e(TAG, "remoteButtonPushKey is empty")
                return null
            }
            return ServerConfig(
                buildTimestamp = body.body.buildTimestamp,
                remoteButtonBuildTimestamp = remoteButtonBuildTimestamp,
                remoteButtonPushKey = body.body.remoteButtonPushKey,
            ).also { newConfig ->
                _serverConfig = newConfig
            }
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "IllegalArgumentException: $e")
        } catch (e: Exception) {
            Log.e(TAG, "Exception: $e")
        }
        return null
    }
}

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class ServerConfigRepositoryModule {
    @Binds
    abstract fun bindServerConfigRepository(
        serverConfigRepository: ServerConfigRepositoryImpl,
    ): ServerConfigRepository
}

private const val TAG = "ServerConfigRepo"