package com.chriscartland.garage.domain.repository

import com.chriscartland.garage.domain.model.ServerConfig
import kotlinx.coroutines.flow.StateFlow

/**
 * Server configuration owner.
 *
 * Per ADR-022, the repository owns the authoritative
 * `StateFlow<ServerConfig?>` — an always-on collector populates it on
 * construction and [fetchServerConfig] refreshes it.
 */
interface ServerConfigRepository {
    /** Observation: latest cached config (null until the first fetch succeeds). */
    val serverConfig: StateFlow<ServerConfig?>

    /**
     * Force-fetch the server config. On success, writes the result into
     * [serverConfig] and returns it. Returns null on failure (the flow is
     * left unchanged).
     */
    suspend fun fetchServerConfig(): ServerConfig?
}
