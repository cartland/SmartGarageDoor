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

/// The three top-level tabs, mirroring Android's nav chrome: Home / History /
/// Settings. Diagnostics and Functions are NOT top-level tabs — they live in a
/// gated Developer section under Settings (parity with Android, where they're
/// `developerAccess`-gated rows, not tabs).
enum MainTab: String, CaseIterable, Identifiable {
    case home
    case history
    case settings

    var id: String { rawValue }

    var title: String {
        switch self {
        case .home: return "Home"
        case .history: return "History"
        case .settings: return "Settings"
        }
    }

    /// SF Symbol for the tab item.
    var systemImage: String {
        switch self {
        case .home: return "house"
        case .history: return "clock.arrow.circlepath"
        case .settings: return "gearshape"
        }
    }
}
