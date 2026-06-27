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

/// Diagnostics tab — lifetime counters + "Clear all diagnostics".
/// Mirrors Android's `DiagnosticsContent`.
struct DiagnosticsScreen: View {
    @StateObject private var wrapper: DiagnosticsViewModelWrapper

    init(component: NativeComponent) {
        _wrapper = StateObject(wrappedValue: DiagnosticsViewModelWrapper(component: component))
    }

    var body: some View {
        DiagnosticsContentView(
            counters: wrapper.counters,
            clearInFlight: wrapper.clearInFlight,
            onClear: { wrapper.clearDiagnostics() }
        )
    }
}

/// Pure Diagnostics content — renders without a live `NativeComponent`. Captured
/// by the `#Preview`s / snapshot gallery.
struct DiagnosticsContentView: View {
    let counters: [DiagnosticsViewModelWrapper.Counter]
    let clearInFlight: Bool
    let onClear: () -> Void

    @State private var showClearConfirm = false

    var body: some View {
        List {
            Section("Counters") {
                ForEach(counters) { counter in
                    HStack {
                        Text(counter.label)
                        Spacer()
                        Text("\(counter.value)")
                            .monospacedDigit()
                            .foregroundStyle(.secondary)
                    }
                }
            }

            Section {
                Button(role: .destructive) {
                    showClearConfirm = true
                } label: {
                    HStack(spacing: GarageSpacing.tight) {
                        if clearInFlight {
                            ProgressView().controlSize(.small)
                        }
                        Text(clearInFlight ? "Clearing…" : "Clear all diagnostics")
                    }
                }
                .disabled(clearInFlight)
            }
        }
        .navigationTitle("Diagnostics")
        .confirmationDialog(
            "Clear all diagnostics?",
            isPresented: $showClearConfirm,
            titleVisibility: .visible
        ) {
            Button("Clear all", role: .destructive) { onClear() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Wipes the app-event log and lifetime counters.")
        }
    }
}

#Preview("Diagnostics counters") {
    NavigationStack {
        DiagnosticsContentView(
            counters: [
                .init(id: "initCurrent", label: "Init current door", value: 12),
                .init(id: "initRecent", label: "Init recent door", value: 8),
                .init(id: "userCurrent", label: "User fetch current", value: 34),
                .init(id: "userRecent", label: "User fetch recent", value: 21),
                .init(id: "fcmReceived", label: "FCM door events received", value: 57),
                .init(id: "fcmSubscribe", label: "FCM topic subscribes", value: 3),
                .init(id: "fcmExceeded", label: "Exceeded expected time without FCM", value: 1),
                .init(id: "fcmInRange", label: "Time without FCM in range", value: 56),
            ],
            clearInFlight: false,
            onClear: {}
        )
    }
}
