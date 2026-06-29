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

    private static var appVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "unknown"
    }

    private static var appBuild: String {
        Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "unknown"
    }

    private static var appPackage: String {
        Bundle.main.bundleIdentifier ?? "unknown"
    }

    /// Build timestamp sourced from the app bundle — the executable's
    /// modification date is set at build/link time, so it's the iOS equivalent
    /// of Android's "Built" row. Formatted to match Android
    /// (`yyyy-MM-dd HH:mm:ss UTC`). Falls back to "unknown" if the bundle
    /// attributes are unreadable.
    private static var appBuilt: String {
        guard let path = Bundle.main.executablePath,
              let attrs = try? FileManager.default.attributesOfItem(atPath: path),
              let date = attrs[.modificationDate] as? Date else { return "unknown" }
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss 'UTC'"
        formatter.timeZone = TimeZone(identifier: "UTC")
        return formatter.string(from: date)
    }

    var body: some View {
        SettingsContentView(
            signedIn: wrapper.signedIn,
            displayName: wrapper.displayName,
            email: wrapper.email,
            snoozeLabel: wrapper.snoozeLabel,
            snoozeSending: wrapper.snoozeSending,
            snoozeError: wrapper.snoozeError,
            durations: wrapper.durations,
            developerAccess: wrapper.developerAccess,
            functionListAccess: wrapper.functionListAccess,
            appVersion: Self.appVersion,
            appBuild: Self.appBuild,
            appPackage: Self.appPackage,
            appBuilt: Self.appBuilt,
            notificationsGranted: wrapper.notificationsGranted,
            onSignIn: { wrapper.signInWithGoogle() },
            onSignOut: { wrapper.signOut() },
            onSnooze: { wrapper.snooze($0) },
            onRefresh: { wrapper.refreshSnooze() },
            onEnableNotifications: { wrapper.requestNotificationPermission() }
        )
        .navigationDestination(for: SettingsRoute.self) { route in
            switch route {
            case .diagnostics:
                DiagnosticsScreen(component: component)
            case .functions:
                FunctionListScreen(component: component)
            }
        }
        // Re-read permission on appear so a change made in iOS Settings (while
        // the app was backgrounded) is reflected when the user returns.
        .onAppear { wrapper.refreshNotificationPermission() }
    }
}

/// Pure Settings content — plain values + actions, renders without a live
/// `NativeComponent`. Captured by the `#Preview`s / snapshot gallery. The
/// Developer section uses value-based `NavigationLink`s so it stays
/// component-free; the screen shell supplies the destinations.
struct SettingsContentView: View {
    let signedIn: Bool
    let displayName: String?
    let email: String?
    let snoozeLabel: String
    let snoozeSending: Bool
    let snoozeError: String?
    let durations: [(label: String, option: SnoozeDurationUIOption)]
    let developerAccess: Bool?
    let functionListAccess: Bool?
    let appVersion: String
    let appBuild: String
    let appPackage: String
    let appBuilt: String
    var notificationsGranted: Bool = true
    let onSignIn: () -> Void
    let onSignOut: () -> Void
    let onSnooze: (SnoozeDurationUIOption) -> Void
    let onRefresh: () -> Void
    var onEnableNotifications: () -> Void = {}

    var body: some View {
        List {
            Section("Account") {
                if signedIn {
                    VStack(alignment: .leading, spacing: GarageSpacing.tight) {
                        Text(displayName ?? "Signed in")
                            .font(.headline)
                        if let email {
                            Text(email)
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                    }
                    Button("Sign out", role: .destructive) { onSignOut() }
                } else {
                    Text("Signed out")
                        .foregroundStyle(.secondary)
                    Button("Sign in with Google") { onSignIn() }
                }
            }

            Section("Snooze notifications") {
                if notificationsGranted {
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
                } else {
                    // Notifications denied — snoozing is meaningless without them,
                    // so replace the controls with a tap-to-enable row (mirrors
                    // Android's SnoozeRowState.PermissionDenied, whose tap calls
                    // launchPermissionRequest()).
                    Button {
                        onEnableNotifications()
                    } label: {
                        HStack {
                            Image(systemName: "bell.slash")
                            Text("Notifications disabled. Tap to enable.")
                        }
                    }
                }
            }

            // Tap any row to copy its value (mirrors Android's VersionBottomSheet
            // tap-to-copy; iOS-native inline rows rather than a sheet). iOS shows
            // no system "copied" chip, so each row flashes its own confirmation.
            Section("About") {
                CopyableValueRow(label: "Version", value: appVersion)
                CopyableValueRow(label: "Build", value: appBuild)
                CopyableValueRow(label: "Package", value: appPackage)
                CopyableValueRow(label: "Built", value: appBuilt)
                // Privacy policy URL is the shared `AppLinks.PRIVACY_POLICY_URL`
                // (domain/commonMain) — same value Android's Settings opens, so
                // the two platforms can't drift.
                if let url = URL(string: AppLinks.shared.PRIVACY_POLICY_URL) {
                    Link(destination: url) {
                        HStack {
                            Text("Privacy policy").foregroundStyle(.primary)
                            Spacer()
                            Image(systemName: "arrow.up.right")
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }
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
                            Label("Function list", systemImage: "square.grid.2x2")
                        }
                    }
                }
            }
        }
        .navigationTitle("Settings")
        .refreshable { onRefresh() }
    }
}

/// A List row that copies [value] to the clipboard when tapped, briefly
/// showing a "Copied" confirmation in place of the value. Resting state matches
/// a standard value row (label leading, value trailing/secondary). The
/// `@State` is internal to this row, so it doesn't affect `SettingsContentView`'s
/// memberwise init / the generated snapshot test.
private struct CopyableValueRow: View {
    let label: String
    let value: String
    @State private var copied = false

    var body: some View {
        Button {
            UIPasteboard.general.string = value
            withAnimation { copied = true }
            Task {
                try? await Task.sleep(nanoseconds: 1_500_000_000)
                withAnimation { copied = false }
            }
        } label: {
            HStack {
                Text(label)
                    .foregroundStyle(.primary)
                Spacer()
                if copied {
                    Label("Copied", systemImage: "checkmark")
                        .labelStyle(.titleAndIcon)
                        .foregroundStyle(.secondary)
                } else {
                    Text(value)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityHint("Double tap to copy")
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
            displayName: nil,
            email: nil,
            snoozeLabel: "Not snoozing",
            snoozeSending: false,
            snoozeError: nil,
            durations: durations,
            developerAccess: false,
            functionListAccess: false,
            appVersion: "0.1.0",
            appBuild: "1",
            appPackage: "com.chriscartland.garage",
            appBuilt: "2026-01-15 12:00:00 UTC",
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
            displayName: "Chris Cartland",
            email: "chris@example.com",
            snoozeLabel: "Snoozing until 9:00 PM",
            snoozeSending: false,
            snoozeError: nil,
            durations: durations,
            developerAccess: true,
            functionListAccess: true,
            appVersion: "0.1.0",
            appBuild: "1",
            appPackage: "com.chriscartland.garage",
            appBuilt: "2026-01-15 12:00:00 UTC",
            onSignIn: {}, onSignOut: {}, onSnooze: { _ in }, onRefresh: {}
        )
    }
}

#Preview("Settings notifications disabled") {
    let durations: [(label: String, option: SnoozeDurationUIOption)] = [
        ("Do not snooze", .none), ("1 hour", .oneHour), ("4 hours", .fourHours),
        ("8 hours", .eightHours), ("12 hours", .twelveHours),
    ]
    return NavigationStack {
        SettingsContentView(
            signedIn: true,
            displayName: "Chris Cartland",
            email: "chris@example.com",
            snoozeLabel: "Not snoozing",
            snoozeSending: false,
            snoozeError: nil,
            durations: durations,
            developerAccess: false,
            functionListAccess: false,
            appVersion: "0.1.0",
            appBuild: "1",
            appPackage: "com.chriscartland.garage",
            appBuilt: "2026-01-15 12:00:00 UTC",
            notificationsGranted: false,
            onSignIn: {}, onSignOut: {}, onSnooze: { _ in }, onRefresh: {}
        )
    }
}
