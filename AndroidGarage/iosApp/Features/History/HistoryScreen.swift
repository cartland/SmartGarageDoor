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

/// History tab — recent door events. Mirrors Android's `DoorHistoryContent`.
struct HistoryScreen: View {
    @StateObject private var wrapper: HistoryViewModelWrapper

    init(component: NativeComponent) {
        _wrapper = StateObject(wrappedValue: HistoryViewModelWrapper(component: component))
    }

    var body: some View {
        List {
            if wrapper.rows.isEmpty {
                Text(wrapper.isLoading ? "Loading…" : "No recent events")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(wrapper.rows) { row in
                    HStack {
                        Text(row.position)
                        Spacer()
                        if let seconds = row.changeTimeSeconds {
                            Text(Self.relative(seconds))
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
        }
        .navigationTitle("History")
        .refreshable { wrapper.refresh() }
    }

    private static func relative(_ epochSeconds: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(epochSeconds))
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .abbreviated
        return formatter.localizedString(for: date, relativeTo: Date())
    }
}
