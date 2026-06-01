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

/// Semantic colors for the app. Light/dark follow the system appearance.
///
/// Baseline mirrors the Android door-status palette at a high level; the full
/// per-state `DoorStatusColorScheme.kt` translation lands with the Home screen
/// (the only screen that renders the colored door-status surfaces).
enum GarageColors {
    /// Surface that reads "everything is fine" (door closed).
    static let statusOk = Color.green
    /// Surface that reads "door is open / in motion".
    static let statusOpen = Color.orange
    /// Surface that reads "warning / unknown / error".
    static let statusWarning = Color.red
    /// Neutral container background for cards.
    static let cardBackground = Color(uiColor: .secondarySystemBackground)
}
