package com.chriscartland.garage.data

import com.chriscartland.garage.domain.model.ServerConfig

/**
 * Pure data source interface for fetching server configuration.
 *
 * Implementations handle HTTP communication and map responses
 * to domain [ServerConfig] objects.
 */
interface NetworkConfigDataSource {
    /**
     * Fetch the server configuration.
     *
     * @param serverConfigKey API key for authentication
     * @return The server config, or null if the request failed or config is invalid
     */
    suspend fun fetchServerConfig(serverConfigKey: String): ServerConfig?
}
