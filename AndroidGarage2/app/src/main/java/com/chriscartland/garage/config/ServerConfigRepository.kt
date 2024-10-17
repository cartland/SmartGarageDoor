package com.chriscartland.garage.config

import android.util.Log
import com.chriscartland.garage.internet.GarageNetworkService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject

interface ServerConfigRepository {
    suspend fun getServerConfigCached(): ServerConfig?
    suspend fun fetchServerConfig(): ServerConfig?
}

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
        val tag = "fetchServerConfig"
        try {
            Log.d(tag, "Fetching server config")
            val response = network.getServerConfig(APP_CONFIG.serverConfigKey)
            if (response.code() != 200) {
                Log.e(tag, "Response code is ${response.code()}")
                return null
            }
            val body = response.body()
            if (body == null) {
                Log.e(tag, "Response body is null")
                return null
            }
            if (body.body == null) {
                Log.e(tag, "body.body is null")
                return null
            }
            if (body.body.buildTimestamp.isNullOrEmpty()) {
                Log.e(tag, "buildTimestamp is empty")
                return null
            }
            // remoteButtonBuildTimestamp uses a custom get() accessor so it cannot be smart cast
            // in the ServerConfig constructor. Storing in a local variable for the null check.
            val remoteButtonBuildTimestamp = body.body.remoteButtonBuildTimestamp
            if (remoteButtonBuildTimestamp.isNullOrEmpty()) {
                Log.e(tag, "remoteButtonBuildTimestamp is empty")
                return null
            }
            if (body.body.remoteButtonPushKey.isNullOrEmpty()) {
                Log.e(tag, "remoteButtonPushKey is empty")
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
            Log.e(tag, "IllegalArgumentException: $e")
        } catch (e: Exception) {
            Log.e(tag, "Exception: $e")
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
