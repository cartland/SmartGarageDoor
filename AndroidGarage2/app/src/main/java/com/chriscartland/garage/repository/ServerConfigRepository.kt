package com.chriscartland.garage.repository

import android.util.Log
import com.chriscartland.garage.APP_CONFIG
import com.chriscartland.garage.internet.GarageNetworkService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

data class ServerConfig(
    val buildTimestamp: String,
    val remoteButtonBuildTimestamp: String,
    val remoteButtonPushKey: String,
)

class ServerConfigRepository @Inject constructor(
    private val network: GarageNetworkService,
) {
    private var _serverConfig: ServerConfig? = null

    suspend fun serverConfigCached(): ServerConfig? {
        return _serverConfig ?: fetchServerConfig().also { _serverConfig = it }
    }

    /**
     * Fetch server config.
     */
    suspend fun fetchServerConfig(): ServerConfig? {
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

@EntryPoint
@InstallIn(SingletonComponent::class)
@Suppress("unused")
interface ServerConfigRepositoryEntryPoint {
    fun serverConfigRepository(): ServerConfigRepository
}
