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
 * Platform-specific factory for the app's `DataStore<Preferences>`.
 *
 * The `actual` class for each platform is responsible for resolving the
 * file path appropriate to that platform (Android: `context.filesDir`,
 * iOS: `NSDocumentDirectory`, JVM: `java.io.tmpdir`). The shared bytes
 * — the file name, the `PreferenceDataStoreFactory.createWithPath`
 * call — live inside each `actual`, since the path API differs per
 * platform but the construction is one line.
 *
 * **Singleton requirement.** `DataStore<Preferences>` throws
 * `IllegalStateException: There are multiple DataStores active for the
 * same file` when constructed twice for the same path. The `actual`
 * implementations cache the instance via `lazy` so repeated calls to
 * [createPreferencesDataStore] on the **same** factory return the same
 * `DataStore`. Callers must also ensure the **factory itself** is a
 * singleton (a fresh factory has a fresh `lazy`, which would crash on
 * second-DataStore construction). The DI providers are `@Singleton` and
 * `:checkDataStoreSingleton` enforces the annotation.
 *
 * Shape adopted from
 * [battery-butler](https://github.com/cartland/battery-butler) — the
 * cross-platform reference implementation. iOS / JVM `actual` classes
 * are not yet declared on this module; adding them is purely additive.
 */
expect class DataStoreFactory {
    fun createPreferencesDataStore(): DataStore<Preferences>
}

internal const val PREFERENCES_FILE_NAME = "app_settings.preferences_pb"
