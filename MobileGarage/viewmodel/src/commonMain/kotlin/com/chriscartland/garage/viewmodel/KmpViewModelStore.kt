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

package com.chriscartland.garage.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore

/**
 * Swift-callable owner of a [ViewModel]'s lifetime on iOS.
 *
 * On Android, `ViewModelStore` is owned by the `NavBackStackEntry` /
 * `Activity` and clears the stored ViewModels (cancelling their
 * `viewModelScope`) when the owner is destroyed. iOS has no such host, so
 * the SwiftUI `*ViewModelWrapper` (an `ObservableObject`) creates one of
 * these, [put]s its Kotlin ViewModel in it, and calls [clear] from `deinit`.
 * `clear()` invokes `ViewModel.onCleared()`, which cancels `viewModelScope`
 * — so a screen leaving the view tree tears down its coroutines instead of
 * leaking them for the life of the process.
 *
 * Mirrors battery-butler's `KmpViewModelStore`. Lives in `:viewmodel`
 * commonMain so it rides the framework's `export(project(":viewmodel"))`
 * and is visible to Swift as `KmpViewModelStore`.
 */
class KmpViewModelStore {
    private val store = ViewModelStore()

    fun put(
        key: String,
        viewModel: ViewModel,
    ) {
        store.put(key, viewModel)
    }

    fun clear() {
        store.clear()
    }
}
