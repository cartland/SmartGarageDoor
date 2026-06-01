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
    @State private var showClearConfirm = false

    init(component: NativeComponent) {
        _wrapper = StateObject(wrappedValue: DiagnosticsViewModelWrapper(component: component))
    }

    var body: some View {
        List {
            Section("Counters") {
                ForEach(wrapper.counters) { counter in
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
                        if wrapper.clearInFlight {
                            ProgressView().controlSize(.small)
                        }
                        Text(wrapper.clearInFlight ? "Clearing…" : "Clear all diagnostics")
                    }
                }
                .disabled(wrapper.clearInFlight)
            }
        }
        .navigationTitle("Diagnostics")
        .confirmationDialog(
            "Clear all diagnostics?",
            isPresented: $showClearConfirm,
            titleVisibility: .visible
        ) {
            Button("Clear all", role: .destructive) { wrapper.clearDiagnostics() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Wipes the app-event log and lifetime counters.")
        }
    }
}
