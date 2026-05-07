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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.chriscartland.garage.domain.repository.DiagnosticsCountersRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * KMP-compatible [DiagnosticsCountersRepository] backed by a dedicated
 * Preferences DataStore. Lives on its own file (not the `app_settings`
 * store) so [resetAll] only wipes diagnostics counters and can never
 * clear unrelated preferences.
 *
 * Counters are stored as `Long` keyed by the `AppLoggerKeys` string —
 * no extra layer of indirection so a new key in `AppLoggerKeys` "just
 * works" without a schema change here.
 */
class DataStoreDiagnosticsCounters(
    private val dataStore: DataStore<Preferences>,
) : DiagnosticsCountersRepository {
    override fun observeCount(key: String): Flow<Long> {
        val prefKey = longPreferencesKey(key)
        return dataStore.data.map { prefs -> prefs[prefKey] ?: 0L }
    }

    override suspend fun increment(key: String) {
        val prefKey = longPreferencesKey(key)
        dataStore.edit { prefs ->
            prefs[prefKey] = (prefs[prefKey] ?: 0L) + 1L
        }
    }

    override suspend fun resetAll() {
        dataStore.edit { prefs -> prefs.clear() }
    }
}
