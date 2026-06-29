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
import UIKit
@preconcurrency import shared

/// Bridges `DefaultFunctionListViewModel` to SwiftUI.
///
/// `accessGranted` is tri-state (`Bool?`): gate the buttons on `== true` only —
/// both `nil` (loading / denied) and `false` (server says no) keep the gate
/// closed. See `docs/FEATURE_FLAGS.md`.
@MainActor
final class FunctionListViewModelWrapper: ObservableObject {
    @Published private(set) var accessGranted: Bool?

    /// Test-notification sandbox (diagnostic). `testTopic` is the personal FCM
    /// topic once generated (nil until then — the UI hides the section while
    /// nil, matching Android). `testSubscribed` toggles the subscribe/unsub label.
    @Published private(set) var testTopic: String?
    @Published private(set) var testSubscribed: Bool = false

    private let shared: SharedViewModel<DefaultFunctionListViewModel>
    private var tasks: [Task<Void, Never>] = []
    private var vm: DefaultFunctionListViewModel { shared.instance }

    /// Shared token-fetch usecase for the developer-only copy-auth-token action
    /// (the token isn't VM state, so it's read straight from the graph — same as
    /// Android's `rememberAuthTokenCopier`, which reads the usecase off the component).
    private let getAuthTokenForCopyUseCase: UsecaseGetAuthTokenForCopyUseCase

    init(component: NativeComponent) {
        shared = SharedViewModel(component.functionListViewModel)
        getAuthTokenForCopyUseCase = component.getAuthTokenForCopyUseCase
        accessGranted = vm.accessGranted.value?.boolValue
        applyTestState(vm.testNotificationState.value)

        tasks.append(Task { @MainActor [weak self] in
            guard let self else { return }
            for await v in self.vm.accessGranted { self.accessGranted = v?.boolValue }
        })
        tasks.append(Task { @MainActor [weak self] in
            guard let self else { return }
            for await v in self.vm.testNotificationState { self.applyTestState(v) }
        })
    }

    /// `TestNotificationTopic` is a Kotlin value class erased to its underlying
    /// String at the ObjC boundary (typed `id`), so an optional one bridges as
    /// `Any?` holding an NSString — same pattern as `User.name`/`.email` in the
    /// Settings wrapper.
    private func applyTestState(_ state: TestNotificationSandboxState) {
        testTopic = state.topic as? String
        testSubscribed = state.isSubscribed
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
    func subscribeTestNotification() { vm.subscribeTestNotification() }
    func unsubscribeTestNotification() { vm.unsubscribeTestNotification() }
    func changeTestNotificationTopic() { vm.changeTestNotificationTopic() }

    /// Copy the personal test topic to the clipboard. No sensitivity flag (the
    /// topic is not a secret) — unlike the auth token (see [copyAuthToken]).
    func copyTestTopic() {
        if let topic = testTopic { UIPasteboard.general.string = topic }
    }

    /// Copy the current Firebase ID token to the clipboard (developer-only).
    /// Delegates to the shared [AuthTokenCopier] so the fetch + sensitivity
    /// posture (pasteboard expiration) live in one place; returns the outcome so
    /// the button can flash "Copied" or "Sign in to copy auth token".
    func copyAuthToken() async -> AuthTokenCopyOutcome {
        await AuthTokenCopier.copy(using: getAuthTokenForCopyUseCase)
    }

    deinit { tasks.forEach { $0.cancel() } }
}
