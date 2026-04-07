package com.chriscartland.garage.data

import kotlinx.coroutines.flow.Flow

/**
 * Shared interface for application event logging.
 *
 * Platform-specific implementations handle storage (Room on Android,
 * CoreData on iOS). The Android implementation adds writeCsvToUri()
 * as an extension — that method is not part of the shared contract.
 */
interface AppLoggerDataSource {
    suspend fun log(key: String)

    fun countKey(key: String): Flow<Long>
}
