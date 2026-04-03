package com.chriscartland.garage.domain.repository

import com.chriscartland.garage.domain.model.ServerConfig

interface ServerConfigRepository {
    suspend fun getServerConfigCached(): ServerConfig?

    suspend fun fetchServerConfig(): ServerConfig?
}
