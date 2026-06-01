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

/// Bridges `DefaultDoorHistoryViewModel` to SwiftUI.
@MainActor
final class HistoryViewModelWrapper: ObservableObject {
    struct Row: Identifiable {
        let id: Int
        let position: String
        let changeTimeSeconds: Int64?
    }

    @Published private(set) var rows: [Row] = []
    @Published private(set) var isLoading: Bool = false

    private let shared: SharedViewModel<DefaultDoorHistoryViewModel>
    private var tasks: [Task<Void, Never>] = []
    private var vm: DefaultDoorHistoryViewModel { shared.instance }

    init(component: NativeComponent) {
        shared = SharedViewModel(component.doorHistoryViewModel)
        apply(vm.recentDoorEvents.value)

        tasks.append(Task { @MainActor [weak self] in
            guard let self else { return }
            for await result in self.vm.recentDoorEvents { self.apply(result) }
        })
    }

    private func apply(_ result: LoadingResult<NSArray>) {
        isLoading = result is LoadingResultLoading
        let events = (result.data as? [DoorEvent]) ?? []
        rows = events.enumerated().map { index, event in
            Row(
                id: index,
                position: (event.doorPosition?.name ?? "UNKNOWN")
                    .replacingOccurrences(of: "_", with: " ")
                    .capitalized,
                changeTimeSeconds: event.lastChangeTimeSeconds?.int64Value
            )
        }
    }

    func refresh() { vm.fetchRecentDoorEvents() }

    deinit { tasks.forEach { $0.cancel() } }
}
