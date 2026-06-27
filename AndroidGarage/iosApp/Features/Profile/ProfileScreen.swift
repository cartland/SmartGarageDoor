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

/// Profile tab — account + snooze. Mirrors Android's `ProfileContent`.
/// Google Sign-In (Phase C) presents via `GoogleSignInCoordinator` and hands the
/// token to the shared ViewModel; the account row reflects the real auth state.
struct ProfileScreen: View {
    @StateObject private var wrapper: ProfileViewModelWrapper

    init(component: NativeComponent) {
        _wrapper = StateObject(wrappedValue: ProfileViewModelWrapper(component: component))
    }

    var body: some View {
        ProfileContentView(
            signedIn: wrapper.signedIn,
            snoozeLabel: wrapper.snoozeLabel,
            snoozeSending: wrapper.snoozeSending,
            snoozeError: wrapper.snoozeError,
            durations: wrapper.durations,
            onSignIn: { wrapper.signInWithGoogle() },
            onSignOut: { wrapper.signOut() },
            onSnooze: { wrapper.snooze($0) },
            onRefresh: { wrapper.refreshSnooze() }
        )
    }
}

/// Pure Profile content — plain values + actions, renders without a live
/// `NativeComponent`. Captured by the `#Preview`s / snapshot gallery.
struct ProfileContentView: View {
    let signedIn: Bool
    let snoozeLabel: String
    let snoozeSending: Bool
    let snoozeError: String?
    let durations: [(label: String, option: SnoozeDurationUIOption)]
    let onSignIn: () -> Void
    let onSignOut: () -> Void
    let onSnooze: (SnoozeDurationUIOption) -> Void
    let onRefresh: () -> Void

    var body: some View {
        List {
            Section("Account") {
                Text(signedIn ? "Signed in" : "Signed out")
                    .foregroundStyle(.secondary)
                if signedIn {
                    Button("Sign out", role: .destructive) { onSignOut() }
                } else {
                    Button("Sign in with Google") { onSignIn() }
                }
            }

            Section("Snooze notifications") {
                HStack {
                    Text(snoozeLabel)
                    Spacer()
                    if snoozeSending { ProgressView().controlSize(.small) }
                }
                if let error = snoozeError {
                    Text(error).font(.footnote).foregroundStyle(GarageColors.statusWarning)
                }
                ForEach(durations, id: \.label) { entry in
                    Button(entry.label) { onSnooze(entry.option) }
                        .disabled(snoozeSending)
                }
            }
        }
        .navigationTitle("Profile")
        .refreshable { onRefresh() }
    }
}

// NOTE: a #Preview body is embedded verbatim into the generated PreviewTests, so
// it may only reference symbols visible via `@testable import iosApp` (internal+)
// — never a `private` file-scope helper. Hence the durations are inlined here.

#Preview("Profile signed out") {
    let durations: [(label: String, option: SnoozeDurationUIOption)] = [
        ("Do not snooze", .none), ("1 hour", .oneHour), ("4 hours", .fourHours),
        ("8 hours", .eightHours), ("12 hours", .twelveHours),
    ]
    return NavigationStack {
        ProfileContentView(
            signedIn: false,
            snoozeLabel: "Not snoozing",
            snoozeSending: false,
            snoozeError: nil,
            durations: durations,
            onSignIn: {}, onSignOut: {}, onSnooze: { _ in }, onRefresh: {}
        )
    }
}

#Preview("Profile signed in snoozing") {
    let durations: [(label: String, option: SnoozeDurationUIOption)] = [
        ("Do not snooze", .none), ("1 hour", .oneHour), ("4 hours", .fourHours),
        ("8 hours", .eightHours), ("12 hours", .twelveHours),
    ]
    return NavigationStack {
        ProfileContentView(
            signedIn: true,
            snoozeLabel: "Snoozing until 9:00 PM",
            snoozeSending: false,
            snoozeError: nil,
            durations: durations,
            onSignIn: {}, onSignOut: {}, onSnooze: { _ in }, onRefresh: {}
        )
    }
}
