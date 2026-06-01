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

/// Bridges `DefaultFunctionListViewModel` to SwiftUI.
///
/// `accessGranted` is tri-state (`Bool?`): gate the buttons on `== true` only —
/// both `nil` (loading / denied) and `false` (server says no) keep the gate
/// closed. See `docs/FEATURE_FLAGS.md`.
@MainActor
final class FunctionListViewModelWrapper: ObservableObject {
    @Published private(set) var accessGranted: Bool?

    private let shared: SharedViewModel<DefaultFunctionListViewModel>
    private var tasks: [Task<Void, Never>] = []
    private var vm: DefaultFunctionListViewModel { shared.instance }

    init(component: NativeComponent) {
        shared = SharedViewModel(component.functionListViewModel)
        accessGranted = vm.accessGranted.value?.boolValue

        tasks.append(Task { @MainActor [weak self] in
            guard let self else { return }
            for await v in self.vm.accessGranted { self.accessGranted = v?.boolValue }
        })
    }

    func openOrCloseDoor() { vm.openOrCloseDoor() }
    func refreshDoorStatus() { vm.refreshDoorStatus() }
    func refreshDoorHistory() { vm.refreshDoorHistory() }
    func refreshSnoozeStatus() { vm.refreshSnoozeStatus() }
    func refreshButtonHealth() { vm.refreshButtonHealth() }
    func snoozeForOneHour() { vm.snoozeNotificationsForOneHour() }
    func registerFcm() { vm.registerFcm() }
    func deregisterFcm() { vm.deregisterFcm() }
    func clearDiagnostics() { vm.clearDiagnostics() }
    func pruneDiagnosticsLog() { vm.pruneDiagnosticsLog() }

    deinit { tasks.forEach { $0.cancel() } }
}
