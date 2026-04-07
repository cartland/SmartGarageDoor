package com.chriscartland.garage.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Shared interface for application event logging.
 *
 * Platform implementations handle storage (Room on Android, CoreData on iOS).
 */
interface AppLoggerRepository {
    suspend fun log(key: String)

    fun countKey(key: String): Flow<Long>
}
