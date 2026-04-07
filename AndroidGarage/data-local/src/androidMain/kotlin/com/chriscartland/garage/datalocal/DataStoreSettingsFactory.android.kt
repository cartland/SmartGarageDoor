package com.chriscartland.garage.datalocal

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.chriscartland.garage.domain.repository.AppSettingsRepository
import okio.Path.Companion.toPath

/**
 * Android-specific DataStore factory.
 *
 * Creates a single [AppSettingsRepository] backed by DataStore.
 * Must be a singleton — multiple DataStore instances for the same file crash.
 */
object DataStoreSettingsFactory {
    @Volatile
    private var instance: AppSettingsRepository? = null

    fun create(context: Context): AppSettingsRepository =
        instance ?: synchronized(this) {
            instance ?: DataStoreAppSettings(createDataStore(context)).also { instance = it }
        }

    private fun createDataStore(context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath {
            context.filesDir
                .resolve("app_settings.preferences_pb")
                .absolutePath
                .toPath()
        }
}
