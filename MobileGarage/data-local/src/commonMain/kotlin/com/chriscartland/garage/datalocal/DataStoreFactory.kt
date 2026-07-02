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

/**
 * Platform-specific factory for the app's `DataStore<Preferences>`
 * instances.
 *
 * The `actual` class for each platform is responsible for resolving the
 * file path appropriate to that platform (Android: `context.filesDir`,
 * iOS: `NSDocumentDirectory`, JVM: `java.io.tmpdir`). The shared bytes
 * — the file name, the `PreferenceDataStoreFactory.createWithPath`
 * call — live inside each `actual`, since the path API differs per
 * platform but the construction is one line.
 *
 * **One factory, multiple stores by file.** The factory exposes one
 * method per canonical store ([createPreferencesDataStore],
 * [createDiagnosticsCountersDataStore], etc.), each backed by a
 * dedicated file. Each method returns a cached instance — repeated
 * calls return the same `DataStore`. New stores get a new method here.
 *
 * **Singleton requirement.** `DataStore<Preferences>` throws
 * `IllegalStateException: There are multiple DataStores active for the
 * same file` when constructed twice for the same path. The `actual`
 * implementations cache each store via `lazy`. Callers must also
 * ensure the **factory itself** is a singleton (a fresh factory has
 * fresh `lazy` slots, which would crash on second-DataStore
 * construction). The DI providers are `@Singleton` and
 * `:checkDataStoreSingleton` enforces the annotation.
 *
 * Shape adopted from
 * [battery-butler](https://github.com/cartland/battery-butler) — the
 * cross-platform reference implementation. iOS / JVM `actual` classes
 * are not yet declared on this module; adding them is purely additive.
 */
expect class DataStoreFactory {
    fun createPreferencesDataStore(): DataStore<Preferences>

    /**
     * DataStore for the Diagnostics screen's lifetime counters. Lives
     * on a dedicated file so the user-initiated "Clear all
     * diagnostics" action can wipe these counters without touching
     * unrelated app preferences (snooze, FCM topic, etc.).
     */
    fun createDiagnosticsCountersDataStore(): DataStore<Preferences>
}

internal const val PREFERENCES_FILE_NAME = "app_settings.preferences_pb"
internal const val DIAGNOSTICS_COUNTERS_FILE_NAME = "diagnostics_counters.preferences_pb"
