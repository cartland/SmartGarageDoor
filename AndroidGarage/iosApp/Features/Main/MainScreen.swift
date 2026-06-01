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

/// Root tab shell. Holds the DI `component` and routes each tab to its screen.
///
/// **Phase B baseline:** tab bodies are placeholders. Each screen
/// (Home / History / Profile / Functions / Diagnostics) is wired to its real
/// `Default*ViewModel` from `component` in the screens PR; the placeholder
/// names the ViewModel that will back it so the wiring target is explicit.
struct MainScreen: View {
    let component: NativeComponent

    var body: some View {
        TabView {
            ForEach(MainTab.allCases) { tab in
                NavigationStack {
                    TabPlaceholder(tab: tab)
                        .navigationTitle(tab.title)
                }
                .tabItem {
                    Label(tab.title, systemImage: tab.systemImage)
                }
            }
        }
    }
}

/// Temporary per-tab body for the Phase B foundation. Replaced screen-by-screen.
private struct TabPlaceholder: View {
    let tab: MainTab

    var body: some View {
        VStack(spacing: GarageSpacing.betweenItems) {
            Image(systemName: tab.systemImage)
                .font(.system(size: 48))
                .foregroundStyle(.secondary)
            Text(tab.title)
                .font(.title2.weight(.semibold))
            Text("Screen coming soon")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .padding(GarageSpacing.screen)
    }
}
