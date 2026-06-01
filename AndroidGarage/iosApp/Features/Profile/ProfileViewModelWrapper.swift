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

/// Bridges `DefaultProfileViewModel` to SwiftUI.
@MainActor
final class ProfileViewModelWrapper: ObservableObject {
    @Published private(set) var signedIn: Bool = false
    @Published private(set) var snoozeLabel: String = "Not snoozing"
    @Published private(set) var snoozeSending: Bool = false
    @Published private(set) var snoozeError: String?

    /// Duration options exposed to the UI, in display order.
    let durations: [(label: String, option: SnoozeDurationUIOption)] = [
        ("Do not snooze", .none),
        ("1 hour", .oneHour),
        ("4 hours", .fourHours),
        ("8 hours", .eightHours),
        ("12 hours", .twelveHours),
    ]

    private let shared: SharedViewModel<DefaultProfileViewModel>
    private var tasks: [Task<Void, Never>] = []
    private var vm: DefaultProfileViewModel { shared.instance }

    init(component: NativeComponent) {
        shared = SharedViewModel(component.profileViewModel)
        applyAuth(vm.authState.value)
        applySnooze(vm.snoozeState.value)
        applyAction(vm.snoozeAction.value)

        tasks.append(Task { @MainActor [weak self] in
            guard let self else { return }
            for await v in self.vm.authState { self.applyAuth(v) }
        })
        tasks.append(Task { @MainActor [weak self] in
            guard let self else { return }
            for await v in self.vm.snoozeState { self.applySnooze(v) }
        })
        tasks.append(Task { @MainActor [weak self] in
            guard let self else { return }
            for await v in self.vm.snoozeAction { self.applyAction(v) }
        })
    }

    private func applyAuth(_ state: AuthState) {
        if case .authenticated = onEnum(of: state) {
            signedIn = true
        } else {
            signedIn = false
        }
    }

    private func applySnooze(_ state: SnoozeState) {
        switch onEnum(of: state) {
        case .snoozing(let snoozing):
            let date = Date(timeIntervalSince1970: TimeInterval(snoozing.untilEpochSeconds))
            snoozeLabel = "Snoozing until \(date.formatted(date: .omitted, time: .shortened))"
        case .loading:
            snoozeLabel = "Loading…"
        case .notSnoozing:
            snoozeLabel = "Not snoozing"
        }
    }

    private func applyAction(_ action: SnoozeAction) {
        switch onEnum(of: action) {
        case .sending:
            snoozeSending = true
            snoozeError = nil
        case .failed:
            snoozeSending = false
            snoozeError = "Snooze failed, try again"
        case .idle, .succeeded:
            snoozeSending = false
            snoozeError = nil
        }
    }

    func signOut() { vm.signOut() }
    func refreshSnooze() { vm.fetchSnoozeStatus() }
    func snooze(_ option: SnoozeDurationUIOption) { vm.snoozeOpenDoorsNotifications(snoozeDuration: option) }

    deinit { tasks.forEach { $0.cancel() } }
}
