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

package com.chriscartland.garage.wear.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.chriscartland.garage.data.statuscache.StatusCacheStorage
import kotlinx.coroutines.flow.first

/**
 * The watch's `preferences_pb` file for the status-snapshot cache.
 *
 * NOTE: this name must be excluded from Android cloud backup in BOTH
 * `wearApp/src/main/res/xml/backup_rules.xml` and
 * `data_extraction_rules.xml` — the exclude lists are per-exact-filename, so
 * a new file silently fails OPEN into Google Drive Auto Backup (security
 * posture M6). Enforced by the `checkWearBackupRulesExcludes` Gradle task.
 */
internal const val WEAR_STATUS_CACHE_FILE_NAME = "wear_status_cache.preferences_pb"

/**
 * The watch's one and only status-cache `DataStore<Preferences>`.
 *
 * **Why a process-wide `object` rather than a DI provider.**
 * `DataStore<Preferences>` throws `IllegalStateException: There are multiple
 * DataStores active for the same file` when constructed twice for one path,
 * and on the watch the callers are not all in one place: the app has a DI
 * component, but a `TileService` or a complication provider is started by the
 * SYSTEM, in a process that may exist only to answer that one request. The
 * phone solves this with a `@Singleton`-scoped factory plus the
 * `:checkDataStoreSingleton` lint, which works because every caller there
 * goes through `AppComponent`. Here the same discipline would be a rule to
 * remember rather than a fact about the code, and the failure mode is a
 * crash, not a drift.
 *
 * A Kotlin `object` holding the instance in a `lazy` cannot be constructed
 * twice however many entry points the system invents, so the crash is
 * unreachable instead of merely discouraged. The DI graph then takes the
 * resulting [StatusCacheStorage] as a constructor dependency, exactly the way
 * it takes `authBridge` — a platform leaf built outside and injected in,
 * which also keeps `android.content.Context` out of `WearComponent` and lets
 * `WearComponentGraphTest` keep constructing the real graph on the JVM.
 *
 * Holds the APPLICATION context (never an Activity), which lives as long as
 * the process does.
 *
 * **Why the watch does not use `:data-local`'s `DataStoreFactory`.** That is
 * where the phone's and iOS's DataStore wiring lives and it would be the
 * obvious home — but it also carries Room, and the watch deliberately has no
 * database (docs/WEAR_OS.md § Architecture). Reaching twenty lines of
 * plumbing would drag the whole persistence stack onto a device that wants
 * none of it. Same trade-off, and same shape, as `WearFreshnessTint`'s
 * documented near-copy of the phone's: the DUPLICATION IS THE PLUMBING, and
 * everything that decides behaviour — the envelope, the schema-version gate,
 * the never-throws policy, the clock-skew rule — stays in `:data`'s shared
 * `DefaultStatusSnapshotStore`, which every platform runs.
 */
object WearStatusCache {
    @Volatile
    private var instance: DataStore<Preferences>? = null

    /**
     * The process's status-cache storage, creating the backing DataStore on
     * first call. Safe to call from any entry point — the app, a tile
     * service, a complication provider — and from any thread.
     */
    fun storage(context: Context): StatusCacheStorage = WearStatusCacheStorage(dataStore(context))

    private fun dataStore(context: Context): DataStore<Preferences> =
        instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }

    private fun create(applicationContext: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            // A corrupted cache file self-heals to empty instead of throwing
            // on every launch. Always safe here: the door snapshot is only
            // ever a copy of something the server will hand back on the next
            // request, so losing it costs one fetch — where the alternative
            // is a watch that cannot start.
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            produceFile = { applicationContext.filesDir.resolve(WEAR_STATUS_CACHE_FILE_NAME) },
        )
}

/**
 * [StatusCacheStorage] over a Preferences DataStore.
 *
 * Pure string persistence — the envelope encoding and the failure policy live
 * in `:data`'s `DefaultStatusSnapshotStore`, which wraps this. MAY throw on
 * IO failure; that is the contract, and the typed store above catches.
 *
 * Mirrors `:data-local`'s `DataStoreStatusCacheStorage` line for line; see
 * [WearStatusCache] for why the watch keeps its own copy.
 */
class WearStatusCacheStorage(
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
