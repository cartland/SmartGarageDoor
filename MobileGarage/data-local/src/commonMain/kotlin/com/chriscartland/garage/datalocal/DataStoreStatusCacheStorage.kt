/*
 * Copyright 2026 Chris Cartland. All rights reserved.
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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.chriscartland.garage.data.statuscache.StatusCacheStorage
import kotlinx.coroutines.flow.first

/**
 * [StatusCacheStorage] over the dedicated status-cache Preferences
 * DataStore (`DataStoreFactory.createStatusCacheDataStore()`). Pure
 * string persistence — envelope encoding and failure policy live in
 * `:data`'s `DefaultStatusSnapshotStore`. May throw on IO failure;
 * the typed store above catches.
 */
class DataStoreStatusCacheStorage(
    private val dataStore: DataStore<Preferences>,
) : StatusCacheStorage {
    override suspend fun get(key: String): String? = dataStore.data.first()[stringPreferencesKey(key)]

    override suspend fun put(
        key: String,
        value: String,
    ) {
        dataStore.edit { prefs -> prefs[stringPreferencesKey(key)] = value }
    }

    override suspend fun remove(keys: Set<String>) {
        if (keys.isEmpty()) return
        dataStore.edit { prefs ->
            keys.forEach { prefs.remove(stringPreferencesKey(it)) }
        }
    }
}
