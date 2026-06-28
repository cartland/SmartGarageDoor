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
            sinceLine: wrapper.sinceLine,
            warningText: wrapper.warningText,
            isCheckInStale: wrapper.isCheckInStale,
            buttonStateLabel: wrapper.buttonStateLabel,
            buttonHealthLabel: wrapper.buttonHealthLabel,
            signedIn: wrapper.signedIn,
            alerts: wrapper.alerts,
            checkIn: wrapper.checkIn,
            onButtonTap: { wrapper.onButtonTap() },
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
    /// Pre-formatted "Since 9:47 AM · 2 hr 14 min" line (resolved from the shared
    /// typed `SinceStatus` in the wrapper); `nil` when the last-change time is
    /// unknown. Mirrors Android's status line; replaces the old raw door message.
    let sinceLine: String?
    /// Already-localized warning text (resolved from the shared typed
    /// `DoorWarning` in the wrapper). Non-nil only for stuck/anomalous states.
    let warningText: String?
    let isCheckInStale: Bool
    let buttonStateLabel: String
    let buttonHealthLabel: String
    let signedIn: Bool
    /// Resolved alert banners (ADR-031 Phase 4) shown above the Status card.
    /// Empty in the steady state; the shared `HomeAlertMapper` decides when a
    /// stale / permission / fetch-error banner applies.
    let alerts: [HomeAlertItem]
    /// Resolved device check-in pill (ADR-031 Phase 5) shown in the Status
    /// section header. `label == nil` renders icon-only (no heartbeat yet).
    let checkIn: DeviceCheckInItem
    let onButtonTap: () -> Void
    let onRefresh: () -> Void
    let onAlertAction: (HomeAlertItem.Kind) -> Void

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
                    GarageDoorView(position: doorPosition, isStale: isCheckInStale)
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
                }
            }

            Section("Remote button") {
                Button(action: onButtonTap) {
                    Text(buttonStateLabel)
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .listRowInsets(EdgeInsets())
                .padding(GarageSpacing.tight)
            }

            Section("Device health") {
                Text(buttonHealthLabel).foregroundStyle(.secondary)
            }

            Section("Account") {
                Text(signedIn ? "Signed in" : "Signed out")
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Garage")
        .refreshable { onRefresh() }
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

/// Compact device-heartbeat pill in the Home "Status" section header — the
/// SwiftUI analog of Android's `DeviceCheckInPill`. Antenna icon + "… ago" text
/// when fresh; slashed antenna + warning tint when stale (>11 min). Text is
/// hidden (icon only) until the first heartbeat is observed (`label == nil`).
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
                ? "antenna.radiowaves.left.and.right.slash"
                : "antenna.radiowaves.left.and.right")
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

#Preview("Home closed signed out") {
    NavigationStack {
        HomeContentView(
            doorPosition: .closed,
            sinceLine: "Since 11:22 AM · 38 min",
            warningText: nil,
            isCheckInStale: true,
            buttonStateLabel: "Tap to open / close",
            buttonHealthLabel: "Sign in to see device health",
            signedIn: false,
            alerts: [],
            checkIn: DeviceCheckInItem(label: "23 min ago", isStale: true),
            onButtonTap: {},
            onRefresh: {},
            onAlertAction: { _ in }
        )
    }
}

#Preview("Home open signed in") {
    NavigationStack {
        HomeContentView(
            doorPosition: .open,
            sinceLine: "Since 9:47 AM · 2 hr 14 min",
            warningText: nil,
            isCheckInStale: false,
            buttonStateLabel: "Tap to open / close",
            buttonHealthLabel: "Online",
            signedIn: true,
            alerts: [],
            checkIn: DeviceCheckInItem(label: "1 min ago", isStale: false),
            onButtonTap: {},
            onRefresh: {},
            onAlertAction: { _ in }
        )
    }
}

#Preview("Home opening too long warning") {
    NavigationStack {
        HomeContentView(
            doorPosition: .openingTooLong,
            sinceLine: "Since 12:01 PM · 4 min",
            warningText: "Opening, taking longer than expected",
            isCheckInStale: false,
            buttonStateLabel: "Tap to open / close",
            buttonHealthLabel: "Online",
            signedIn: true,
            alerts: [],
            checkIn: DeviceCheckInItem(label: "1 min ago", isStale: false),
            onButtonTap: {},
            onRefresh: {},
            onAlertAction: { _ in }
        )
    }
}

#Preview("Home with alerts") {
    NavigationStack {
        HomeContentView(
            doorPosition: .unknown,
            sinceLine: "Since 8:15 AM · 1 hr 5 min",
            warningText: nil,
            isCheckInStale: true,
            buttonStateLabel: "Tap to open / close",
            buttonHealthLabel: "Online",
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
            onRefresh: {},
            onAlertAction: { _ in }
        )
    }
}
