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

/// Bridges `DefaultDoorHistoryViewModel` to SwiftUI.
///
/// The merge / dedup / duration / day-grouping pipeline lives in the shared
/// `HistoryMapper` (ADR-031) and runs identically on Android. This wrapper feeds
/// it the raw events + the ticking clock + the device time zone, then resolves
/// the resulting *typed* `[HistoryDay]` into already-localized display rows —
/// the same job Android's `HistoryContent.kt` Composable does with
/// `stringResource`. The English wording mirrors the `history_*` resources.
@MainActor
final class HistoryViewModelWrapper: ObservableObject {
    /// One day section: a localized header + its rows (newest-first).
    struct DaySection: Identifiable {
        let id: String
        let title: String
        let entries: [Entry]
    }

    /// One history row, fully resolved to display values. `position` drives the
    /// leading garage-door icon; the strings are already localized.
    struct Entry: Identifiable {
        let id: String
        let position: DoorPosition
        let headline: String
        let supporting: String
        let warnings: [String]
    }

    @Published private(set) var days: [DaySection] = []
    @Published private(set) var isLoading: Bool = false
    /// Whether the stale-check-in banner should show — device telemetry is
    /// older than the staleness threshold. The decision routes through the
    /// shared `HistoryAlertMapper` (ADR-031) so it can't diverge from Android.
    @Published private(set) var showStaleBanner: Bool = false
    /// Older events are available to page in — drives the scroll-to-end
    /// trigger and the footer's terminal state. Pass-through of the shared
    /// `paginationState.canLoadMore` (the repo owns the cursor; ADR-028).
    @Published private(set) var canLoadMore: Bool = false
    /// An older-page fetch is in flight — drives the footer spinner.
    @Published private(set) var isLoadingMore: Bool = false

    private let shared: SharedViewModel<DefaultDoorHistoryViewModel>
    private var tasks: [Task<Void, Never>] = []
    private var vm: DefaultDoorHistoryViewModel { shared.instance }

    /// Latest raw events + clock, kept so either stream can trigger a remap.
    private var latestEvents: [DoorEvent] = []
    private var nowEpochSeconds: Int64 = 0

    init(component: NativeComponent) {
        shared = SharedViewModel(component.doorHistoryViewModel)
        nowEpochSeconds = vm.nowEpochSeconds.value.int64Value
        apply(vm.recentDoorEvents.value)
        applyPagination(vm.paginationState.value)
        applyStale(vm.isCheckInStale.value.boolValue)
        rebuild()

        tasks.append(Task { @MainActor [weak self] in
            guard let self else { return }
            for await result in self.vm.recentDoorEvents {
                self.apply(result)
                self.rebuild()
            }
        })
        tasks.append(Task { @MainActor [weak self] in
            guard let self else { return }
            for await stale in self.vm.isCheckInStale {
                self.applyStale(stale.boolValue)
            }
        })
        tasks.append(Task { @MainActor [weak self] in
            guard let self else { return }
            for await tick in self.vm.nowEpochSeconds {
                self.nowEpochSeconds = tick.int64Value
                self.rebuild()
            }
        })
        tasks.append(Task { @MainActor [weak self] in
            guard let self else { return }
            for await state in self.vm.paginationState {
                self.applyPagination(state)
            }
        })
    }

    private func applyPagination(_ state: PaginationState) {
        canLoadMore = state.canLoadMore
        isLoadingMore = state.isLoadingMore
    }

    private func applyStale(_ stale: Bool) {
        // Route the show/hide decision through the shared HistoryAlertMapper so
        // it can't diverge from Android (ADR-031). For the single current alert
        // type this reduces to "is the list non-empty", but the decision lives
        // in the shared layer, and new alert types land there for both platforms.
        showStaleBanner = !HistoryAlertMapper.shared.toHistoryAlerts(isCheckInStale: stale).isEmpty
    }

    private func apply(_ result: LoadingResult<NSArray>) {
        isLoading = result is LoadingResultLoading
        latestEvents = (result.data as? [DoorEvent]) ?? []
    }

    /// Runs the shared mapper over the latest events + clock and resolves the
    /// typed output to display rows.
    private func rebuild() {
        let historyDays = HistoryMapper.shared.toHistoryDays(
            events: latestEvents,
            nowEpochSeconds: nowEpochSeconds,
            timeZoneId: TimeZone.current.identifier
        )
        days = historyDays.map { day in
            DaySection(
                id: Self.dayKey(day.label),
                title: Self.dayTitle(day.label),
                entries: day.entries.enumerated().map { index, entry in
                    Self.resolve(entry, index: index)
                }
            )
        }
    }

    // MARK: - Day label resolution (mirrors HistoryContent.dayLabelText)

    private static func dayKey(_ label: DayLabel) -> String {
        switch onEnum(of: label) {
        case .today: return "today"
        case .yesterday: return "yesterday"
        case .date(let d): return "\(d.year)-\(d.monthNumber)-\(d.dayOfMonth)"
        }
    }

    private static func dayTitle(_ label: DayLabel) -> String {
        switch onEnum(of: label) {
        case .today: return "Today"
        case .yesterday: return "Yesterday"
        case .date(let d):
            var components = DateComponents()
            components.year = Int(d.year)
            components.month = Int(d.monthNumber)
            components.day = Int(d.dayOfMonth)
            guard let date = Calendar.current.date(from: components) else {
                return "\(d.monthNumber)/\(d.dayOfMonth)"
            }
            return dateLabelFormatter.string(from: date)
        }
    }

    // MARK: - Entry resolution (mirrors HistoryContent.HistoryEntryRow)

    private static func resolve(_ entry: HistoryEntry, index: Int) -> Entry {
        switch onEnum(of: entry) {
        case .opened(let opened):
            let time = clockText(opened.timeSeconds)
            let duration = stateDuration(opened.durationSeconds, isCurrent: opened.isCurrent, isOpen: true)
            let headline: String
            if opened.isCurrent && opened.misaligned {
                headline = "Open (misaligned)"
            } else if opened.isCurrent {
                headline = "Open"
            } else {
                headline = "Opened at \(time)"
            }
            var warnings: [String] = []
            if let warning = opened.transitWarning { warnings.append(transitText(warning)) }
            if opened.misaligned && !opened.isCurrent { warnings.append("Door was misaligned") }
            return Entry(
                id: "\(opened.timeSeconds)-\(index)",
                position: opened.misaligned ? .openMisaligned : .open,
                headline: headline,
                supporting: opened.isCurrent ? "Since \(time) · \(duration)" : duration,
                warnings: warnings
            )
        case .closed(let closed):
            let time = clockText(closed.timeSeconds)
            let duration = stateDuration(closed.durationSeconds, isCurrent: closed.isCurrent, isOpen: false)
            var warnings: [String] = []
            if let warning = closed.transitWarning { warnings.append(transitText(warning)) }
            return Entry(
                id: "\(closed.timeSeconds)-\(index)",
                position: .closed,
                headline: closed.isCurrent ? "Closed" : "Closed at \(time)",
                supporting: closed.isCurrent ? "Since \(time) · \(duration)" : duration,
                warnings: warnings
            )
        case .anomaly(let anomaly):
            return Entry(
                id: "\(anomaly.timeSeconds)-\(index)",
                position: anomaly.doorPosition,
                headline: anomalyTitle(anomaly.kind),
                supporting: clockText(anomaly.timeSeconds),
                warnings: []
            )
        }
    }

    private static func anomalyTitle(_ kind: AnomalyKind) -> String {
        switch onEnum(of: kind) {
        case .sensorConflict: return "Sensor conflict"
        case .unknownState: return "Unknown state"
        case .stuckOpening: return "Stuck opening"
        case .stuckClosing: return "Stuck closing"
        case .openMisaligned: return "Open (misaligned)"
        }
    }

    // MARK: - Duration formatting (mirrors HistoryContent + HistoryFormatter)

    /// "Open for X" / "Closed for X" / "X and counting". Granularity mirrors
    /// Android's `stateDurationDisplay`: days dominate (with hours), then hours
    /// (with minutes), then minutes, then seconds.
    private static func stateDuration(_ seconds: Int64, isCurrent: Bool, isOpen: Bool) -> String {
        let safe = max(seconds, 0)
        let days = Int(safe / 86_400)
        let hours = Int((safe % 86_400) / 3_600)
        let minutes = Int((safe % 3_600) / 60)
        let secs = Int(safe % 60)
        let text: String
        if days >= 1 {
            text = hours == 0 ? plural(days, "day") : "\(days) day \(hours) hr"
        } else if hours >= 1 {
            text = minutes == 0 ? "\(hours) hr" : "\(hours) hr \(minutes) min"
        } else if minutes >= 1 {
            text = "\(minutes) min"
        } else {
            text = "\(secs) sec"
        }
        if isCurrent { return "\(text) and counting" }
        return isOpen ? "Open for \(text)" : "Closed for \(text)"
    }

    /// "Took X to open/close, longer than expected". Transit granularity mirrors
    /// Android's `transitWarningText`: hours (with minutes), then minutes (with
    /// seconds), then seconds.
    private static func transitText(_ warning: TransitWarning) -> String {
        let seconds: Int64
        let opening: Bool
        switch onEnum(of: warning) {
        case .toOpen(let w): seconds = w.transitSeconds; opening = true
        case .toClose(let w): seconds = w.transitSeconds; opening = false
        }
        let safe = max(seconds, 0)
        let hours = Int(safe / 3_600)
        let minutes = Int((safe % 3_600) / 60)
        let secs = Int(safe % 60)
        let text: String
        if hours >= 1 {
            text = minutes == 0 ? "\(hours) hr" : "\(hours) hr \(minutes) min"
        } else if minutes >= 1 {
            text = secs == 0 ? "\(minutes) min" : "\(minutes) min \(secs) sec"
        } else {
            text = "\(secs) sec"
        }
        return opening
            ? "Took \(text) to open, longer than expected"
            : "Took \(text) to close, longer than expected"
    }

    private static func plural(_ value: Int, _ unit: String) -> String {
        value == 1 ? "\(value) \(unit)" : "\(value) \(unit)s"
    }

    // MARK: - Clock formatting (mirrors HistoryFormatter.formatTime)

    private static func clockText(_ epochSeconds: Int64) -> String {
        timeFormatter.string(from: Date(timeIntervalSince1970: TimeInterval(epochSeconds)))
    }

    private static let timeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US")
        formatter.dateFormat = "h:mm a"
        return formatter
    }()

    private static let dateLabelFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US")
        formatter.dateFormat = "EEE, MMM d"
        return formatter
    }()

    func refresh() { vm.fetchRecentDoorEvents() }

    /// Page in the next older window. Appended events flow back through
    /// `recentDoorEvents`; `isLoadingMore` / `canLoadMore` update via
    /// `paginationState`. The shared repo guards against re-entrant fetches, so
    /// a duplicate scroll-trigger fire is a no-op.
    func loadMore() { vm.fetchOlderDoorEvents() }

    /// Stale-banner recovery: deregister FCM (so it re-subscribes fresh) and
    /// refetch recent events. Mirrors Android's `DoorHistoryContent` reset-FCM
    /// banner action (`onResetFcm` + `onFetchRecentDoorEvents`).
    func resetFcmAndRefetch() {
        vm.deregisterFcm()
        vm.fetchRecentDoorEvents()
    }

    deinit { tasks.forEach { $0.cancel() } }
}
