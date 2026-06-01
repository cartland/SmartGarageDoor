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

import Foundation

/// The five top-level tabs, mirroring the Android nav chrome
/// (`AppLayoutMode.visibleTabs`): Home / History / Profile / Functions /
/// Diagnostics.
enum MainTab: String, CaseIterable, Identifiable {
    case home
    case history
    case profile
    case functions
    case diagnostics

    var id: String { rawValue }

    var title: String {
        switch self {
        case .home: return "Home"
        case .history: return "History"
        case .profile: return "Profile"
        case .functions: return "Functions"
        case .diagnostics: return "Diagnostics"
        }
    }

    /// SF Symbol for the tab item.
    var systemImage: String {
        switch self {
        case .home: return "house"
        case .history: return "clock.arrow.circlepath"
        case .profile: return "person.crop.circle"
        case .functions: return "square.grid.2x2"
        case .diagnostics: return "stethoscope"
        }
    }
}
