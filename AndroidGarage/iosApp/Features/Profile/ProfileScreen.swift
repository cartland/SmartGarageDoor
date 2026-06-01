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

/// Profile tab — account + snooze. Mirrors Android's `ProfileContent`.
/// Google Sign-In is wired once the Firebase iOS SDK lands (Phase C); for now
/// the account row reflects the (NoOp) auth state.
struct ProfileScreen: View {
    @StateObject private var wrapper: ProfileViewModelWrapper

    init(component: NativeComponent) {
        _wrapper = StateObject(wrappedValue: ProfileViewModelWrapper(component: component))
    }

    var body: some View {
        List {
            Section("Account") {
                Text(wrapper.signedIn ? "Signed in" : "Signed out")
                    .foregroundStyle(.secondary)
                if wrapper.signedIn {
                    Button("Sign out", role: .destructive) { wrapper.signOut() }
                }
            }

            Section("Snooze notifications") {
                HStack {
                    Text(wrapper.snoozeLabel)
                    Spacer()
                    if wrapper.snoozeSending { ProgressView().controlSize(.small) }
                }
                if let error = wrapper.snoozeError {
                    Text(error).font(.footnote).foregroundStyle(GarageColors.statusWarning)
                }
                ForEach(wrapper.durations, id: \.label) { entry in
                    Button(entry.label) { wrapper.snooze(entry.option) }
                        .disabled(wrapper.snoozeSending)
                }
            }
        }
        .navigationTitle("Profile")
        .refreshable { wrapper.refreshSnooze() }
    }
}
