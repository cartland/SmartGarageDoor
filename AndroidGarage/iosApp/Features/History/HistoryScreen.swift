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

/// History tab — door events grouped by day with door icons, durations, and
/// transit / anomaly tags (parity with Android's `HistoryContent`). Thin shell
/// binding the wrapper into the pure `HistoryContentView`.
struct HistoryScreen: View {
    @StateObject private var wrapper: HistoryViewModelWrapper

    init(component: NativeComponent) {
        _wrapper = StateObject(wrappedValue: HistoryViewModelWrapper(component: component))
    }

    var body: some View {
        HistoryContentView(
            days: wrapper.days,
            isLoading: wrapper.isLoading,
            canLoadMore: wrapper.canLoadMore,
            isLoadingMore: wrapper.isLoadingMore,
            onRefresh: { wrapper.refresh() },
            onLoadMore: { wrapper.loadMore() }
        )
    }
}

/// Pure History content — renders day sections from already-resolved display
/// rows, so it draws without a live `NativeComponent`. Captured by the
/// `#Preview`s / snapshot gallery.
struct HistoryContentView: View {
    let days: [HistoryViewModelWrapper.DaySection]
    let isLoading: Bool
    /// Older events are available to page in — gates the scroll-to-end trigger
    /// and the footer's terminal "reached the beginning" state.
    var canLoadMore: Bool = false
    /// An older-page fetch is in flight — shows the footer spinner.
    var isLoadingMore: Bool = false
    let onRefresh: () -> Void
    /// Fires when the bottom footer scrolls into view with more to load. The
    /// shared repo guards re-entrancy, so an extra fire is harmless.
    var onLoadMore: () -> Void = {}

    var body: some View {
        List {
            if days.isEmpty {
                HistoryEmptyState(isLoading: isLoading)
                    .frame(maxWidth: .infinity)
                    .listRowSeparator(.hidden)
            } else {
                ForEach(days) { day in
                    Section {
                        ForEach(day.entries) { entry in
                            HistoryRow(entry: entry)
                        }
                    } header: {
                        Text(day.title)
                    }
                }
                // Bottom-of-list pagination footer: spinner while an older page
                // loads, a muted terminal note once nothing older remains. The
                // footer appearing near the end is itself the load-more trigger
                // (mirrors Android's near-end LaunchedEffect in HistoryContent).
                HistoryLoadMoreFooter(canLoadMore: canLoadMore, isLoadingMore: isLoadingMore)
                    .listRowSeparator(.hidden)
                    .onAppear {
                        if canLoadMore && !isLoadingMore { onLoadMore() }
                    }
            }
        }
        .navigationTitle("History")
        .refreshable { onRefresh() }
    }
}

/// One history row: leading garage-door icon + headline / supporting / warnings.
private struct HistoryRow: View {
    let entry: HistoryViewModelWrapper.Entry

    var body: some View {
        HStack(spacing: GarageSpacing.card) {
            GarageDoorView(position: entry.position)
                .frame(width: 40, height: 40)
            VStack(alignment: .leading, spacing: GarageSpacing.tight) {
                Text(entry.headline)
                Text(entry.supporting)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                ForEach(entry.warnings, id: \.self) { warning in
                    Label(warning, systemImage: "exclamationmark.triangle")
                        .font(.caption)
                        .foregroundStyle(GarageColors.statusWarning)
                }
            }
        }
        .padding(.vertical, GarageSpacing.tight)
    }
}

/// Bottom-of-list pagination footer. Spinner while an older page loads; a muted
/// terminal note once there's nothing older (distinct from the empty-list state,
/// which means "no events at all"). Renders nothing while idle with more to
/// load — the `onAppear` scroll-to-end trigger does the work. Mirrors Android's
/// `HistoryFooter`; the wording mirrors `R.string.history_footer_reached_beginning`.
private struct HistoryLoadMoreFooter: View {
    let canLoadMore: Bool
    let isLoadingMore: Bool

    var body: some View {
        HStack {
            Spacer(minLength: 0)
            if isLoadingMore {
                ProgressView()
            } else if !canLoadMore {
                Text("You've reached the beginning of your history")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, GarageSpacing.tight)
    }
}

private struct HistoryEmptyState: View {
    let isLoading: Bool

    var body: some View {
        VStack(spacing: GarageSpacing.tight) {
            Image(systemName: "clock.arrow.circlepath")
                .font(.largeTitle)
                .foregroundStyle(.secondary)
            Text(isLoading ? "Loading…" : "No events yet")
                .font(.headline)
            if !isLoading {
                Text("Open or close the garage and check back here.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
        }
        .padding(.vertical, GarageSpacing.card)
    }
}

// MARK: - Previews
//
// Fixtures are inlined with the memberwise initializer (no file-private helper):
// a #Preview body is embedded verbatim into the generated snapshot test target
// (@testable import iosApp), so it may reference only internal+ symbols.

#Preview("History recent events") {
    NavigationStack {
        HistoryContentView(
            days: [
                .init(id: "today", title: "Today", entries: [
                    .init(id: "t0", position: .open, headline: "Open",
                          supporting: "Since 10:15 AM · 12 min and counting", warnings: []),
                    .init(id: "t1", position: .closed, headline: "Closed at 9:53 AM",
                          supporting: "Closed for 22 min", warnings: []),
                    .init(id: "t2", position: .open, headline: "Opened at 9:47 AM",
                          supporting: "Open for 6 min",
                          warnings: ["Took 4 min to open, longer than expected"]),
                ]),
                .init(id: "yesterday", title: "Yesterday", entries: [
                    .init(id: "y0", position: .errorSensorConflict, headline: "Sensor conflict",
                          supporting: "11:42 PM", warnings: []),
                    .init(id: "y1", position: .closed, headline: "Closed at 8:30 PM",
                          supporting: "Closed for 10 hr 12 min", warnings: []),
                    .init(id: "y2", position: .open, headline: "Opened at 6:30 PM",
                          supporting: "Open for 2 hr", warnings: []),
                ]),
                .init(id: "2026-4-27", title: "Mon, Apr 27", entries: [
                    .init(id: "d0", position: .openingTooLong, headline: "Stuck opening",
                          supporting: "5:30 PM", warnings: []),
                    .init(id: "d1", position: .closed, headline: "Closed at 7:18 AM",
                          supporting: "Closed for 10 hr 12 min", warnings: []),
                ]),
            ],
            isLoading: false,
            onRefresh: {}
        )
    }
}

#Preview("History closed states") {
    NavigationStack {
        HistoryContentView(
            days: [
                .init(id: "today", title: "Today", entries: [
                    .init(id: "t0", position: .closed, headline: "Closed",
                          supporting: "Since 11:30 AM · 47 min and counting",
                          warnings: ["Took 3 min to close, longer than expected"]),
                    .init(id: "t1", position: .openMisaligned, headline: "Opened at 11:20 AM",
                          supporting: "Open for 10 min", warnings: ["Door was misaligned"]),
                ]),
                .init(id: "2026-4-27", title: "Mon, Apr 27", entries: [
                    .init(id: "d0", position: .closingTooLong, headline: "Stuck closing",
                          supporting: "4:00 PM", warnings: []),
                    .init(id: "d1", position: .unknown, headline: "Unknown state",
                          supporting: "11:00 AM", warnings: []),
                ]),
            ],
            isLoading: false,
            onRefresh: {}
        )
    }
}

#Preview("History empty") {
    NavigationStack {
        HistoryContentView(days: [], isLoading: false, onRefresh: {})
    }
}

// Footer-state previews use a SHORT list (one day) so the pagination footer
// renders inside the snapshot viewport (mirrors Android's HistoryFooter previews).

#Preview("History loading older page") {
    NavigationStack {
        HistoryContentView(
            days: [
                .init(id: "today", title: "Today", entries: [
                    .init(id: "t0", position: .closed, headline: "Closed at 9:53 AM",
                          supporting: "Closed for 22 min", warnings: []),
                    .init(id: "t1", position: .open, headline: "Opened at 9:47 AM",
                          supporting: "Open for 6 min", warnings: []),
                ]),
            ],
            isLoading: false,
            canLoadMore: true,
            isLoadingMore: true,
            onRefresh: {},
            onLoadMore: {}
        )
    }
}

#Preview("History reached beginning") {
    NavigationStack {
        HistoryContentView(
            days: [
                .init(id: "today", title: "Today", entries: [
                    .init(id: "t0", position: .closed, headline: "Closed at 9:53 AM",
                          supporting: "Closed for 22 min", warnings: []),
                ]),
            ],
            isLoading: false,
            canLoadMore: false,
            isLoadingMore: false,
            onRefresh: {},
            onLoadMore: {}
        )
    }
}
