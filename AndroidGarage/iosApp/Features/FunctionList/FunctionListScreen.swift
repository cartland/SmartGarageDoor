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
                clearDiagnostics: { wrapper.clearDiagnostics() }
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
}

/// Pure Functions content — renders without a live `NativeComponent`. Captured
/// by the `#Preview`s / snapshot gallery.
struct FunctionListContentView: View {
    let accessGranted: Bool?
    var actions = FunctionListActions()

    var body: some View {
        List {
            if accessGranted == true {
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
                Section("Diagnostics") {
                    Button("Prune diagnostics log") { actions.pruneDiagnosticsLog() }
                    Button("Clear all diagnostics", role: .destructive) { actions.clearDiagnostics() }
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
        .navigationTitle("Functions")
    }
}

#Preview("Functions granted") {
    NavigationStack {
        FunctionListContentView(accessGranted: true)
    }
}

#Preview("Functions locked") {
    NavigationStack {
        FunctionListContentView(accessGranted: false)
    }
}
