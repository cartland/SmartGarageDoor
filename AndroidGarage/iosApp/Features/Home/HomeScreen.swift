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
/// Functional baseline; the animated door canvas is a later polish pass.
struct HomeScreen: View {
    @StateObject private var wrapper: HomeViewModelWrapper

    init(component: NativeComponent) {
        _wrapper = StateObject(wrappedValue: HomeViewModelWrapper(component: component))
    }

    var body: some View {
        List {
            Section("Status") {
                VStack(spacing: GarageSpacing.card) {
                    GarageDoorView(position: wrapper.doorPosition, isStale: wrapper.isCheckInStale)
                        .frame(height: 160)
                        .frame(maxWidth: .infinity)
                    VStack(spacing: GarageSpacing.tight) {
                        Text(wrapper.doorPosition.statusLabel)
                            .font(.title2.weight(.semibold))
                        if let message = wrapper.doorMessage {
                            Text(message)
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                                .multilineTextAlignment(.center)
                        }
                        if wrapper.isCheckInStale {
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
                Button(action: { wrapper.onButtonTap() }) {
                    Text(wrapper.buttonStateLabel)
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .listRowInsets(EdgeInsets())
                .padding(GarageSpacing.tight)
            }

            Section("Device health") {
                Text(wrapper.buttonHealthLabel).foregroundStyle(.secondary)
            }

            Section("Account") {
                Text(wrapper.signedIn ? "Signed in" : "Signed out")
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Garage")
        .refreshable { wrapper.refresh() }
    }
}
