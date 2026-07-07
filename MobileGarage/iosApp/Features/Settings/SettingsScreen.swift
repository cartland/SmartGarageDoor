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
            snoozeSnoozing: wrapper.snoozeSnoozing,
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
///
/// Row design mirrors Android's `SettingsRow` (leading icon, title, secondary
/// state text, chevron when a sheet opens); the snooze duration picker lives in
/// a sheet the row opens (Android's `SnoozeBottomSheet`), not inline.
struct SettingsContentView: View {
    let signedIn: Bool
    let displayName: String?
    let email: String?
    let snoozeLabel: String
    /// Whether a snooze is currently active — drives the row icon
    /// (mirrors Android's `SnoozeRowState.SnoozingUntil` icon swap).
    var snoozeSnoozing: Bool = false
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

    /// Sheet presentation is pure local UI state. The explicit `init` below
    /// keeps these `private @State`s from lowering the synthesized memberwise
    /// initializer to `private` (which would break the generated snapshot test
    /// that constructs this view across files).
    @State private var snoozeSheetOpen = false
    @State private var accountSheetOpen = false

    init(
        signedIn: Bool,
        displayName: String?,
        email: String?,
        snoozeLabel: String,
        snoozeSnoozing: Bool = false,
        snoozeSending: Bool,
        snoozeError: String?,
        durations: [(label: String, option: SnoozeDurationUIOption)],
        developerAccess: Bool?,
        functionListAccess: Bool?,
        appVersion: String,
        appBuild: String,
        appPackage: String,
        appBuilt: String,
        notificationsGranted: Bool = true,
        onSignIn: @escaping () -> Void,
        onSignOut: @escaping () -> Void,
        onSnooze: @escaping (SnoozeDurationUIOption) -> Void,
        onRefresh: @escaping () -> Void,
        onEnableNotifications: @escaping () -> Void = {}
    ) {
        self.signedIn = signedIn
        self.displayName = displayName
        self.email = email
        self.snoozeLabel = snoozeLabel
        self.snoozeSnoozing = snoozeSnoozing
        self.snoozeSending = snoozeSending
        self.snoozeError = snoozeError
        self.durations = durations
        self.developerAccess = developerAccess
        self.functionListAccess = functionListAccess
        self.appVersion = appVersion
        self.appBuild = appBuild
        self.appPackage = appPackage
        self.appBuilt = appBuilt
        self.notificationsGranted = notificationsGranted
        self.onSignIn = onSignIn
        self.onSignOut = onSignOut
        self.onSnooze = onSnooze
        self.onRefresh = onRefresh
        self.onEnableNotifications = onEnableNotifications
    }

    var body: some View {
        List {
            Section("Account") {
                if signedIn {
                    // Signed in — identity row opens the account sheet (sign
                    // out lives there), mirroring Android's AccountBottomSheet.
                    Button { accountSheetOpen = true } label: {
                        SettingsRowLabel(
                            icon: "person.crop.circle.fill",
                            title: displayName ?? "Signed in",
                            subtitle: email,
                            showChevron: true
                        )
                    }
                    .buttonStyle(.plain)
                } else {
                    // Signed out — the row itself starts sign-in (mirrors
                    // Android's single Login row; no separate status text).
                    Button(action: onSignIn) {
                        SettingsRowLabel(
                            icon: "person.crop.circle.badge.plus",
                            title: "Sign in with Google",
                            subtitle: "Unlocks the remote button and snooze",
                            showChevron: false
                        )
                    }
                    .buttonStyle(.plain)
                }
            }

            Section("Notifications") {
                if notificationsGranted {
                    // The duration picker lives in a sheet this row opens —
                    // current state reads as the subtitle (Android's snooze
                    // row + SnoozeBottomSheet pattern).
                    Button { snoozeSheetOpen = true } label: {
                        SettingsRowLabel(
                            icon: snoozeSnoozing ? "bell.slash" : "bell",
                            title: "Snooze notifications",
                            subtitle: snoozeLabel,
                            showChevron: !snoozeSending,
                            inFlight: snoozeSending
                        )
                    }
                    .buttonStyle(.plain)
                    .disabled(snoozeSending)
                    if let error = snoozeError {
                        Label(error, systemImage: "exclamationmark.triangle")
                            .font(.footnote)
                            .foregroundStyle(GarageColors.statusWarning)
                    }
                } else {
                    // Notifications denied — snoozing is meaningless without them,
                    // so replace the controls with a tap-to-enable row (mirrors
                    // Android's SnoozeRowState.PermissionDenied, whose tap calls
                    // launchPermissionRequest()).
                    Button(action: onEnableNotifications) {
                        SettingsRowLabel(
                            icon: "bell.slash",
                            title: "Notifications disabled",
                            subtitle: "Tap to enable",
                            showChevron: false
                        )
                    }
                    .buttonStyle(.plain)
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
        .sheet(isPresented: $snoozeSheetOpen) {
            SnoozeSheetView(
                currentLabel: snoozeLabel,
                durations: durations,
                onSave: { option in
                    onSnooze(option)
                    snoozeSheetOpen = false
                },
                onCancel: { snoozeSheetOpen = false }
            )
            .presentationDetents([.medium, .large])
        }
        .sheet(isPresented: $accountSheetOpen) {
            AccountSheetContentView(
                displayName: displayName ?? "Signed in",
                email: email,
                onSignOut: {
                    onSignOut()
                    accountSheetOpen = false
                },
                onCancel: { accountSheetOpen = false }
            )
            .presentationDetents([.medium])
        }
    }
}

/// Shared row layout for Settings — leading icon, title + optional secondary
/// state text, optional chevron / in-flight spinner. The SwiftUI analog of
/// Android's `SettingsRow` ListItem (icon, headline, supporting text, trailing
/// chevron, in-flight indicator). `internal` for previews.
struct SettingsRowLabel: View {
    let icon: String
    let title: String
    var subtitle: String?
    var showChevron: Bool = false
    var inFlight: Bool = false

    var body: some View {
        HStack(spacing: GarageSpacing.card) {
            ZStack {
                Image(systemName: icon)
                    .font(.title3)
                    .foregroundStyle(.tint)
                if inFlight {
                    ProgressView()
                        .controlSize(.regular)
                }
            }
            .frame(width: 32, height: 32)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.body)
                    .foregroundStyle(.primary)
                if let subtitle {
                    Text(subtitle)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer()
            if showChevron {
                Image(systemName: "chevron.right")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Color(uiColor: .tertiaryLabel))
            }
        }
        .contentShape(Rectangle())
    }
}

/// Stateful holder for the snooze sheet — owns the pending selection so the
/// pure `SnoozeSheetContentView` stays previewable. Opens with **no option
/// selected** (Save disabled) — the user must actively pick, mirroring
/// Android's deliberate no-preselection `SnoozeBottomSheet` semantics.
struct SnoozeSheetView: View {
    let currentLabel: String
    let durations: [(label: String, option: SnoozeDurationUIOption)]
    let onSave: (SnoozeDurationUIOption) -> Void
    let onCancel: () -> Void

    @State private var selected: SnoozeDurationUIOption?

    init(
        currentLabel: String,
        durations: [(label: String, option: SnoozeDurationUIOption)],
        onSave: @escaping (SnoozeDurationUIOption) -> Void,
        onCancel: @escaping () -> Void
    ) {
        self.currentLabel = currentLabel
        self.durations = durations
        self.onSave = onSave
        self.onCancel = onCancel
    }

    var body: some View {
        SnoozeSheetContentView(
            currentLabel: currentLabel,
            durations: durations,
            selected: selected,
            onSelect: { selected = $0 },
            onCancel: onCancel,
            onSave: {
                if let choice = selected { onSave(choice) }
            }
        )
    }
}

/// Pure snooze-sheet content — the SwiftUI analog of Android's
/// `SnoozeSheetContent`: title, current state, one selectable row per duration
/// (radio-style), Cancel + Save. Save stays disabled until the user picks an
/// option. Pure values so the snapshot gallery renders it directly.
struct SnoozeSheetContentView: View {
    let currentLabel: String
    let durations: [(label: String, option: SnoozeDurationUIOption)]
    let selected: SnoozeDurationUIOption?
    let onSelect: (SnoozeDurationUIOption) -> Void
    let onCancel: () -> Void
    let onSave: () -> Void

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach(durations, id: \.label) { entry in
                        Button {
                            onSelect(entry.option)
                        } label: {
                            HStack {
                                Text(entry.label)
                                    .foregroundStyle(.primary)
                                Spacer()
                                Image(systemName: selected == entry.option
                                    ? "checkmark.circle.fill"
                                    : "circle")
                                    .foregroundStyle(selected == entry.option
                                        ? AnyShapeStyle(.tint)
                                        : AnyShapeStyle(Color(uiColor: .tertiaryLabel)))
                            }
                            // Without an explicit content shape, a .plain Button's
                            // hit area is only the rendered Text + Image — a tap on
                            // the Spacer gap between them does nothing (caught in
                            // the simulator walkthrough).
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                } footer: {
                    Text("Silences the open-door warning for the selected time. Currently: \(currentLabel.lowercased()).")
                }
            }
            .navigationTitle("Snooze notifications")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", action: onCancel)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save", action: onSave)
                        .disabled(selected == nil)
                }
            }
        }
    }
}

/// Pure account-sheet content — identity + sign out, the SwiftUI analog of
/// Android's `AccountBottomSheet`. Pure values so the snapshot gallery renders
/// it directly.
struct AccountSheetContentView: View {
    let displayName: String
    let email: String?
    let onSignOut: () -> Void
    let onCancel: () -> Void

    var body: some View {
        NavigationStack {
            VStack(spacing: GarageSpacing.card) {
                Image(systemName: "person.crop.circle.fill")
                    .font(.system(size: 56))
                    .foregroundStyle(.tint)
                VStack(spacing: GarageSpacing.tight) {
                    Text(displayName)
                        .font(.title3.weight(.semibold))
                    if let email {
                        Text(email)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }
                Button(role: .destructive, action: onSignOut) {
                    Text("Sign out")
                        .frame(maxWidth: .infinity, minHeight: 36)
                }
                .buttonStyle(.bordered)
                .padding(.top, GarageSpacing.tight)
            }
            .padding(GarageSpacing.card)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done", action: onCancel)
                }
            }
        }
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
// it may only reference symbols visible via `@testable import GarageControl` (internal+)
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
            snoozeSnoozing: true,
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

#Preview("Settings snooze sending") {
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
            snoozeSending: true,
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

#Preview("Snooze sheet nothing selected") {
    let durations: [(label: String, option: SnoozeDurationUIOption)] = [
        ("Do not snooze", .none), ("1 hour", .oneHour), ("4 hours", .fourHours),
        ("8 hours", .eightHours), ("12 hours", .twelveHours),
    ]
    return SnoozeSheetContentView(
        currentLabel: "Not snoozing",
        durations: durations,
        selected: nil,
        onSelect: { _ in },
        onCancel: {},
        onSave: {}
    )
}

#Preview("Snooze sheet option selected") {
    let durations: [(label: String, option: SnoozeDurationUIOption)] = [
        ("Do not snooze", .none), ("1 hour", .oneHour), ("4 hours", .fourHours),
        ("8 hours", .eightHours), ("12 hours", .twelveHours),
    ]
    return SnoozeSheetContentView(
        currentLabel: "Snoozing until 9:00 PM",
        durations: durations,
        selected: .fourHours,
        onSelect: { _ in },
        onCancel: {},
        onSave: {}
    )
}

#Preview("Account sheet") {
    AccountSheetContentView(
        displayName: "Chris Cartland",
        email: "chris@example.com",
        onSignOut: {},
        onCancel: {}
    )
}
