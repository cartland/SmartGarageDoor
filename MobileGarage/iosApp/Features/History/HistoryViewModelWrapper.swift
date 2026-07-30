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
        /// "Today"/"Yesterday" are copy; an explicit date is already
        /// locale-formatted, so it is data. See `DisplayText`.
        let title: DisplayText
        let entries: [Entry]
    }

    /// One history row, fully resolved to display values. `position` drives the
    /// leading garage-door icon; the strings are already localized.
    struct Entry: Identifiable {
        let id: String
        let position: DoorPosition
        /// `LocalizedStringResource`, not `String`: as a `String` these were
        /// invisible to the compiler's extractor and could never be translated.
        let headline: LocalizedStringResource
        /// Copy for the state-duration lines; data for the anomaly rows, whose
        /// supporting text is just an already-formatted clock time.
        let supporting: DisplayText
        let warnings: [LocalizedStringResource]
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

        // `guard let stream = self?...` + `self?.` per iteration — NEVER
        // `guard let self` up front. These loops never end, so holding self
        // strongly keeps the wrapper (and with it the Kotlin ViewModel and its
        // collectors) alive for the life of the process: `deinit` can't run, so
        // `SharedViewModel.deinit` never clears the store, and every re-entry
        // into History stacks another live VM. Mirrors Home/Settings.
        tasks.append(Task { @MainActor [weak self] in
            guard let stream = self?.vm.recentDoorEvents else { return }
            for await result in stream {
                self?.apply(result)
                self?.rebuild()
            }
        })
        tasks.append(Task { @MainActor [weak self] in
            guard let stream = self?.vm.isCheckInStale else { return }
            for await stale in stream { self?.applyStale(stale.boolValue) }
        })
        tasks.append(Task { @MainActor [weak self] in
            guard let stream = self?.vm.nowEpochSeconds else { return }
            for await tick in stream {
                self?.nowEpochSeconds = tick.int64Value
                self?.rebuild()
            }
        })
        tasks.append(Task { @MainActor [weak self] in
            guard let stream = self?.vm.paginationState else { return }
            for await state in stream { self?.applyPagination(state) }
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

    private static func dayTitle(_ label: DayLabel) -> DisplayText {
        switch onEnum(of: label) {
        case .today: return .copy("Today")
        case .yesterday: return .copy("Yesterday")
        case .date(let d):
            var components = DateComponents()
            components.year = Int(d.year)
            components.month = Int(d.monthNumber)
            components.day = Int(d.dayOfMonth)
            guard let date = Calendar.current.date(from: components) else {
                return .data("\(d.monthNumber)/\(d.dayOfMonth)")
            }
            // Already formatted for the user's locale by the formatter — data,
            // not copy, so it must not be looked up in the catalog.
            return .data(dateLabelFormatter.string(from: date))
        }
    }

    // MARK: - Entry resolution (words for the shared HistoryRowMapper)

    /// Every decision this row makes — door art, which of six headlines, which
    /// supporting line, which tags and whether one is suppressed — comes from
    /// `HistoryRowMapper`. This only supplies the words, and the `id`.
    private static func resolve(_ entry: HistoryEntry, index: Int) -> Entry {
        let row = HistoryRowMapper.shared.forEntry(entry: entry)
        return Entry(
            id: "\(entryTimeSeconds(entry))-\(index)",
            position: row.doorPosition,
            headline: headlineText(row.headline),
            supporting: supportingText(row.supporting),
            warnings: row.tags.map { tagText($0) }
        )
    }

    /// iOS wording for one shared `HistoryHeadline`.
    private static func headlineText(_ headline: HistoryHeadline) -> LocalizedStringResource {
        switch onEnum(of: headline) {
        case .openNow:
            return "Open"
        case .openNowMisaligned:
            return "Open (misaligned)"
        case .openedAt(let h):
            return "Opened at \(clockText(h.timeSeconds))"
        case .closedNow:
            return "Closed"
        case .closedAt(let h):
            return "Closed at \(clockText(h.timeSeconds))"
        case .anomaly(let h):
            return anomalyTitle(h.kind)
        }
    }

    /// iOS wording for one shared `HistorySupporting`.
    private static func supportingText(_ supporting: HistorySupporting) -> DisplayText {
        switch onEnum(of: supporting) {
        case .sinceWithSpan(let s):
            let span = String(localized: stateSpanText(s.span))
            return .copy("Since \(clockText(s.timeSeconds)) · \(span)")
        case .span(let s):
            return .copy(stateSpanText(s.span))
        case .clockTime(let s):
            // An already-locale-formatted clock time: data, not copy.
            return .data(clockText(s.timeSeconds))
        }
    }

    /// iOS wording for one shared `HistoryTag`.
    private static func tagText(_ tag: HistoryTag) -> LocalizedStringResource {
        switch onEnum(of: tag) {
        case .transit(let t): return transitText(t.warning)
        case .misaligned: return "Door was misaligned"
        }
    }

    /// The row's own timestamp, used only to build a stable list identity.
    private static func entryTimeSeconds(_ entry: HistoryEntry) -> Int64 {
        switch onEnum(of: entry) {
        case .opened(let e): return e.timeSeconds
        case .closed(let e): return e.timeSeconds
        case .anomaly(let e): return e.timeSeconds
        }
    }

    private static func anomalyTitle(_ kind: AnomalyKind) -> LocalizedStringResource {
        switch onEnum(of: kind) {
        case .sensorConflict: return "Sensor conflict"
        case .unknownState: return "Unknown state"
        case .stuckOpening: return "Stuck opening"
        case .stuckClosing: return "Stuck closing"
        case .openMisaligned: return "Open (misaligned)"
        }
    }

    // MARK: - Duration formatting (mirrors HistoryContent + HistoryFormatter)

    /// "Open for X" / "Closed for X" / "X and counting".
    ///
    /// The bucket and the framing come from the shared `HistoryDurationMapper`;
    /// this supplies the words. Both were previously reimplemented here against
    /// Android's `stateDurationDisplay`, on the highest-traffic surface in the
    /// app — every row of the history list.
    private static func stateSpanText(_ display: StateSpanDisplay) -> LocalizedStringResource {
        // Resolved here so it can be interpolated into the framing's own
        // catalog entry ("Open for %@").
        let text = String(localized: stateDurationText(display.duration))
        switch display.framing {
        case .andCounting: return "\(text) and counting"
        case .openFor: return "Open for \(text)"
        case .closedFor: return "Closed for \(text)"
        }
    }

    /// iOS wording for one shared `StateSpanDuration` arm.
    private static func stateDurationText(_ duration: StateSpanDuration) -> LocalizedStringResource {
        switch onEnum(of: duration) {
        case .days(let d):
            return d.days == 1
                ? "1 day"
                : "\(d.days) days"
        case .daysHours(let d):
            return "\(d.days) day \(d.hours) hr"
        case .hours(let d):
            return "\(d.hours) hr"
        case .hoursMinutes(let d):
            return "\(d.hours) hr \(d.minutes) min"
        case .minutes(let d):
            return "\(d.minutes) min"
        case .seconds(let d):
            return "\(d.seconds) sec"
        }
    }

    /// "Took X to open/close, longer than expected".
    ///
    /// Uses the transit ladder, which deliberately keeps seconds at minute
    /// scale — for a slow door the seconds are the interesting part. See
    /// `TransitSpanDuration`.
    private static func transitText(_ warning: TransitWarning) -> LocalizedStringResource {
        let seconds: Int64
        let opening: Bool
        switch onEnum(of: warning) {
        case .toOpen(let w): seconds = w.transitSeconds; opening = true
        case .toClose(let w): seconds = w.transitSeconds; opening = false
        }
        let text = String(localized: transitDurationText(HistoryDurationMapper.shared.transitSpan(seconds: seconds)))
        return opening
            ? "Took \(text) to open, longer than expected"
            : "Took \(text) to close, longer than expected"
    }

    /// iOS wording for one shared `TransitSpanDuration` arm.
    private static func transitDurationText(_ duration: TransitSpanDuration) -> LocalizedStringResource {
        switch onEnum(of: duration) {
        case .hours(let d):
            return "\(d.hours) hr"
        case .hoursMinutes(let d):
            return "\(d.hours) hr \(d.minutes) min"
        case .minutes(let d):
            return "\(d.minutes) min"
        case .minutesSeconds(let d):
            return "\(d.minutes) min \(d.seconds) sec"
        case .seconds(let d):
            return "\(d.seconds) sec"
        }
    }

    // MARK: - Clock formatting (mirrors HistoryFormatter.formatTime)

    private static func clockText(_ epochSeconds: Int64) -> String {
        timeFormatter.string(from: Date(timeIntervalSince1970: TimeInterval(epochSeconds)))
    }

    private static let timeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        // A localized template, not a fixed pattern: the device decides 12- vs
        // 24-hour and field order. Pinning "h:mm a" + en_US shows AM/PM to a
        // user whose phone is set to 24-hour time. Android uses
        // `DateTimeFormatter.ofLocalizedTime` for the same reason.
        formatter.setLocalizedDateFormatFromTemplate("jmm")
        return formatter
    }()

    private static let dateLabelFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.setLocalizedDateFormatFromTemplate("EEEMMMd")
        return formatter
    }()

    /// Async so `.refreshable` keeps the system indicator up until the fetch
    /// settles; a synchronous body dismisses it immediately and the pull reads
    /// as a no-op. Also records the user action in the shared diagnostics log,
    /// which Android has always done and iOS was silently skipping.
    func refresh() async {
        vm.log(key: AppLoggerKeys.shared.USER_FETCH_RECENT_DOOR)
        isLoading = true
        vm.fetchRecentDoorEvents()
        let deadline = Date().addingTimeInterval(15)
        while isLoading, Date() < deadline {
            try? await Task.sleep(nanoseconds: 120_000_000)
        }
    }

    /// Page in the next older window. Appended events flow back through
    /// `recentDoorEvents`; `isLoadingMore` / `canLoadMore` update via
    /// `paginationState`. The shared repo guards against re-entrant fetches, so
    /// a duplicate scroll-trigger fire is a no-op.
    func loadMore() {
        vm.log(key: AppLoggerKeys.shared.USER_LOAD_MORE_DOOR)
        vm.fetchOlderDoorEvents()
    }

    /// Stale-banner recovery: deregister FCM (so it re-subscribes fresh) and
    /// refetch recent events. Mirrors Android's `DoorHistoryContent` reset-FCM
    /// banner action (`onResetFcm` + `onFetchRecentDoorEvents`).
    func resetFcmAndRefetch() {
        vm.deregisterFcm()
        vm.fetchRecentDoorEvents()
    }

    deinit { tasks.forEach { $0.cancel() } }
}
