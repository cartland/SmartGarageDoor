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
import UserNotifications
@preconcurrency import shared

/// Bridges `DefaultProfileViewModel` to SwiftUI (the Settings tab).
@MainActor
final class SettingsViewModelWrapper: ObservableObject {
    /// Tri-state so the sign-in call to action is never shown while Firebase is
    /// still resolving the session (see `AuthDisplayState`).
    @Published private(set) var authState: AuthDisplayState = .checking
    @Published private(set) var displayName: String?
    @Published private(set) var email: String?
    @Published private(set) var snoozeLabel: String = "Notifications enabled"
    /// Whether a snooze is currently active — drives the Settings row icon
    /// (bell vs bell.slash), mirroring Android's `SnoozeRowState` icon swap.
    @Published private(set) var snoozeSnoozing: Bool = false
    @Published private(set) var snoozeSending: Bool = false
    @Published private(set) var snoozeError: String?
    /// Whether notification authorization is granted. Drives the snooze section:
    /// when `false`, the snooze controls are replaced by a "tap to enable" row
    /// (mirrors Android's `SnoozeRowState.PermissionDenied`). Defaults `true` so
    /// the prompt doesn't flash before the async read resolves. The notification
    /// read/request mirror `HomeViewModelWrapper` (per-UI `UNUserNotificationCenter`,
    /// the analog of Android's runtime permission).
    @Published private(set) var notificationsGranted: Bool = true
    /// Tri-state allowlist flags (`nil` = not yet known). The Developer section
    /// is shown only when `developerAccess == true`; the Functions row inside it
    /// only when `functionListAccess == true` — mirrors Android. See FEATURE_FLAGS.md.
    @Published private(set) var developerAccess: Bool?
    @Published private(set) var functionListAccess: Bool?

    /// Duration options exposed to the UI, in display order.
    let durations: [(label: String, option: SnoozeDurationUIOption)] = [
        ("Don't snooze", .none),
        ("1 hour", .oneHour),
        ("4 hours", .fourHours),
        ("8 hours", .eightHours),
        ("12 hours", .twelveHours),
    ]

    /// Server-config gate for the whole snooze surface, read once from the
    /// component's `AppConfig` (it is fixed for the process). Android hides its
    /// Notifications section on the same flag.
    let snoozeOptionEnabled: Bool

    private let shared: SharedViewModel<DefaultProfileViewModel>
    /// Bumped on every `snoozeState` emission; `refreshSnooze()` waits for it to
    /// move rather than guessing how long a fetch takes.
    private var snoozeRevision: UInt = 0
    private var tasks: [Task<Void, Never>] = []
    private var vm: DefaultProfileViewModel { shared.instance }

    init(component: NativeComponent) {
        snoozeOptionEnabled = component.appConfig.snoozeNotificationsOption
        shared = SharedViewModel(component.profileViewModel)
        applyAuth(vm.authState.value)
        applySnooze(vm.snoozeState.value)
        applyAction(vm.snoozeAction.value)
        developerAccess = vm.developerAccess.value?.boolValue
        functionListAccess = vm.functionListAccess.value?.boolValue
        refreshNotificationPermission()

        // `self?.` per iteration (not `guard let self` up front) — holding self
        // strongly across the never-ending `for await` would keep the wrapper
        // alive forever, so `deinit` (which cancels these tasks) could never
        // run. Mirrors `HomeViewModelWrapper`'s weak-capture pattern.
        tasks.append(Task { @MainActor [weak self] in
            guard let stream = self?.vm.authState else { return }
            for await v in stream { self?.applyAuth(v) }
        })
        tasks.append(Task { @MainActor [weak self] in
            guard let stream = self?.vm.snoozeState else { return }
            for await v in stream { self?.applySnooze(v) }
        })
        tasks.append(Task { @MainActor [weak self] in
            guard let stream = self?.vm.snoozeAction else { return }
            for await v in stream { self?.applyAction(v) }
        })
        tasks.append(Task { @MainActor [weak self] in
            guard let stream = self?.vm.developerAccess else { return }
            for await v in stream { self?.developerAccess = v?.boolValue }
        })
        tasks.append(Task { @MainActor [weak self] in
            guard let stream = self?.vm.functionListAccess else { return }
            for await v in stream { self?.functionListAccess = v?.boolValue }
        })
    }

    private func applyAuth(_ state: AuthState) {
        authState = AuthDisplayState.from(state)
        if case .authenticated(let authed) = onEnum(of: state) {
            // `User.name`/`.email` are Kotlin value classes (DisplayName / Email)
            // erased to their underlying String at the ObjC boundary (typed `id`),
            // so they bridge to Swift as `Any` holding an NSString.
            displayName = authed.user.name as? String
            email = authed.user.email as? String
        } else {
            displayName = nil
            email = nil
        }
    }

    private func applySnooze(_ state: SnoozeState) {
        // Counts emissions so `refreshSnooze()` can tell "the fetch came back"
        // from "nothing has happened yet".
        snoozeRevision &+= 1
        switch onEnum(of: state) {
        case .snoozing(let snoozing):
            let date = Date(timeIntervalSince1970: TimeInterval(snoozing.untilEpochSeconds))
            snoozeLabel = "Snoozing until \(date.formatted(date: .omitted, time: .shortened))"
            snoozeSnoozing = true
        case .loading:
            snoozeLabel = "Loading…"
            snoozeSnoozing = false
        case .notSnoozing:
            snoozeLabel = "Notifications enabled"
            snoozeSnoozing = false
        }
    }

    private func applyAction(_ action: SnoozeAction) {
        switch onEnum(of: action) {
        case .sending:
            snoozeSending = true
            snoozeError = nil
        case .failed(let failed):
            snoozeSending = false
            // Typed copy, verbatim from Android's `snooze_failed_*` strings.
            // `SnoozeAction.Failed` is a shared sealed type, so this switch is
            // exhaustive — a new failure case forces an update here rather than
            // silently falling back to a generic message.
            switch onEnum(of: failed) {
            case .eventChanged:
                snoozeError = "Door state changed before snooze could apply. Try again."
            case .networkError:
                snoozeError = "Snooze did not apply. Check your connection and try again."
            case .notAuthenticated:
                snoozeError = "Sign in to snooze notifications."
            case .missingData:
                snoozeError = "No recent door event available. Try again in a moment."
            }
        case .idle, .succeeded:
            snoozeSending = false
            snoozeError = nil
        }
    }

    func signInWithGoogle() {
        Task { @MainActor in
            // GoogleIdToken is a Kotlin value class erased to its String at the
            // boundary, so the VM's `idToken: Any` parameter takes the raw token.
            if let idToken = await GoogleSignInCoordinator.signIn() {
                vm.signInWithGoogle(idToken: idToken)
            }
        }
    }

    func signOut() { vm.signOut() }

    /// Async so `.refreshable` keeps the system indicator up while the fetch is
    /// in flight; a synchronous body dismisses it instantly and the pull reads
    /// as a no-op.
    ///
    /// The snooze fetch exposes no `LoadingResult`, so completion is inferred
    /// from the next `snoozeState` emission with a short ceiling — a re-fetch
    /// that returns an unchanged value is deduped by `StateFlow` and would
    /// otherwise never resolve.
    func refreshSnooze() async {
        let before = snoozeRevision
        vm.fetchSnoozeStatus()
        let deadline = Date().addingTimeInterval(3)
        while snoozeRevision == before, Date() < deadline {
            try? await Task.sleep(nanoseconds: 100_000_000)
        }
    }

    /// TTL-gated screen-entry revalidate (STATUS_CACHE_PLAN.md D3):
    /// the cached snooze state renders instantly; the shared repo fetches
    /// only when the last server round-trip is stale. Android parity —
    /// its Settings entry calls the same VM method.
    func revalidateSnooze() { vm.revalidateSnoozeIfStale() }

    func snooze(_ option: SnoozeDurationUIOption) { vm.snoozeOpenDoorsNotifications(snoozeDuration: option) }

    /// Reads current notification authorization. `.authorized` / `.provisional` /
    /// `.ephemeral` count as granted; only a hard denial / not-yet-asked surfaces
    /// the "tap to enable" snooze row. Called on init and on screen appear so a
    /// permission change made in iOS Settings is reflected on return.
    func refreshNotificationPermission() {
        UNUserNotificationCenter.current().getNotificationSettings { [weak self] settings in
            let granted = settings.authorizationStatus == .authorized
                || settings.authorizationStatus == .provisional
                || settings.authorizationStatus == .ephemeral
            Task { @MainActor in self?.notificationsGranted = granted }
        }
    }

    /// Requests notification authorization, then refreshes the published state.
    /// Mirrors Android's `launchPermissionRequest()` on the denied snooze row.
    /// (After a prior hard denial iOS won't re-prompt — the same limitation as
    /// Android; the Home permission banner carries the "manage in Settings"
    /// escalation guidance.)
    func requestNotificationPermission() {
        UNUserNotificationCenter.current()
            .requestAuthorization(options: [.alert, .badge, .sound]) { [weak self] _, _ in
                Task { @MainActor in self?.refreshNotificationPermission() }
            }
    }

    deinit { tasks.forEach { $0.cancel() } }
}
