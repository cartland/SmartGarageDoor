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

/// Bridges `DefaultHomeViewModel` to SwiftUI. See `DiagnosticsViewModelWrapper`
/// for the shared observe pattern.
@MainActor
final class HomeViewModelWrapper: ObservableObject {
    @Published private(set) var signedIn: Bool = false
    @Published private(set) var doorPosition: DoorPosition = .unknown
    @Published private(set) var doorMessage: String?
    /// Localized text for the typed `DoorWarning` exposed by the shared VM
    /// (ADR-031), or `nil` when the current state warrants no warning. The
    /// shared layer emits a *typed* warning; this wrapper resolves it to a
    /// string here (iOS's localization boundary) — mirrors Android's
    /// `doorWarningText` Composable + `strings.xml`.
    @Published private(set) var warningText: String?
    @Published private(set) var lastChangeTimeSeconds: Int64?
    @Published private(set) var isCheckInStale: Bool = false
    @Published private(set) var buttonStateLabel: String = "Ready"
    @Published private(set) var buttonHealthLabel: String = "Unknown"

    private let shared: SharedViewModel<DefaultHomeViewModel>
    private var tasks: [Task<Void, Never>] = []
    private var vm: DefaultHomeViewModel { shared.instance }

    init(component: NativeComponent) {
        shared = SharedViewModel(component.homeViewModel)
        applyAuth(vm.authState.value)
        applyDoor(vm.currentDoorEvent.value)
        applyWarning(vm.warning.value)
        applyButton(vm.buttonState.value)
        applyHealth(vm.buttonHealthDisplay.value)
        isCheckInStale = vm.isCheckInStale.value.boolValue

        tasks.append(Task { @MainActor [weak self] in
            for await v in self!.vm.authState { self?.applyAuth(v) }
        })
        tasks.append(Task { @MainActor [weak self] in
            for await v in self!.vm.currentDoorEvent { self?.applyDoor(v) }
        })
        tasks.append(Task { @MainActor [weak self] in
            for await v in self!.vm.warning { self?.applyWarning(v) }
        })
        tasks.append(Task { @MainActor [weak self] in
            for await v in self!.vm.buttonState { self?.applyButton(v) }
        })
        tasks.append(Task { @MainActor [weak self] in
            for await v in self!.vm.buttonHealthDisplay { self?.applyHealth(v) }
        })
        tasks.append(Task { @MainActor [weak self] in
            for await v in self!.vm.isCheckInStale { self?.isCheckInStale = v.boolValue }
        })
    }

    private func applyAuth(_ state: AuthState) {
        if case .authenticated = onEnum(of: state) {
            signedIn = true
        } else {
            signedIn = false
        }
    }

    private func applyDoor(_ result: LoadingResult<DoorEvent>) {
        let event = result.data
        doorPosition = event?.doorPosition ?? .unknown
        doorMessage = event?.message
        lastChangeTimeSeconds = event?.lastChangeTimeSeconds?.int64Value
    }

    /// Resolves the shared typed `DoorWarning` to a localized string. The four
    /// fallback strings mirror Android's `home_warning_*` resources verbatim so
    /// both platforms read identically; a server-supplied message renders as-is.
    private func applyWarning(_ warning: DoorWarning?) {
        guard let warning else {
            warningText = nil
            return
        }
        switch onEnum(of: warning) {
        case .serverMessage(let message):
            warningText = message.text
        case .openingTooLong:
            warningText = "Opening, taking longer than expected"
        case .closingTooLong:
            warningText = "Closing, taking longer than expected"
        case .openMisaligned:
            warningText = "Door is open and misaligned"
        case .sensorConflict:
            warningText = "Sensor conflict. Check the door."
        }
    }

    private func applyButton(_ state: RemoteButtonState) {
        buttonStateLabel = HomeViewModelWrapper.label(for: state)
    }

    private func applyHealth(_ display: UsecaseButtonHealthDisplay) {
        switch onEnum(of: display) {
        case .offline(let offline):
            buttonHealthLabel = "Offline (\(offline.durationLabel))"
        case .online:
            buttonHealthLabel = "Online"
        case .loading:
            buttonHealthLabel = "Checking…"
        case .unauthorized:
            buttonHealthLabel = "Sign in to see device health"
        case .unknown:
            buttonHealthLabel = "Unknown"
        }
    }

    private static func label(for state: RemoteButtonState) -> String {
        switch onEnum(of: state) {
        case .ready: return "Tap to open / close"
        case .preparing: return "Preparing…"
        case .awaitingConfirmation: return "Tap again to confirm"
        case .cancelled: return "Cancelled"
        case .sendingToServer: return "Sending…"
        case .sendingToDoor: return "Waiting for door…"
        case .succeeded: return "Done"
        case .serverFailed: return "Server error"
        case .doorFailed: return "Door did not move"
        }
    }

    func onButtonTap() { vm.onButtonTap() }
    func refresh() {
        vm.fetchCurrentDoorEvent()
        vm.refreshButtonHealth()
    }

    deinit { tasks.forEach { $0.cancel() } }
}
