package com.chriscartland.garage.data

import com.chriscartland.garage.domain.model.ServerConfig

/**
 * Data source interface for fetching server configuration.
 *
 * Returns [NetworkResult] so callers handle errors with exhaustive `when`.
 */
interface NetworkConfigDataSource {
    suspend fun fetchServerConfig(serverConfigKey: String): NetworkResult<ServerConfig>
}
