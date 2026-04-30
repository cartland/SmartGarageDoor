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
 * Android `actual` for [DataStoreFactory]. Resolves the file path via
 * `context.filesDir`. The DataStore is cached in [instance] via `lazy`
 * so repeated calls to [createPreferencesDataStore] on the same
 * factory return the same instance. The DI provider must scope this
 * factory as `@Singleton` — a fresh factory has a fresh `lazy`, which
 * would create a second DataStore for the same file (crash).
 */
actual class DataStoreFactory(
    private val context: Context,
) {
    private val instance: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.createWithPath(
            produceFile = {
                context.filesDir
                    .resolve(PREFERENCES_FILE_NAME)
                    .absolutePath
                    .toPath()
            },
        )
    }

    actual fun createPreferencesDataStore(): DataStore<Preferences> = instance
}
