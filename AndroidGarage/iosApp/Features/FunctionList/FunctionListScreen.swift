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

/// Functions tab — per-user-gated developer actions. Mirrors Android's
/// `FunctionListContent`. Buttons show only when `accessGranted == true`.
struct FunctionListScreen: View {
    @StateObject private var wrapper: FunctionListViewModelWrapper

    init(component: NativeComponent) {
        _wrapper = StateObject(wrappedValue: FunctionListViewModelWrapper(component: component))
    }

    var body: some View {
        FunctionListContentView(
            accessGranted: wrapper.accessGranted,
            testTopic: wrapper.testTopic,
            testSubscribed: wrapper.testSubscribed,
            actions: FunctionListActions(
                openOrCloseDoor: { wrapper.openOrCloseDoor() },
                snoozeForOneHour: { wrapper.snoozeForOneHour() },
                refreshDoorStatus: { wrapper.refreshDoorStatus() },
                refreshDoorHistory: { wrapper.refreshDoorHistory() },
                refreshSnoozeStatus: { wrapper.refreshSnoozeStatus() },
                refreshButtonHealth: { wrapper.refreshButtonHealth() },
                registerFcm: { wrapper.registerFcm() },
                deregisterFcm: { wrapper.deregisterFcm() },
                pruneDiagnosticsLog: { wrapper.pruneDiagnosticsLog() },
                clearDiagnostics: { wrapper.clearDiagnostics() },
                copyAuthToken: { await wrapper.copyAuthToken() },
                copyTestTopic: { wrapper.copyTestTopic() },
                subscribeTestNotification: { wrapper.subscribeTestNotification() },
                unsubscribeTestNotification: { wrapper.unsubscribeTestNotification() },
                changeTestNotificationTopic: { wrapper.changeTestNotificationTopic() }
            )
        )
    }
}

/// Action closures for the developer panel, bundled so the content view and its
/// previews stay readable (all default to no-ops — previews pass none).
struct FunctionListActions {
    var openOrCloseDoor: () -> Void = {}
    var snoozeForOneHour: () -> Void = {}
    var refreshDoorStatus: () -> Void = {}
    var refreshDoorHistory: () -> Void = {}
    var refreshSnoozeStatus: () -> Void = {}
    var refreshButtonHealth: () -> Void = {}
    var registerFcm: () -> Void = {}
    var deregisterFcm: () -> Void = {}
    var pruneDiagnosticsLog: () -> Void = {}
    var clearDiagnostics: () -> Void = {}
    /// Developer-only: copy the Firebase ID token. `async` (token fetch) and
    /// returns the outcome so the button can flash. Defaults to `.notSignedIn`
    /// for previews (no live component).
    var copyAuthToken: () async -> AuthTokenCopyOutcome = { .notSignedIn }
    var copyTestTopic: () -> Void = {}
    var subscribeTestNotification: () -> Void = {}
    var unsubscribeTestNotification: () -> Void = {}
    var changeTestNotificationTopic: () -> Void = {}
}

/// Pure Functions content — renders without a live `NativeComponent`. Captured
/// by the `#Preview`s / snapshot gallery.
struct FunctionListContentView: View {
    let accessGranted: Bool?
    /// Test-notification sandbox topic (nil/empty hides the section, matching
    /// Android, which only shows the rows once the personal topic exists).
    var testTopic: String?
    var testSubscribed: Bool = false
    var actions = FunctionListActions()

    /// Verbatim copy of Android's `R.string.function_list_warning`.
    ///
    /// `static` (not a `private let` instance property) on purpose: a private
    /// *stored instance* property would lower this struct's synthesized
    /// memberwise initializer to `private`, breaking the cross-file generated
    /// snapshot test that calls `FunctionListContentView(...)`. A type property
    /// is never part of the memberwise init. See CLAUDE.md § iOS snapshot gallery.
    private static let warningText =
        "Each button below performs a real action immediately. No confirmation " +
        "prompts. Tapping triggers calls to the server, modifies app state, or " +
        "wipes local data. Double-check the label before tapping."

    var body: some View {
        List {
            if accessGranted == true {
                Section {
                    Text(Self.warningText)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                Section("Door") {
                    Button("Open / close door") { actions.openOrCloseDoor() }
                    Button("Snooze 1 hour") { actions.snoozeForOneHour() }
                }
                Section("Refresh") {
                    Button("Door status") { actions.refreshDoorStatus() }
                    Button("Door history") { actions.refreshDoorHistory() }
                    Button("Snooze status") { actions.refreshSnoozeStatus() }
                    Button("Button health") { actions.refreshButtonHealth() }
                }
                Section("FCM") {
                    Button("Register FCM") { actions.registerFcm() }
                    Button("Deregister FCM") { actions.deregisterFcm() }
                }
                Section("Auth") {
                    // Developer-only: copy the Firebase ID token (mirrors
                    // Android's `function_list_copy_auth_token`). The button owns
                    // its own confirmation flash; the fetch + sensitivity posture
                    // live in `AuthTokenCopier`.
                    CopyAuthTokenButton(copy: actions.copyAuthToken)
                }
                Section("Diagnostics") {
                    Button("Prune diagnostics log") { actions.pruneDiagnosticsLog() }
                    Button("Clear all diagnostics", role: .destructive) { actions.clearDiagnostics() }
                }
                // Test-notification sandbox (diagnostic). Hidden until the
                // personal topic is generated, so the gallery's "granted"
                // preview stays focused on the core actions.
                if let topic = testTopic, !topic.isEmpty {
                    TestNotificationSectionView(
                        topic: topic,
                        subscribed: testSubscribed,
                        actions: actions
                    )
                }
            } else {
                VStack(spacing: GarageSpacing.betweenItems) {
                    Image(systemName: "lock")
                        .font(.system(size: 48))
                        .foregroundStyle(.secondary)
                    Text("Functions unavailable")
                        .font(.title2.weight(.semibold))
                    Text("This developer panel is gated to allowlisted accounts.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                .padding(GarageSpacing.screen)
                .listRowBackground(Color.clear)
            }
        }
        .navigationTitle("Function list")
    }
}

/// The test-notification sandbox rows, extracted as a pure subview so the
/// snapshot gallery can capture them directly: in the full screen this section
/// sits below the Door / Refresh / FCM / Diagnostics sections, i.e. below the
/// top-anchored snapshot fold. Mirrors the `HomeInfoSheetContentView` extraction
/// pattern. The body is a `Section`, so callers embed it inside a `List`.
struct TestNotificationSectionView: View {
    let topic: String
    let subscribed: Bool
    var actions = FunctionListActions()

    var body: some View {
        Section("Test notifications") {
            LabeledContent("Topic") {
                Text(topic)
                    .font(.footnote.monospaced())
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                    .truncationMode(.middle)
            }
            Button("Copy test topic") { actions.copyTestTopic() }
            if subscribed {
                Button("Unsubscribe test notifications") { actions.unsubscribeTestNotification() }
            } else {
                Button("Subscribe test notifications") { actions.subscribeTestNotification() }
            }
            Button("Change test topic") { actions.changeTestNotificationTopic() }
        }
    }
}

#Preview("Functions granted") {
    NavigationStack {
        FunctionListContentView(accessGranted: true)
    }
}

#Preview("Functions test notifications") {
    NavigationStack {
        List {
            TestNotificationSectionView(
                topic: "testNotification-7f3a9c20-1e4b-4d6a-9c2f-8b5e0a1d3c47",
                subscribed: true
            )
        }
        .navigationTitle("Test notifications")
    }
}

#Preview("Functions locked") {
    NavigationStack {
        FunctionListContentView(accessGranted: false)
    }
}
