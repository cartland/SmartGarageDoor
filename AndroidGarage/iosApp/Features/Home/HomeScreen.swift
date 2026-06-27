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
            doorMessage: wrapper.doorMessage,
            isCheckInStale: wrapper.isCheckInStale,
            buttonStateLabel: wrapper.buttonStateLabel,
            buttonHealthLabel: wrapper.buttonHealthLabel,
            signedIn: wrapper.signedIn,
            onButtonTap: { wrapper.onButtonTap() },
            onRefresh: { wrapper.refresh() }
        )
    }
}

/// Pure Home content — takes plain values + actions so it renders without a live
/// `NativeComponent` (mirrors Android's `HomeContent(state)`). This is what the
/// `#Preview`s and snapshot gallery capture.
struct HomeContentView: View {
    let doorPosition: DoorPosition
    let doorMessage: String?
    let isCheckInStale: Bool
    let buttonStateLabel: String
    let buttonHealthLabel: String
    let signedIn: Bool
    let onButtonTap: () -> Void
    let onRefresh: () -> Void

    var body: some View {
        List {
            Section("Status") {
                VStack(spacing: GarageSpacing.card) {
                    GarageDoorView(position: doorPosition, isStale: isCheckInStale)
                        .frame(height: 160)
                        .frame(maxWidth: .infinity)
                    VStack(spacing: GarageSpacing.tight) {
                        Text(doorPosition.statusLabel)
                            .font(.title2.weight(.semibold))
                        if let message = doorMessage {
                            Text(message)
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                                .multilineTextAlignment(.center)
                        }
                        if isCheckInStale {
                            Label("Check-in is stale", systemImage: "exclamationmark.triangle")
                                .font(.footnote)
                                .foregroundStyle(GarageColors.statusWarning)
                        }
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, GarageSpacing.tight)
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

#Preview("Home closed signed out") {
    NavigationStack {
        HomeContentView(
            doorPosition: .closed,
            doorMessage: "The door is closed.",
            isCheckInStale: true,
            buttonStateLabel: "Tap to open / close",
            buttonHealthLabel: "Sign in to see device health",
            signedIn: false,
            onButtonTap: {},
            onRefresh: {}
        )
    }
}

#Preview("Home open signed in") {
    NavigationStack {
        HomeContentView(
            doorPosition: .open,
            doorMessage: "The door is open.",
            isCheckInStale: false,
            buttonStateLabel: "Tap to open / close",
            buttonHealthLabel: "Online",
            signedIn: true,
            onButtonTap: {},
            onRefresh: {}
        )
    }
}
