package com.chriscartland.garage.datalocal

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
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

    actual fun createPreferencesDataStore(): DataStore<Preferences> = appSettingsInstance

    actual fun createDiagnosticsCountersDataStore(): DataStore<Preferences> = diagnosticsCountersInstance

    private fun createPreferences(fileName: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath(
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
