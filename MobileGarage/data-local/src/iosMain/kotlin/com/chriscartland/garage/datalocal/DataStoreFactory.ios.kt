package com.chriscartland.garage.datalocal

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * iOS `actual` for [DataStoreFactory]. Resolves file paths via
 * `NSDocumentDirectory`. Each canonical store is cached via `lazy`,
 * mirroring the Android impl. The DI provider must scope this
 * factory as `@Singleton` — a fresh factory has fresh `lazy` slots,
 * which would create a second DataStore for the same file (crash).
 */
actual class DataStoreFactory {
    private val appSettingsInstance: DataStore<Preferences> by lazy {
        createPreferences(PREFERENCES_FILE_NAME)
    }

    private val diagnosticsCountersInstance: DataStore<Preferences> by lazy {
        createPreferences(DIAGNOSTICS_COUNTERS_FILE_NAME)
    }

    private val statusCacheInstance: DataStore<Preferences> by lazy {
        // Corruption handler: a corrupted cache file self-heals to empty
        // instead of throwing on every launch — the cache is re-earned
        // from the network, so losing it is always safe. The two stores
        // above deliberately have NO handler: they are the source of
        // truth for their data, so silently discarding them on
        // corruption is a different product tradeoff.
        createPreferences(
            STATUS_CACHE_FILE_NAME,
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        )
    }

    actual fun createPreferencesDataStore(): DataStore<Preferences> = appSettingsInstance

    actual fun createDiagnosticsCountersDataStore(): DataStore<Preferences> = diagnosticsCountersInstance

    actual fun createStatusCacheDataStore(): DataStore<Preferences> = statusCacheInstance

    private fun createPreferences(
        fileName: String,
        corruptionHandler: ReplaceFileCorruptionHandler<Preferences>? = null,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath(
            corruptionHandler = corruptionHandler,
            produceFile = {
                "${iosDocumentsDirectory()}/$fileName".toPath()
            },
        )

    @OptIn(ExperimentalForeignApi::class)
    private fun iosDocumentsDirectory(): String {
        val documentDirectory: NSURL = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        )!!
        return requireNotNull(documentDirectory.path)
    }
}
