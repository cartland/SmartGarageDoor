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
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.chriscartland.garage.domain.repository.AppSettingsRepository
import com.chriscartland.garage.domain.repository.Setting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * KMP-compatible [AppSettingsRepository] backed by [DataStore].
 *
 * All reads are reactive via [Flow]. All writes are suspend functions.
 * On Android the DataStore file lives in the app's files directory.
 * On iOS it can use the same file-based DataStore.
 */
class DataStoreAppSettings(
    private val dataStore: DataStore<Preferences>,
) : AppSettingsRepository {
    override val fcmDoorTopic: Setting<String> =
        DataStoreStringSetting(dataStore, "FCM_DOOR_TOPIC", "")
    override val profileAppCardExpanded: Setting<Boolean> =
        DataStoreBooleanSetting(dataStore, "PROFILE_APP_CARD_EXPANDED", true)
    override val profileLogCardExpanded: Setting<Boolean> =
        DataStoreBooleanSetting(dataStore, "PROFILE_LOG_CARD_EXPANDED", false)
    override val profileUserCardExpanded: Setting<Boolean> =
        DataStoreBooleanSetting(dataStore, "PROFILE_USER_CARD_EXPANDED", true)
    override val layoutDebugEnabled: Setting<Boolean> =
        DataStoreBooleanSetting(dataStore, "LAYOUT_DEBUG_ENABLED", false)
}

private class DataStoreStringSetting(
    private val dataStore: DataStore<Preferences>,
    override val key: String,
    private val default: String,
) : Setting<String> {
    private val prefKey = stringPreferencesKey(key)

    override val flow: Flow<String> = dataStore.data.map { prefs ->
        prefs[prefKey] ?: default
    }

    override suspend fun set(value: String) {
        dataStore.edit { prefs -> prefs[prefKey] = value }
    }

    override suspend fun restoreDefault() {
        dataStore.edit { prefs -> prefs.remove(prefKey) }
    }
}

private class DataStoreBooleanSetting(
    private val dataStore: DataStore<Preferences>,
    override val key: String,
    private val default: Boolean,
) : Setting<Boolean> {
    private val prefKey = booleanPreferencesKey(key)

    override val flow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[prefKey] ?: default
    }

    override suspend fun set(value: Boolean) {
        dataStore.edit { prefs -> prefs[prefKey] = value }
    }

    override suspend fun restoreDefault() {
        dataStore.edit { prefs -> prefs.remove(prefKey) }
    }
}
