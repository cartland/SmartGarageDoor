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
import UserNotifications
@preconcurrency import shared

/// Bridges `DefaultHomeViewModel` to SwiftUI. See `DiagnosticsViewModelWrapper`
/// for the shared observe pattern.
@MainActor
final class HomeViewModelWrapper: ObservableObject {
    @Published private(set) var signedIn: Bool = false
    @Published private(set) var doorPosition: DoorPosition = .unknown
    /// Pre-formatted "Since 9:47 AM · 2 hr 14 min" status line, resolved from
    /// the shared VM's typed `SinceStatus` (ADR-031); `nil` when the last-change
    /// time is unknown. The elapsed-bucket *logic* is shared; the clock-time +
    /// localized-unit formatting happen here (mirrors Android's
    /// `rememberSinceLine`).
    @Published private(set) var sinceLine: String?
    /// Localized text for the typed `DoorWarning` exposed by the shared VM
    /// (ADR-031), or `nil` when the current state warrants no warning. The
    /// shared layer emits a *typed* warning; this wrapper resolves it to a
    /// string here (iOS's localization boundary) — mirrors Android's
    /// `doorWarningText` Composable + `strings.xml`.
    @Published private(set) var warningText: String?
    @Published private(set) var lastChangeTimeSeconds: Int64?
    @Published private(set) var isCheckInStale: Bool = false
    /// Resolved device check-in pill (ADR-031 Phase 5). The shared
    /// `CheckInStatusMapper` buckets the heartbeat age + decides staleness; this
    /// wrapper formats the "… ago" string per-UI (mirrors Android's
    /// `DeviceCheckIn.format`). `label == nil` until the first heartbeat.
    @Published private(set) var checkIn: DeviceCheckInItem = DeviceCheckInItem(label: nil, isStale: false)
    @Published private(set) var buttonStateLabel: String = "Ready"
    @Published private(set) var buttonHealthLabel: String = "Unknown"
    /// Resolved alert banners shown above the Status card (ADR-031 Phase 4).
    /// The shared `HomeAlertMapper` decides WHICH banners to show from typed
    /// inputs; this wrapper resolves each typed `HomeAlert` to iOS banner copy.
    @Published private(set) var alerts: [HomeAlertItem] = []

    private let shared: SharedViewModel<DefaultHomeViewModel>
    private var tasks: [Task<Void, Never>] = []
    private var vm: DefaultHomeViewModel { shared.instance }

    // Inputs to the shared `HomeAlertMapper`. The door result + stale flag come
    // from the VM; the notification-permission pieces are resolved per-UI here
    // (iOS `UNUserNotificationCenter`, the analog of Android's runtime
    // permission). `notificationGranted` defaults to `true` so the permission
    // banner doesn't flash before the async settings probe resolves.
    private var latestDoorResult: LoadingResult<DoorEvent>?
    // Latest wall clock (epoch seconds), from the VM's `nowEpochSeconds`
    // StateFlow. Drives the check-in "… ago" re-bucketing each tick.
    private var latestNowEpochSeconds: Int64 = 0
    private var notificationGranted: Bool = true
    private var notificationRequestCount: Int32 = 0

    init(component: NativeComponent) {
        shared = SharedViewModel(component.homeViewModel)
        // Seed the stale flag before `applyDoor` so the first `rebuildAlerts`
        // sees the real value rather than the default `false`.
        isCheckInStale = vm.isCheckInStale.value.boolValue
        // Seed the clock before `applyDoor` so the first `rebuildCheckIn` (called
        // from `applyDoor`) sees the real now rather than 0.
        latestNowEpochSeconds = vm.nowEpochSeconds.value.int64Value
        applyAuth(vm.authState.value)
        applyDoor(vm.currentDoorEvent.value)
        applyWarning(vm.warning.value)
        applySince(vm.sinceStatus.value)
        applyButton(vm.buttonState.value)
        applyHealth(vm.buttonHealthDisplay.value)
        // Async — rebuilds the alert stack again once the OS settings resolve.
        refreshNotificationPermission()

        tasks.append(Task { @MainActor [weak self] in
            for await v in self!.vm.authState { self?.applyAuth(v) }
        })
        tasks.append(Task { @MainActor [weak self] in
            for await v in self!.vm.currentDoorEvent { self?.applyDoor(v) }
        })
        tasks.append(Task { @MainActor [weak self] in
            for await v in self!.vm.warning { self?.applyWarning(v) }
        })
        tasks.append(Task { @MainActor [weak self] in
            for await v in self!.vm.sinceStatus { self?.applySince(v) }
        })
        tasks.append(Task { @MainActor [weak self] in
            for await v in self!.vm.buttonState { self?.applyButton(v) }
        })
        tasks.append(Task { @MainActor [weak self] in
            for await v in self!.vm.buttonHealthDisplay { self?.applyHealth(v) }
        })
        tasks.append(Task { @MainActor [weak self] in
            for await v in self!.vm.isCheckInStale {
                self?.isCheckInStale = v.boolValue
                self?.rebuildAlerts()
            }
        })
        tasks.append(Task { @MainActor [weak self] in
            for await v in self!.vm.nowEpochSeconds {
                self?.latestNowEpochSeconds = v.int64Value
                self?.rebuildCheckIn()
            }
        })
    }

    private func applyAuth(_ state: AuthState) {
        if case .authenticated = onEnum(of: state) {
            signedIn = true
        } else {
            signedIn = false
        }
    }

    private func applyDoor(_ result: LoadingResult<DoorEvent>) {
        latestDoorResult = result
        let event = result.data
        doorPosition = event?.doorPosition ?? .unknown
        lastChangeTimeSeconds = event?.lastChangeTimeSeconds?.int64Value
        rebuildAlerts()
        rebuildCheckIn()
    }

    /// Recomputes the banner stack from the shared `HomeAlertMapper` using the
    /// latest door result + stale flag + per-UI notification-permission state.
    /// The mapper decides which typed alerts apply; `resolve` turns each into
    /// iOS banner copy.
    private func rebuildAlerts() {
        guard let result = latestDoorResult else {
            alerts = []
            return
        }
        let typed = HomeAlertMapper.shared.toHomeAlerts(
            currentDoorEvent: result,
            isCheckInStale: isCheckInStale,
            notificationPermissionGranted: notificationGranted,
            notificationRequestCount: notificationRequestCount
        )
        alerts = typed.map { Self.resolve($0) }
    }

    /// Reads the current notification authorization and rebuilds the alerts.
    /// `.authorized` / `.provisional` / `.ephemeral` all count as granted —
    /// only a hard denial / not-yet-asked surfaces the permission banner.
    private func refreshNotificationPermission() {
        UNUserNotificationCenter.current().getNotificationSettings { [weak self] settings in
            let granted = settings.authorizationStatus == .authorized
                || settings.authorizationStatus == .provisional
                || settings.authorizationStatus == .ephemeral
            Task { @MainActor in
                self?.notificationGranted = granted
                self?.rebuildAlerts()
            }
        }
    }

    /// Bumps the attempt count (drives the escalation copy, mirroring Android's
    /// `rememberSaveable` counter) and asks the OS for authorization. Once a
    /// user has denied, iOS silently returns the existing status without
    /// re-prompting — the escalation lines then point them at Settings.
    private func requestNotificationPermission() {
        notificationRequestCount += 1
        rebuildAlerts()
        UNUserNotificationCenter.current()
            .requestAuthorization(options: [.alert, .badge, .sound]) { [weak self] _, _ in
                Task { @MainActor in self?.refreshNotificationPermission() }
            }
    }

    /// Resolves a typed `HomeAlert` to its iOS banner copy. Stale + fetch-error
    /// strings mirror Android's `home_alert_*` resources; the permission copy is
    /// iOS-specific (points at the Settings app, not Android system settings).
    private static func resolve(_ alert: HomeAlert) -> HomeAlertItem {
        switch onEnum(of: alert) {
        case .stale:
            return HomeAlertItem(
                id: "stale",
                kind: .stale,
                message: "Not receiving updates from server",
                actionLabel: "Retry"
            )
        case .permissionMissing(let permission):
            return HomeAlertItem(
                id: "permission",
                kind: .permission,
                message: justificationText(attemptCount: permission.attemptCount),
                actionLabel: "Allow"
            )
        case .fetchError(let fetchError):
            return HomeAlertItem(
                id: "fetchError",
                kind: .fetchError,
                message: "Error fetching current door event: \(fetchError.truncatedException)",
                actionLabel: "Retry"
            )
        }
    }

    /// Assembles the multi-line permission justification from the attempt count.
    /// Mirrors Android's `notificationJustificationText` escalation (3+, 4+, 5+)
    /// but with iOS-appropriate wording.
    private static func justificationText(attemptCount: Int32) -> String {
        var lines = ["Turn on notifications to get alerted when the door is left open."]
        if attemptCount > 2 {
            lines.append("You can manage permissions in the iOS Settings app.")
        }
        if attemptCount > 3 {
            lines.append("iOS may be blocking requests because the permission was denied multiple times.")
        }
        if attemptCount > 4 {
            lines.append("You have tapped the button \(attemptCount) times.")
        }
        return lines.joined(separator: "\n")
    }

    /// Builds the "Since {time} · {duration}" line from the shared typed
    /// `SinceStatus`. The elapsed bucket is decided in shared code; here we
    /// format the clock time (same-day → time only, else month + time) and the
    /// localized units — the unit wording mirrors Android's `home_duration_*`
    /// resources verbatim. `nil` status → no line (unknown last-change time).
    private func applySince(_ status: SinceStatus?) {
        guard let status else {
            sinceLine = nil
            return
        }
        let date = Date(timeIntervalSince1970: TimeInterval(status.sinceEpochSeconds))
        sinceLine = "Since \(Self.clockText(for: date)) · \(Self.durationText(for: status.elapsed))"
    }

    private static let timeOnlyFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US")
        formatter.dateFormat = "h:mm a"
        return formatter
    }()

    private static let dateTimeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US")
        formatter.dateFormat = "MMM d, h:mm a"
        return formatter
    }()

    private static func clockText(for date: Date) -> String {
        let formatter = Calendar.current.isDateInToday(date) ? timeOnlyFormatter : dateTimeFormatter
        return formatter.string(from: date)
    }

    private static func durationText(for elapsed: ElapsedDuration) -> String {
        switch onEnum(of: elapsed) {
        case .days(let days):
            return days.days == 1 ? "1 day" : "\(days.days) days"
        case .hoursMinutes(let hm):
            return "\(hm.hours) hr \(hm.minutes) min"
        case .minutes(let minutes):
            return minutes.minutes == 1 ? "1 min" : "\(minutes.minutes) min"
        case .seconds(let seconds):
            return seconds.seconds == 1 ? "1 sec" : "\(seconds.seconds) sec"
        }
    }

    /// Resolves the shared typed `DoorWarning` to a localized string. The four
    /// fallback strings mirror Android's `home_warning_*` resources verbatim so
    /// both platforms read identically; a server-supplied message renders as-is.
    private func applyWarning(_ warning: DoorWarning?) {
        guard let warning else {
            warningText = nil
            return
        }
        switch onEnum(of: warning) {
        case .serverMessage(let message):
            warningText = message.text
        case .openingTooLong:
            warningText = "Opening, taking longer than expected"
        case .closingTooLong:
            warningText = "Closing, taking longer than expected"
        case .openMisaligned:
            warningText = "Door is open and misaligned"
        case .sensorConflict:
            warningText = "Sensor conflict. Check the door."
        }
    }

    private func applyButton(_ state: RemoteButtonState) {
        buttonStateLabel = HomeViewModelWrapper.label(for: state)
    }

    private func applyHealth(_ display: UsecaseButtonHealthDisplay) {
        switch onEnum(of: display) {
        case .offline(let offline):
            buttonHealthLabel = "Offline (\(offline.durationLabel))"
        case .online:
            buttonHealthLabel = "Online"
        case .loading:
            buttonHealthLabel = "Checking…"
        case .unauthorized:
            buttonHealthLabel = "Sign in to see device health"
        case .unknown:
            buttonHealthLabel = "Unknown"
        }
    }

    /// Recomputes the check-in pill from the latest door event's heartbeat
    /// timestamp + the live clock, via the shared `CheckInStatusMapper`. The
    /// mapper decides the bucket + staleness; `resolveCheckIn` formats the
    /// "… ago" string here (iOS's localization boundary, mirroring Android's
    /// `DeviceCheckIn.format`).
    private func rebuildCheckIn() {
        let status = CheckInStatusMapper.shared.forCheckIn(
            lastCheckInEpochSeconds: latestDoorResult?.data?.lastCheckInTimeSeconds,
            nowEpochSeconds: latestNowEpochSeconds
        )
        checkIn = Self.resolveCheckIn(status)
    }

    private static func resolveCheckIn(_ status: CheckInStatus) -> DeviceCheckInItem {
        switch onEnum(of: status) {
        case .noData:
            return DeviceCheckInItem(label: nil, isStale: false)
        case .reported(let reported):
            return DeviceCheckInItem(label: agoText(reported.age), isStale: reported.isStale)
        }
    }

    /// Formats a typed `CheckInAge` bucket as "… ago". Mirrors Android's
    /// `DeviceCheckIn.label` verbatim so both platforms read identically.
    private static func agoText(_ age: CheckInAge) -> String {
        switch onEnum(of: age) {
        case .justNow:
            return "Just now"
        case .seconds(let s):
            return "\(s.seconds) sec ago"
        case .minutes(let m):
            return m.seconds == 0 ? "\(m.minutes) min ago" : "\(m.minutes) min \(m.seconds) sec ago"
        case .hours(let h):
            return h.minutes == 0 ? "\(h.hours) hr ago" : "\(h.hours) hr \(h.minutes) min ago"
        case .days(let d):
            return d.days == 1 ? "1 day ago" : "\(d.days) days ago"
        }
    }

    private static func label(for state: RemoteButtonState) -> String {
        switch onEnum(of: state) {
        case .ready: return "Tap to open / close"
        case .preparing: return "Preparing…"
        case .awaitingConfirmation: return "Tap again to confirm"
        case .cancelled: return "Cancelled"
        case .sendingToServer: return "Sending…"
        case .sendingToDoor: return "Waiting for door…"
        case .succeeded: return "Done"
        case .serverFailed: return "Server error"
        case .doorFailed: return "Door did not move"
        }
    }

    func onButtonTap() { vm.onButtonTap() }
    func refresh() {
        vm.fetchCurrentDoorEvent()
        vm.refreshButtonHealth()
    }

    /// Handles a tap on a banner's action button. Mirrors Android's
    /// `onAlertAction` in the Home route wrapper.
    func onAlertAction(_ kind: HomeAlertItem.Kind) {
        switch kind {
        case .stale:
            // Re-register FCM + refetch, matching Android's "fix outdated info".
            vm.deregisterFcm()
            vm.fetchCurrentDoorEvent()
        case .permission:
            requestNotificationPermission()
        case .fetchError:
            vm.fetchCurrentDoorEvent()
        }
    }

    deinit { tasks.forEach { $0.cancel() } }
}

/// Resolved alert-banner display data for the Home tab. The shared
/// `HomeAlertMapper` picks which typed `HomeAlert`s apply; the wrapper resolves
/// each to this view-ready struct (icon-driving `kind` + localized copy).
/// `internal` so `#Preview` fixtures can construct it (the generated snapshot
/// test embeds preview bodies verbatim and can't see `private` symbols).
struct HomeAlertItem: Identifiable {
    enum Kind { case stale, permission, fetchError }

    let id: String
    let kind: Kind
    let message: String
    let actionLabel: String
}

/// View-ready device check-in pill data (ADR-031 Phase 5). The shared
/// `CheckInStatusMapper` buckets the heartbeat age + staleness; the wrapper
/// resolves it to this struct (formatting the "… ago" `label` per-UI).
/// `label == nil` means no heartbeat has been observed yet (icon-only pill).
/// `internal` so `#Preview` fixtures can build it (the generated snapshot test
/// embeds preview bodies verbatim and can't see `private` symbols).
struct DeviceCheckInItem {
    let label: String?
    let isStale: Bool
}
