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

/// Bridges `DefaultDiagnosticsViewModel`'s `StateFlow`s to SwiftUI.
///
/// Pattern (shared by every `*ViewModelWrapper`): own a `SharedViewModel` for
/// lifetime, seed each `@Published` synchronously from the flow's `.value`
/// (no initial flicker), then spawn one `@MainActor` task per flow that
/// republishes updates via `for await`. Tasks are cancelled in `deinit`.
@MainActor
final class DiagnosticsViewModelWrapper: ObservableObject {
    struct Counter: Identifiable {
        let id: String
        let label: String
        let value: Int64
    }

    @Published private(set) var counters: [Counter] = []
    @Published private(set) var clearInFlight: Bool = false

    private let shared: SharedViewModel<DefaultDiagnosticsViewModel>
    private var tasks: [Task<Void, Never>] = []

    private var vm: DefaultDiagnosticsViewModel { shared.instance }

    /// Read directly for CSV export — mirrors Android, whose export reads
    /// `app.component.appLoggerRepository` straight (the events aren't on the VM).
    private let appLoggerRepository: AppLoggerRepository

    init(component: NativeComponent) {
        shared = SharedViewModel(component.diagnosticsViewModel)
        appLoggerRepository = component.appLoggerRepository
        rebuildCounters()
        clearInFlight = vm.clearInFlight.value.boolValue

        // Any counter emission re-derives the whole list from current `.value`s.
        observeCounter(vm.initCurrentDoorCount)
        observeCounter(vm.initRecentDoorCount)
        observeCounter(vm.userFetchCurrentDoorCount)
        observeCounter(vm.userFetchRecentDoorCount)
        observeCounter(vm.fcmReceivedDoorCount)
        observeCounter(vm.fcmSubscribeTopicCount)
        observeCounter(vm.exceededExpectedTimeWithoutFcmCount)
        observeCounter(vm.timeWithoutFcmInExpectedRangeCount)

        tasks.append(Task { @MainActor [weak self] in
            guard let self else { return }
            for await value in self.vm.clearInFlight {
                self.clearInFlight = value.boolValue
            }
        })
    }

    private func observeCounter(_ flow: SkieSwiftStateFlow<KotlinLong>) {
        tasks.append(Task { @MainActor [weak self] in
            for await _ in flow { self?.rebuildCounters() }
        })
    }

    private func rebuildCounters() {
        counters = [
            Counter(id: "initCurrent", label: "Init current door", value: vm.initCurrentDoorCount.value.int64Value),
            Counter(id: "initRecent", label: "Init recent door", value: vm.initRecentDoorCount.value.int64Value),
            Counter(id: "userCurrent", label: "User fetch current", value: vm.userFetchCurrentDoorCount.value.int64Value),
            Counter(id: "userRecent", label: "User fetch recent", value: vm.userFetchRecentDoorCount.value.int64Value),
            Counter(id: "fcmReceived", label: "FCM door events received", value: vm.fcmReceivedDoorCount.value.int64Value),
            Counter(id: "fcmSubscribe", label: "FCM topic subscribes", value: vm.fcmSubscribeTopicCount.value.int64Value),
            Counter(id: "fcmExceeded", label: "Exceeded expected time without FCM", value: vm.exceededExpectedTimeWithoutFcmCount.value.int64Value),
            Counter(id: "fcmInRange", label: "Time without FCM in range", value: vm.timeWithoutFcmInExpectedRangeCount.value.int64Value),
        ]
    }

    func clearDiagnostics() {
        vm.clearDiagnostics()
    }

    /// Build the export CSV from the shared `AppLogCsv` builder (the same format
    /// Android writes). Reads the first emission of the repo's `getAll()` flow —
    /// the current snapshot of logged events (ordered ascending). Returns the
    /// header-only CSV if the flow somehow never emits.
    func buildCsv() async -> String {
        for await events in appLoggerRepository.getAll() {
            return AppLogCsv.shared.build(events: events)
        }
        return AppLogCsv.shared.HEADER
    }

    deinit {
        tasks.forEach { $0.cancel() }
    }
}
