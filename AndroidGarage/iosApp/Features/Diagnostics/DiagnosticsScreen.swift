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

/// Diagnostics tab — lifetime counters + Export CSV + "Clear all diagnostics".
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
            onClear: { wrapper.clearDiagnostics() },
            onBuildCsv: { await wrapper.buildCsv() },
            onCopyAuthToken: { await wrapper.copyAuthToken() }
        )
    }
}

/// Pure Diagnostics content — renders without a live `NativeComponent`. Captured
/// by the `#Preview`s / snapshot gallery.
struct DiagnosticsContentView: View {
    let counters: [DiagnosticsViewModelWrapper.Counter]
    let clearInFlight: Bool
    let onClear: () -> Void
    /// Builds the export CSV (shared `AppLogCsv` format). `async` because it
    /// reads the log repo. Defaults to empty so the `#Preview`s/snapshot gallery
    /// render the button without a live component.
    let onBuildCsv: () async -> String
    /// Copies the Firebase ID token (developer-only). `async` (token fetch);
    /// returns the outcome so the button can flash. Defaults to `.notSignedIn`
    /// for the `#Preview`s/snapshot gallery (no live component).
    let onCopyAuthToken: () async -> AuthTokenCopyOutcome

    // `@State` here would lower the *synthesized* memberwise init to `private`
    // (the optional `shareItem` is the exact case that breaks the cross-file
    // generated snapshot test), so an explicit `internal init` covers the
    // injected `let`s and the `@State` stay initialized inline. Same pattern as
    // `HomeContentView`'s info-sheet state.
    @State private var showClearConfirm = false
    @State private var shareItem: CsvShareItem?
    @State private var exporting = false

    init(
        counters: [DiagnosticsViewModelWrapper.Counter],
        clearInFlight: Bool,
        onClear: @escaping () -> Void,
        onBuildCsv: @escaping () async -> String = { "" },
        onCopyAuthToken: @escaping () async -> AuthTokenCopyOutcome = { .notSignedIn }
    ) {
        self.counters = counters
        self.clearInFlight = clearInFlight
        self.onClear = onClear
        self.onBuildCsv = onBuildCsv
        self.onCopyAuthToken = onCopyAuthToken
    }

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
                // Developer-only: copy the Firebase ID token (mirrors Android's
                // Diagnostics `settings_diagnostics_copy_auth_token`). The button
                // owns its confirmation flash; the fetch routes through the VM
                // (`copyAuthToken` → `fetchAuthTokenForCopy`, ADR-033) and
                // `AuthTokenCopier` owns the iOS sensitivity posture.
                CopyAuthTokenButton(copy: onCopyAuthToken)

                Button {
                    export()
                } label: {
                    HStack(spacing: GarageSpacing.tight) {
                        if exporting {
                            ProgressView().controlSize(.small)
                            Text("Exporting…")
                        } else {
                            Image(systemName: "square.and.arrow.up")
                            Text("Export CSV")
                        }
                    }
                }
                .disabled(exporting)

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
            // Mirrors Android's `settings_diagnostics_confirm_message` — names
            // the scope (counters + event log), what is NOT affected (door
            // history, other settings), and the irreversibility.
            Text(
                "Resets every counter on this screen to 0 and deletes the " +
                    "exportable event log. Door history and other app settings " +
                    "are not affected. This cannot be undone."
            )
        }
        .sheet(item: $shareItem) { item in
            ActivityView(activityItems: [item.url])
        }
    }

    /// Build the CSV off the main actor, write it to a temp `.csv`, then present
    /// the system share sheet. Mirrors Android's "Export CSV" (which writes to a
    /// user-chosen content `Uri`); iOS shares the file instead.
    private func export() {
        exporting = true
        Task {
            let csv = await onBuildCsv()
            shareItem = writeTempCsv(csv).map(CsvShareItem.init)
            exporting = false
        }
    }

    private func writeTempCsv(_ csv: String) -> URL? {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("garage-app-log.csv")
        do {
            try csv.write(to: url, atomically: true, encoding: .utf8)
            return url
        } catch {
            return nil
        }
    }
}

/// Identifiable wrapper so the share sheet can be driven by `.sheet(item:)`.
private struct CsvShareItem: Identifiable {
    let id = UUID()
    let url: URL
    init(_ url: URL) { self.url = url }
}

/// Minimal `UIActivityViewController` bridge for the share sheet (SwiftUI's
/// `ShareLink` can't be triggered from an async button action cleanly).
private struct ActivityView: UIViewControllerRepresentable {
    let activityItems: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: activityItems, applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
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
