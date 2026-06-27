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

/// History tab — recent door events. Thin shell binding the wrapper into the
/// pure `HistoryContentView`. Mirrors Android's `DoorHistoryContent`.
struct HistoryScreen: View {
    @StateObject private var wrapper: HistoryViewModelWrapper

    init(component: NativeComponent) {
        _wrapper = StateObject(wrappedValue: HistoryViewModelWrapper(component: component))
    }

    var body: some View {
        HistoryContentView(
            rows: wrapper.rows,
            isLoading: wrapper.isLoading,
            onRefresh: { wrapper.refresh() }
        )
    }
}

/// Pure History content — renders without a live `NativeComponent`. Captured by
/// the `#Preview`s / snapshot gallery.
///
/// `now` is the reference instant for the relative timestamps. It defaults to the
/// live clock in production; previews inject a fixed `PreviewFixtures.now` so the
/// rendered "x ago" strings (and thus the snapshots) are deterministic.
struct HistoryContentView: View {
    let rows: [HistoryViewModelWrapper.Row]
    let isLoading: Bool
    var now: Date = Date()
    let onRefresh: () -> Void

    var body: some View {
        List {
            if rows.isEmpty {
                Text(isLoading ? "Loading…" : "No recent events")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(rows) { row in
                    HStack {
                        Text(row.position)
                        Spacer()
                        if let seconds = row.changeTimeSeconds {
                            Text(Self.relative(seconds, now: now))
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
        }
        .navigationTitle("History")
        .refreshable { onRefresh() }
    }

    private static func relative(_ epochSeconds: Int64, now: Date) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(epochSeconds))
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .abbreviated
        return formatter.localizedString(for: date, relativeTo: now)
    }
}

#Preview("History recent events") {
    let base = Int64(PreviewFixtures.now.timeIntervalSince1970)
    return NavigationStack {
        HistoryContentView(
            rows: [
                .init(id: 0, position: "Open", changeTimeSeconds: base - 300),
                .init(id: 1, position: "Closed", changeTimeSeconds: base - 3_600),
                .init(id: 2, position: "Opening too long", changeTimeSeconds: base - 86_400),
                .init(id: 3, position: "Closed", changeTimeSeconds: base - 172_800),
            ],
            isLoading: false,
            now: PreviewFixtures.now,
            onRefresh: {}
        )
    }
}

#Preview("History empty") {
    NavigationStack {
        HistoryContentView(
            rows: [],
            isLoading: false,
            now: PreviewFixtures.now,
            onRefresh: {}
        )
    }
}
