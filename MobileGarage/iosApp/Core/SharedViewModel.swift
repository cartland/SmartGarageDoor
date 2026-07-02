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

import SwiftUI
@preconcurrency import shared

/// Ties a Kotlin `ViewModel`'s lifetime to a SwiftUI view.
///
/// On Android, a `ViewModelStore` (owned by the `NavBackStackEntry`) clears
/// stored ViewModels — cancelling their `viewModelScope` — when the owner is
/// destroyed. iOS has no such host, so a screen holds its ViewModel through a
/// `SharedViewModel` declared as `@StateObject`. When SwiftUI releases the
/// `@StateObject` (the view leaves the tree for good), `deinit` calls
/// `KmpViewModelStore.clear()`, which runs `ViewModel.onCleared()` and cancels
/// the Kotlin `viewModelScope` — so screen coroutines tear down instead of
/// leaking for the life of the process.
///
/// `instance` is the concrete `Default*ViewModel`; screens read its
/// `StateFlow`s (bridged by SKIE) and call its action methods directly.
///
/// Mirrors battery-butler's `*ViewModelWrapper` ownership pattern, generalized
/// to one wrapper because this app's ViewModels expose several `StateFlow`s
/// rather than a single `uiState`.
final class SharedViewModel<VM: ViewModel>: ObservableObject {
    let instance: VM
    private let store = KmpViewModelStore()

    init(_ instance: VM) {
        self.instance = instance
        store.put(key: "vm", viewModel: instance)
    }

    deinit {
        store.clear()
    }
}
