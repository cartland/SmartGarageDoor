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

/// Home tab — current door status, the open/close button, device health.
/// Thin shell: owns the ViewModel wrapper and binds its state into the pure,
/// preview-able `HomeContentView`.
struct HomeScreen: View {
    @StateObject private var wrapper: HomeViewModelWrapper

    init(component: NativeComponent) {
        _wrapper = StateObject(wrappedValue: HomeViewModelWrapper(component: component))
    }

    var body: some View {
        HomeContentView(
            doorPosition: wrapper.doorPosition,
            lastChangeTimeSeconds: wrapper.lastChangeTimeSeconds,
            sinceLine: wrapper.sinceLine,
            warningText: wrapper.warningText,
            isCheckInStale: wrapper.isCheckInStale,
            buttonItem: wrapper.buttonItem,
            buttonHealth: wrapper.buttonHealth,
            signedIn: wrapper.signedIn,
            alerts: wrapper.alerts,
            checkIn: wrapper.checkIn,
            onButtonTap: { wrapper.onButtonTap() },
            onSignIn: { wrapper.signInWithGoogle() },
            onRefresh: { wrapper.refresh() },
            onAlertAction: { wrapper.onAlertAction($0) }
        )
    }
}

/// Pure Home content — takes plain values + actions so it renders without a live
/// `NativeComponent` (mirrors Android's `HomeContent(state)`). This is what the
/// `#Preview`s and snapshot gallery capture.
struct HomeContentView: View {
    let doorPosition: DoorPosition
    /// Server timestamp of the door's last position change. Threaded to the live
    /// `GarageDoorView(animated:)` so the open/close slide replays once per
    /// motion event (cold-open / first view), not on every re-render. `nil` when
    /// the last-change time is unknown. Mirrors Android's `lastChangeTimeSeconds`
    /// passed to `GarageIcon`.
    let lastChangeTimeSeconds: Int64?
    /// Pre-formatted "Since 9:47 AM · 2 hr 14 min" line (resolved from the shared
    /// typed `SinceStatus` in the wrapper); `nil` when the last-change time is
    /// unknown. Mirrors Android's status line; replaces the old raw door message.
    let sinceLine: String?
    /// Already-localized warning text (resolved from the shared typed
    /// `DoorWarning` in the wrapper). Non-nil only for stuck/anomalous states.
    let warningText: String?
    let isCheckInStale: Bool
    /// View-ready remote-button state — styling kind + copy + progress phase.
    /// All logic lives in the shared `ButtonStateMachine`; this is display data
    /// (mirrors Android's `GarageDoorButton` + `NetworkProgressDiagram`).
    let buttonItem: RemoteButtonItem
    /// Resolved remote-button health pill (ADR-031 Phase 5) shown in the
    /// "Remote control" section header, mirroring Android's `RemoteButtonHealthPill`.
    let buttonHealth: ButtonHealthItem
    let signedIn: Bool
    /// Resolved alert banners (ADR-031 Phase 4) shown above the Status card.
    /// Empty in the steady state; the shared `HomeAlertMapper` decides when a
    /// stale / permission / fetch-error banner applies.
    let alerts: [HomeAlertItem]
    /// Resolved device check-in pill (ADR-031 Phase 5) shown in the Status
    /// section header. `label == nil` renders icon-only (no heartbeat yet).
    let checkIn: DeviceCheckInItem
    let onButtonTap: () -> Void
    /// Triggers Google Sign-In from Home — shown only when signed out, where the
    /// remote button is replaced by a sign-in CTA (mirrors Android's signed-out
    /// Home, which hides the button and shows a "Sign in" card).
    let onSignIn: () -> Void
    let onRefresh: () -> Void
    let onAlertAction: (HomeAlertItem.Kind) -> Void

    /// Which per-pill info sheet is open. Pure local UI state (no VM data),
    /// mirroring Android's `openInfoSheet` `remember` in `HomeContent`. An
    /// explicit `init` for the injected values keeps this `private @State`
    /// from lowering the synthesized memberwise initializer to `private`
    /// (which would break the generated snapshot test that constructs the
    /// view across files).
    @State private var activeInfoSheet: HomeInfoSheet?

    init(
        doorPosition: DoorPosition,
        lastChangeTimeSeconds: Int64?,
        sinceLine: String?,
        warningText: String?,
        isCheckInStale: Bool,
        buttonItem: RemoteButtonItem,
        buttonHealth: ButtonHealthItem,
        signedIn: Bool,
        alerts: [HomeAlertItem],
        checkIn: DeviceCheckInItem,
        onButtonTap: @escaping () -> Void,
        onSignIn: @escaping () -> Void,
        onRefresh: @escaping () -> Void,
        onAlertAction: @escaping (HomeAlertItem.Kind) -> Void
    ) {
        self.doorPosition = doorPosition
        self.lastChangeTimeSeconds = lastChangeTimeSeconds
        self.sinceLine = sinceLine
        self.warningText = warningText
        self.isCheckInStale = isCheckInStale
        self.buttonItem = buttonItem
        self.buttonHealth = buttonHealth
        self.signedIn = signedIn
        self.alerts = alerts
        self.checkIn = checkIn
        self.onButtonTap = onButtonTap
        self.onSignIn = onSignIn
        self.onRefresh = onRefresh
        self.onAlertAction = onAlertAction
    }

    var body: some View {
        List {
            if !alerts.isEmpty {
                Section {
                    ForEach(alerts) { alert in
                        HomeAlertBanner(alert: alert, onAction: { onAlertAction(alert.kind) })
                            .listRowBackground(GarageColors.statusWarning.opacity(0.12))
                    }
                }
            }

            Section {
                VStack(spacing: GarageSpacing.card) {
                    // Live door — the one surface that drives the full shared
                    // trajectory (12 s linear slide + spring-settle on the
                    // terminal event), replaying once per motion event keyed by
                    // `lastChangeTimeSeconds`. History rows stay static.
                    GarageDoorView(
                        position: doorPosition,
                        isStale: isCheckInStale,
                        animated: true,
                        lastChangeTimeSeconds: lastChangeTimeSeconds
                    )
                    .frame(height: 160)
                    .frame(maxWidth: .infinity)
                    VStack(spacing: GarageSpacing.tight) {
                        Text(doorPosition.statusLabel)
                            .font(.title2.weight(.semibold))
                        if let sinceLine {
                            Text(sinceLine)
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                                .multilineTextAlignment(.center)
                        }
                        if let warningText {
                            DoorWarningChip(text: warningText)
                        }
                        // Staleness now surfaces via the top Stale banner +
                        // the muted door color (`GarageDoorView(isStale:)`),
                        // matching Android — no separate in-card label.
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, GarageSpacing.tight)
            } header: {
                // Pill right-aligned in the section header, mirroring Android's
                // `DeviceCheckInPill` in the "Status" header row.
                HStack {
                    Text("Status")
                    Spacer()
                    DeviceCheckInPill(item: checkIn)
                        .contentShape(Capsule())
                        .onTapGesture { activeInfoSheet = .doorStatus }
                }
            }

            Section {
                if signedIn {
                    RemoteButtonView(item: buttonItem, onTap: onButtonTap)
                        .padding(.vertical, GarageSpacing.tight)
                } else {
                    // Signed out — the remote action requires auth (the shared use
                    // case returns NotAuthenticated). A tappable sign-in row
                    // mirrors Android's `HomeSignInBody` ListItem (leading icon,
                    // title + subtitle, chevron) — the whole row starts sign-in.
                    HomeSignInRow(onSignIn: onSignIn)
                }
            } header: {
                // Health pill right-aligned in the header, mirroring Android's
                // `RemoteButtonHealthPill` in the "Remote control" section header.
                // Hidden when signed out (it would only read "Unauthorized",
                // redundant with the CTA) — matching Android, which also swaps
                // the section label to "Sign in" when signed out.
                HStack {
                    Text(signedIn ? "Remote control" : "Sign in")
                    Spacer()
                    if signedIn {
                        RemoteButtonHealthPill(item: buttonHealth)
                            .contentShape(Capsule())
                            .onTapGesture { activeInfoSheet = .remoteControl }
                    }
                }
            }

            // The redundant "Account" status row was removed — Settings owns the
            // real account display + sign-out; Home's auth state now drives the
            // Remote button section above (signed-out → sign-in CTA).
        }
        .navigationTitle("Garage")
        .refreshable { onRefresh() }
        .sheet(item: $activeInfoSheet) { sheet in
            HomeInfoSheetView(sheet: sheet)
                .presentationDetents([.medium, .large])
        }
    }
}

/// One alert banner — the SwiftUI analog of Android's `HomeAlertCard`. Renders
/// the already-resolved message + an action button. The leading icon is driven
/// by the typed `kind`; the red-tinted list-row background (set by the caller)
/// gives the banner its alert weight (mirrors Android's `errorContainer`).
private struct HomeAlertBanner: View {
    let alert: HomeAlertItem
    let onAction: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: GarageSpacing.card) {
            Image(systemName: icon)
                .foregroundStyle(GarageColors.statusWarning)
            Text(alert.message)
                .font(.footnote)
                .frame(maxWidth: .infinity, alignment: .leading)
            Button(alert.actionLabel, action: onAction)
                .buttonStyle(.bordered)
                .font(.footnote)
        }
        .padding(.vertical, GarageSpacing.tight)
    }

    private var icon: String {
        switch alert.kind {
        case .stale: return "wifi.slash"
        case .permission: return "bell.badge"
        case .fetchError: return "exclamationmark.triangle"
        }
    }
}

/// The remote garage button + network progress diagram — the SwiftUI analog of
/// Android's `RemoteButtonContent` (`GarageDoorButton` + `NetworkProgressDiagram`).
/// Stateless: all logic (two-tap confirm, timeouts, network coordination) lives
/// in the shared `ButtonStateMachine`; this renders the view-ready `item` and
/// forwards taps. `internal` so the `#Preview` fixtures can render it.
struct RemoteButtonView: View {
    let item: RemoteButtonItem
    let onTap: () -> Void

    var body: some View {
        VStack(spacing: GarageSpacing.card) {
            Button(action: onTap) {
                VStack(spacing: 2) {
                    if item.kind == .busy {
                        HStack(spacing: GarageSpacing.tight) {
                            ProgressView().controlSize(.small).tint(.secondary)
                            Text(item.title).font(.headline)
                        }
                    } else {
                        Text(item.title).font(.headline)
                    }
                    if let subtitle = item.subtitle {
                        Text(subtitle).font(.subheadline)
                    }
                }
                .frame(maxWidth: .infinity, minHeight: 44)
            }
            .buttonStyle(.borderedProminent)
            .tint(buttonTint)
            .foregroundStyle(buttonForeground)
            .disabled(!isTappable)
            RemoteProgressDiagram(phase: item.phase)
        }
        .animation(.easeInOut(duration: 0.2), value: item.kind)
    }

    /// Only Ready and AwaitingConfirmation accept taps — every other state is
    /// display-only until the shared state machine returns to Ready (mirrors
    /// Android's disabled `FilledTonalButton` states).
    private var isTappable: Bool {
        item.kind == .ready || item.kind == .confirm
    }

    /// Amber for the confirm state (Android's `cautionContainer`), green
    /// success, red failure, neutral tint otherwise. System colors adapt to
    /// dark mode on their own.
    private var buttonTint: Color {
        switch item.kind {
        case .confirm: return .orange
        case .succeeded: return .green
        case .failed: return .red
        case .ready, .busy, .idle: return .accentColor
        }
    }

    private var buttonForeground: Color {
        switch item.kind {
        case .confirm, .succeeded, .failed: return .white
        case .ready, .busy, .idle: return .white
        }
    }
}

/// Phone → cloud → house progress diagram under the remote button — the SwiftUI
/// analog of Android's `NetworkProgressDiagram`. Node/edge activation follows
/// Android's `RemoteButtonDiagramMapping` table: sending-to-server animates the
/// first leg, sending-to-door completes it and animates the second, success
/// fills everything, failure marks the failing leg red. `internal` for previews.
struct RemoteProgressDiagram: View {
    let phase: RemoteButtonItem.Phase?

    var body: some View {
        HStack(spacing: GarageSpacing.tight) {
            node("iphone", status: phoneStatus)
            edge(status: firstEdge)
            node("cloud", status: cloudStatus)
            edge(status: secondEdge)
            node("house", status: houseStatus)
        }
        .padding(.horizontal, GarageSpacing.card)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityText)
    }

    private enum NodeStatus { case idle, active, succeeded, failed }
    private enum EdgeStatus { case notStarted, inProgress, succeeded, failed }

    private var phoneStatus: NodeStatus {
        switch phase {
        case nil: return .idle
        case .sendingToServer: return .active
        case .sendingToDoor, .succeeded: return .succeeded
        case .serverFailed: return .failed
        case .doorFailed: return .succeeded
        }
    }

    private var cloudStatus: NodeStatus {
        switch phase {
        case nil, .sendingToServer, .serverFailed: return .idle
        case .sendingToDoor: return .active
        case .succeeded: return .succeeded
        case .doorFailed: return .failed
        }
    }

    private var houseStatus: NodeStatus {
        switch phase {
        case .succeeded: return .succeeded
        default: return .idle
        }
    }

    private var firstEdge: EdgeStatus {
        switch phase {
        case nil: return .notStarted
        case .sendingToServer: return .inProgress
        case .sendingToDoor, .succeeded, .doorFailed: return .succeeded
        case .serverFailed: return .failed
        }
    }

    private var secondEdge: EdgeStatus {
        switch phase {
        case nil, .sendingToServer, .serverFailed: return .notStarted
        case .sendingToDoor: return .inProgress
        case .succeeded: return .succeeded
        case .doorFailed: return .failed
        }
    }

    private func node(_ systemName: String, status: NodeStatus) -> some View {
        Image(systemName: systemName)
            .font(.footnote)
            .foregroundStyle(color(for: status))
    }

    private func color(for status: NodeStatus) -> Color {
        switch status {
        case .idle: return Color(uiColor: .tertiaryLabel)
        case .active: return .accentColor
        case .succeeded: return .green
        case .failed: return .red
        }
    }

    private func edge(status: EdgeStatus) -> some View {
        RoundedRectangle(cornerRadius: 1)
            .fill(edgeColor(for: status))
            .frame(maxWidth: .infinity)
            .frame(height: 2)
    }

    private func edgeColor(for status: EdgeStatus) -> Color {
        switch status {
        case .notStarted: return Color(uiColor: .tertiarySystemFill)
        case .inProgress: return .accentColor
        case .succeeded: return .green
        case .failed: return .red
        }
    }

    private var accessibilityText: String {
        switch phase {
        case nil: return "Remote button idle"
        case .sendingToServer: return "Sending to server"
        case .sendingToDoor: return "Waiting for the door"
        case .succeeded: return "Command delivered"
        case .serverFailed: return "Server error"
        case .doorFailed: return "Door did not respond"
        }
    }
}

/// Signed-out sign-in row — the SwiftUI analog of Android's `HomeSignInBody`
/// ListItem (leading icon, title + subtitle, chevron; whole row tappable).
/// `internal` for previews.
struct HomeSignInRow: View {
    let onSignIn: () -> Void

    var body: some View {
        Button(action: onSignIn) {
            HStack(spacing: GarageSpacing.card) {
                Image(systemName: "person.crop.circle.badge.plus")
                    .font(.title3)
                    .foregroundStyle(.tint)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Sign in with Google")
                        .font(.body)
                        .foregroundStyle(.primary)
                    Text("Required to use the remote button")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Color(uiColor: .tertiaryLabel))
            }
            // A .plain Button only hit-tests rendered content — the Spacer gap
            // would be dead without an explicit content shape.
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// Compact device-heartbeat pill in the Home "Status" section header — the
/// SwiftUI analog of Android's `DeviceCheckInPill`. `wifi` icon + "… ago" text
/// when fresh; `wifi.slash` + warning tint when stale (>11 min). Text is
/// hidden (icon only) until the first heartbeat is observed (`label == nil`).
///
/// Cross-platform icon pairing (recorded per the parity audit): Android
/// Material `Sensors`/`SensorsOff` ↔ iOS SF `wifi`/`wifi.slash`, the nearest SF
/// visual equivalent (concentric signal arcs from a point). The two icon
/// systems can't share a literal glyph, so this pairing is the agreed match.
/// `.textCase(nil)` keeps the duration text from being uppercased by the
/// surrounding section-header style.
private struct DeviceCheckInPill: View {
    let item: DeviceCheckInItem

    var body: some View {
        HStack(spacing: GarageSpacing.tight) {
            if let label = item.label {
                Text(label)
                    .font(.caption2.weight(.medium))
                    .textCase(nil)
            }
            Image(systemName: item.isStale
                ? "wifi.slash"
                : "wifi")
                .font(.caption2)
        }
        .foregroundStyle(item.isStale ? GarageColors.statusWarning : Color.secondary)
        .padding(.horizontal, 8)
        .padding(.vertical, 3)
        .background(
            Capsule().fill(item.isStale
                ? GarageColors.statusWarning.opacity(0.12)
                : Color(uiColor: .tertiarySystemFill))
        )
        .accessibilityElement(children: .combine)
        .accessibilityLabel(item.isStale ? "Device offline" : "Device online")
    }
}

/// Remote-button health pill in the "Remote control" section header — the SwiftUI
/// analog of Android's `RemoteButtonHealthPill`. Label + an availability icon;
/// only `.offline` uses the warning tint (matching Android's "offline screams,
/// the rest whisper" hierarchy). Uses the same `wifi` icon family as
/// `DeviceCheckInPill` for a consistent device-availability grammar.
private struct RemoteButtonHealthPill: View {
    let item: ButtonHealthItem

    var body: some View {
        HStack(spacing: GarageSpacing.tight) {
            Text(item.label)
                .font(.caption2.weight(.medium))
                .textCase(nil)
            Image(systemName: icon)
                .font(.caption2)
        }
        .foregroundStyle(isOffline ? GarageColors.statusWarning : Color.secondary)
        .padding(.horizontal, 8)
        .padding(.vertical, 3)
        .background(
            Capsule().fill(isOffline
                ? GarageColors.statusWarning.opacity(0.12)
                : Color(uiColor: .tertiarySystemFill))
        )
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Remote button \(item.label)")
    }

    private var isOffline: Bool {
        if case .offline = item.kind { return true }
        return false
    }

    private var icon: String {
        switch item.kind {
        case .unauthorized: return "lock"
        case .loading: return "arrow.triangle.2.circlepath"
        case .unknown: return "questionmark.circle"
        case .online: return "wifi"
        case .offline: return "wifi.slash"
        }
    }
}

/// Warning chip for stuck / anomalous door states — the SwiftUI analog of
/// Android's errorContainer warning Surface in `HomeContent`. Renders the
/// already-localized `text` (resolved from the shared typed `DoorWarning`).
private struct DoorWarningChip: View {
    let text: String

    var body: some View {
        Label(text, systemImage: "exclamationmark.triangle.fill")
            .font(.footnote)
            .multilineTextAlignment(.leading)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(GarageColors.statusWarning.opacity(0.12), in: RoundedRectangle(cornerRadius: 10))
            .foregroundStyle(GarageColors.statusWarning)
    }
}

/// Identifies which per-pill info sheet is open on the Home tab — the SwiftUI
/// analog of Android's private `HomeInfoSheet` enum in `HomeContent.kt`. The
/// explanatory copy mirrors Android's `InfoBottomSheet.kt` strings (short,
/// reviewed with the user). iOS sources its own copy inline, matching how the
/// rest of the iOS app handles user-facing strings.
enum HomeInfoSheet: String, Identifiable {
    case doorStatus
    case remoteControl

    var id: String { rawValue }

    var title: String {
        switch self {
        case .doorStatus: return "Door status"
        case .remoteControl: return "Remote control"
        }
    }

    var paragraphs: [String] {
        switch self {
        case .doorStatus:
            return [
                "The door sensor checks in every 10 minutes, or whenever the door moves.",
                "If we don't hear from it on schedule, this shows \"no signal\" so you know the sensor may be offline.",
            ]
        case .remoteControl:
            return [
                "The remote button checks in frequently. \"Available\" means it just told us it's ready to open or close the door.",
                "If contact stops, this shows when we last heard from it. Tapping the button may not work until it reconnects.",
            ]
        }
    }
}

/// Pure info-sheet content — the SwiftUI analog of Android's `InfoSheetLayout`
/// in `InfoBottomSheet.kt`. Centered info icon + title + left-aligned
/// paragraphs in a scrollable column (a `ModalBottomSheet` provides no scroll
/// of its own; tall content on a short viewport would otherwise clip). Pure
/// values so it renders in the snapshot gallery without a live component.
struct HomeInfoSheetContentView: View {
    let title: String
    let paragraphs: [String]

    var body: some View {
        ScrollView {
            VStack(spacing: GarageSpacing.card) {
                Image(systemName: "info.circle")
                    .font(.system(size: 48))
                    .foregroundStyle(.tint)
                Text(title)
                    .font(.title2)
                    .multilineTextAlignment(.center)
                ForEach(Array(paragraphs.enumerated()), id: \.offset) { _, paragraph in
                    Text(paragraph)
                        .font(.body)
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .padding(GarageSpacing.card)
            .frame(maxWidth: .infinity)
        }
    }
}

/// Thin wrapper that maps the typed `HomeInfoSheet` to its content view, used
/// as the `.sheet(item:)` builder on the Home content.
private struct HomeInfoSheetView: View {
    let sheet: HomeInfoSheet

    var body: some View {
        HomeInfoSheetContentView(title: sheet.title, paragraphs: sheet.paragraphs)
    }
}

#Preview("Home closed signed out") {
    NavigationStack {
        HomeContentView(
            doorPosition: .closed,
            lastChangeTimeSeconds: nil,
            sinceLine: "Since 11:22 AM · 38 min",
            warningText: nil,
            isCheckInStale: true,
            buttonItem: RemoteButtonItem(kind: .ready, title: "Tap to open or close", subtitle: nil),
            buttonHealth: ButtonHealthItem(label: "Unauthorized", kind: .unauthorized),
            signedIn: false,
            alerts: [],
            checkIn: DeviceCheckInItem(label: "23 min ago", isStale: true),
            onButtonTap: {},
            onSignIn: {},
            onRefresh: {},
            onAlertAction: { _ in }
        )
    }
}

#Preview("Home open signed in") {
    NavigationStack {
        HomeContentView(
            doorPosition: .open,
            lastChangeTimeSeconds: nil,
            sinceLine: "Since 9:47 AM · 2 hr 14 min",
            warningText: nil,
            isCheckInStale: false,
            buttonItem: RemoteButtonItem(kind: .ready, title: "Tap to open or close", subtitle: nil),
            buttonHealth: ButtonHealthItem(label: "Available", kind: .online),
            signedIn: true,
            alerts: [],
            checkIn: DeviceCheckInItem(label: "1 min ago", isStale: false),
            onButtonTap: {},
            onSignIn: {},
            onRefresh: {},
            onAlertAction: { _ in }
        )
    }
}

#Preview("Home opening too long warning") {
    NavigationStack {
        HomeContentView(
            doorPosition: .openingTooLong,
            lastChangeTimeSeconds: nil,
            sinceLine: "Since 12:01 PM · 4 min",
            warningText: "Opening, taking longer than expected",
            isCheckInStale: false,
            buttonItem: RemoteButtonItem(kind: .ready, title: "Tap to open or close", subtitle: nil),
            buttonHealth: ButtonHealthItem(label: "Available", kind: .online),
            signedIn: true,
            alerts: [],
            checkIn: DeviceCheckInItem(label: "1 min ago", isStale: false),
            onButtonTap: {},
            onSignIn: {},
            onRefresh: {},
            onAlertAction: { _ in }
        )
    }
}

#Preview("Home with alerts") {
    NavigationStack {
        HomeContentView(
            doorPosition: .unknown,
            lastChangeTimeSeconds: nil,
            sinceLine: "Since 8:15 AM · 1 hr 5 min",
            warningText: nil,
            isCheckInStale: true,
            buttonItem: RemoteButtonItem(kind: .ready, title: "Tap to open or close", subtitle: nil),
            buttonHealth: ButtonHealthItem(label: "Unavailable · 11 min ago", kind: .offline),
            signedIn: true,
            alerts: [
                HomeAlertItem(
                    id: "stale",
                    kind: .stale,
                    message: "Not receiving updates from server",
                    actionLabel: "Retry"
                ),
                HomeAlertItem(
                    id: "permission",
                    kind: .permission,
                    message: "Turn on notifications to get alerted when the door is left open.",
                    actionLabel: "Allow"
                ),
            ],
            checkIn: DeviceCheckInItem(label: "23 min ago", isStale: true),
            onButtonTap: {},
            onSignIn: {},
            onRefresh: {},
            onAlertAction: { _ in }
        )
    }
}

#Preview("Info door status") {
    HomeInfoSheetContentView(
        title: "Door status",
        paragraphs: [
            "The door sensor checks in every 10 minutes, or whenever the door moves.",
            "If we don't hear from it on schedule, this shows \"no signal\" so you know the sensor may be offline.",
        ]
    )
}

#Preview("Info remote control") {
    HomeInfoSheetContentView(
        title: "Remote control",
        paragraphs: [
            "The remote button checks in frequently. \"Available\" means it just told us it's ready to open or close the door.",
            "If contact stops, this shows when we last heard from it. Tapping the button may not work until it reconnects.",
        ]
    )
}

#Preview("Remote button states") {
    List {
        Section("Ready") {
            RemoteButtonView(
                item: RemoteButtonItem(kind: .ready, title: "Tap to open or close", subtitle: nil),
                onTap: {}
            )
        }
        Section("Confirm") {
            RemoteButtonView(
                item: RemoteButtonItem(kind: .confirm, title: "Door will move.", subtitle: "Tap again to confirm"),
                onTap: {}
            )
        }
        Section("Sending") {
            RemoteButtonView(
                item: RemoteButtonItem(kind: .busy, title: "Sending…", subtitle: nil, phase: .sendingToServer),
                onTap: {}
            )
        }
        Section("Waiting for door") {
            RemoteButtonView(
                item: RemoteButtonItem(kind: .busy, title: "Waiting for door…", subtitle: nil, phase: .sendingToDoor),
                onTap: {}
            )
        }
        Section("Succeeded") {
            RemoteButtonView(
                item: RemoteButtonItem(kind: .succeeded, title: "Done", subtitle: nil, phase: .succeeded),
                onTap: {}
            )
        }
        Section("Door failed") {
            RemoteButtonView(
                item: RemoteButtonItem(kind: .failed, title: "Door did not move", subtitle: nil, phase: .doorFailed),
                onTap: {}
            )
        }
    }
}

#Preview("Home sign-in row") {
    List {
        Section("Sign in") {
            HomeSignInRow(onSignIn: {})
        }
    }
}

#Preview("Home confirm state") {
    NavigationStack {
        HomeContentView(
            doorPosition: .closed,
            lastChangeTimeSeconds: nil,
            sinceLine: "Since 11:22 AM · 38 min",
            warningText: nil,
            isCheckInStale: false,
            buttonItem: RemoteButtonItem(kind: .confirm, title: "Door will move.", subtitle: "Tap again to confirm"),
            buttonHealth: ButtonHealthItem(label: "Available", kind: .online),
            signedIn: true,
            alerts: [],
            checkIn: DeviceCheckInItem(label: "1 min ago", isStale: false),
            onButtonTap: {},
            onSignIn: {},
            onRefresh: {},
            onAlertAction: { _ in }
        )
    }
}
