package com.chriscartland.garage.testcommon

import com.chriscartland.garage.domain.model.ServerConfig
import com.chriscartland.garage.domain.repository.ServerConfigRepository

class FakeServerConfigRepository : ServerConfigRepository {
    var serverConfig: ServerConfig? = null

    override suspend fun getServerConfigCached(): ServerConfig? = serverConfig

    override suspend fun fetchServerConfig(): ServerConfig? = serverConfig
}
