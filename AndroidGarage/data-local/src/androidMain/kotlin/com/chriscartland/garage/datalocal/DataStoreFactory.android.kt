/*
 * Copyright 2024 Chris Cartland. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.chriscartland.garage.datalocal

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath

/**
 * Android `actual` for [DataStoreFactory]. Resolves file paths via
 * `context.filesDir`. Each canonical store is cached via `lazy`, so
 * repeated calls to the same `create*` method return the same
 * instance. The DI provider must scope this factory as `@Singleton` —
 * a fresh factory has fresh `lazy` slots, which would create a second
 * DataStore for the same file (crash).
 */
actual class DataStoreFactory(
    private val context: Context,
) {
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
                context.filesDir
                    .resolve(fileName)
                    .absolutePath
                    .toPath()
            },
        )
}
