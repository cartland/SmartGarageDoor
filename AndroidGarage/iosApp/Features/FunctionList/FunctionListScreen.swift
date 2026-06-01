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
        List {
            if wrapper.accessGranted == true {
                Section("Door") {
                    Button("Open / close door") { wrapper.openOrCloseDoor() }
                    Button("Snooze 1 hour") { wrapper.snoozeForOneHour() }
                }
                Section("Refresh") {
                    Button("Door status") { wrapper.refreshDoorStatus() }
                    Button("Door history") { wrapper.refreshDoorHistory() }
                    Button("Snooze status") { wrapper.refreshSnoozeStatus() }
                    Button("Button health") { wrapper.refreshButtonHealth() }
                }
                Section("FCM") {
                    Button("Register FCM") { wrapper.registerFcm() }
                    Button("Deregister FCM") { wrapper.deregisterFcm() }
                }
                Section("Diagnostics") {
                    Button("Prune diagnostics log") { wrapper.pruneDiagnosticsLog() }
                    Button("Clear all diagnostics", role: .destructive) { wrapper.clearDiagnostics() }
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
