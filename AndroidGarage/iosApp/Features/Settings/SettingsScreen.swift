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

/// Pushed destinations reachable from the Settings Developer section.
enum SettingsRoute: Hashable {
    case diagnostics
    case functions
}

/// Settings tab — account, snooze, and (allowlisted) a Developer section that
/// pushes to Diagnostics / Functions. Mirrors Android's Settings screen, where
/// those developer surfaces are `developerAccess`-gated rows rather than tabs.
struct SettingsScreen: View {
    private let component: NativeComponent
    @StateObject private var wrapper: SettingsViewModelWrapper

    init(component: NativeComponent) {
        self.component = component
        _wrapper = StateObject(wrappedValue: SettingsViewModelWrapper(component: component))
    }

    var body: some View {
        SettingsContentView(
            signedIn: wrapper.signedIn,
            snoozeLabel: wrapper.snoozeLabel,
            snoozeSending: wrapper.snoozeSending,
            snoozeError: wrapper.snoozeError,
            durations: wrapper.durations,
            developerAccess: wrapper.developerAccess,
            functionListAccess: wrapper.functionListAccess,
            onSignIn: { wrapper.signInWithGoogle() },
            onSignOut: { wrapper.signOut() },
            onSnooze: { wrapper.snooze($0) },
            onRefresh: { wrapper.refreshSnooze() }
        )
        .navigationDestination(for: SettingsRoute.self) { route in
            switch route {
            case .diagnostics:
                DiagnosticsScreen(component: component)
            case .functions:
                FunctionListScreen(component: component)
            }
        }
    }
}

/// Pure Settings content — plain values + actions, renders without a live
/// `NativeComponent`. Captured by the `#Preview`s / snapshot gallery. The
/// Developer section uses value-based `NavigationLink`s so it stays
/// component-free; the screen shell supplies the destinations.
struct SettingsContentView: View {
    let signedIn: Bool
    let snoozeLabel: String
    let snoozeSending: Bool
    let snoozeError: String?
    let durations: [(label: String, option: SnoozeDurationUIOption)]
    let developerAccess: Bool?
    let functionListAccess: Bool?
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

            // Developer section — allowlisted only (parity with Android). The
            // Functions row is additionally gated on functionListAccess.
            if developerAccess == true {
                Section("Developer") {
                    NavigationLink(value: SettingsRoute.diagnostics) {
                        Label("Diagnostics", systemImage: "stethoscope")
                    }
                    if functionListAccess == true {
                        NavigationLink(value: SettingsRoute.functions) {
                            Label("Functions", systemImage: "square.grid.2x2")
                        }
                    }
                }
            }
        }
        .navigationTitle("Settings")
        .refreshable { onRefresh() }
    }
}

// NOTE: a #Preview body is embedded verbatim into the generated PreviewTests, so
// it may only reference symbols visible via `@testable import iosApp` (internal+)
// — never a `private` file-scope helper. Hence the durations are inlined here.

#Preview("Settings signed out") {
    let durations: [(label: String, option: SnoozeDurationUIOption)] = [
        ("Do not snooze", .none), ("1 hour", .oneHour), ("4 hours", .fourHours),
        ("8 hours", .eightHours), ("12 hours", .twelveHours),
    ]
    return NavigationStack {
        SettingsContentView(
            signedIn: false,
            snoozeLabel: "Not snoozing",
            snoozeSending: false,
            snoozeError: nil,
            durations: durations,
            developerAccess: false,
            functionListAccess: false,
            onSignIn: {}, onSignOut: {}, onSnooze: { _ in }, onRefresh: {}
        )
    }
}

#Preview("Settings signed in developer") {
    let durations: [(label: String, option: SnoozeDurationUIOption)] = [
        ("Do not snooze", .none), ("1 hour", .oneHour), ("4 hours", .fourHours),
        ("8 hours", .eightHours), ("12 hours", .twelveHours),
    ]
    return NavigationStack {
        SettingsContentView(
            signedIn: true,
            snoozeLabel: "Snoozing until 9:00 PM",
            snoozeSending: false,
            snoozeError: nil,
            durations: durations,
            developerAccess: true,
            functionListAccess: true,
            onSignIn: {}, onSignOut: {}, onSnooze: { _ in }, onRefresh: {}
        )
    }
}
