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
                    screen(for: tab)
                }
                .tabItem {
                    Label(tab.title, systemImage: tab.systemImage)
                }
            }
        }
    }

    @ViewBuilder
    private func screen(for tab: MainTab) -> some View {
        switch tab {
        case .home:
            HomeScreen(component: component)
        case .history:
            HistoryScreen(component: component)
        case .profile:
            ProfileScreen(component: component)
        case .functions:
            FunctionListScreen(component: component)
        case .diagnostics:
            DiagnosticsScreen(component: component)
        }
    }
}
