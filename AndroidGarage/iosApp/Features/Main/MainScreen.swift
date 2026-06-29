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

/// Root tab shell — three tabs (Home / History / Settings). Diagnostics and
/// Functions are reached from the gated Developer section inside Settings, not
/// as tabs (see `SettingsScreen`).
struct MainScreen: View {
    let component: NativeComponent

    /// App-root holder for the shared door-animation replay memory (`:domain`
    /// `DoorAnimationMemory`). `@State` so the single instance survives tab
    /// switches (the shell isn't recreated) but resets on process death —
    /// exactly when a cold open should replay the slide. Mirrors Android's
    /// `remember { DoorAnimationMemory() }` at the Compose root; injected into
    /// the tree via the `\.doorAnimationMemory` environment value.
    @State private var doorAnimationMemory = DoorAnimationMemory()

    var body: some View {
        TabView {
            ForEach(MainTab.allCases) { tab in
                NavigationStack {
                    screen(for: tab)
                }
                .tabItem {
                    Label(tab.title, systemImage: tab.systemImage)
                }
            }
        }
        .environment(\.doorAnimationMemory, doorAnimationMemory)
    }

    @ViewBuilder
    private func screen(for tab: MainTab) -> some View {
        switch tab {
        case .home:
            HomeScreen(component: component)
        case .history:
            HistoryScreen(component: component)
        case .settings:
            SettingsScreen(component: component)
        }
    }
}
