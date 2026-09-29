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
import UIKit

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
    /// The alarm tint: the THEME's error red — the same `#BA1A1A` / `#FFB4AB`
    /// Android's Material scheme paints its banners, pills and alarm chips with,
    /// so both phones raise an alarm in one colour. Until strategy 1.6 this was
    /// system `.red` (#FF3B30), a third red beside the brand door red on the
    /// same screen. Dynamic: follows the interface style. (The door's OWN red
    /// stays the door's — its dark-mode fill is too dark to serve as text.)
    static let statusWarning = dynamicRGB(light: 0xBA1A1A, dark: 0xFFB4AB)
    /// Advisory tone for informational annotations — a history row noting that a
    /// past event took longer than usual, not a fault needing attention. Red is
    /// reserved for things the user should act on; Android draws the same tags
    /// in its `caution` colour for exactly this distinction — and this IS that
    /// colour (`#7A5200` light, `#E0A33A` dark, both WCAG AA on their grounds),
    /// so the two phones draw one advisory amber rather than blue on one and
    /// orange on the other (audit finding 3.1, strategy 1.5/1.6).
    static let statusCaution = dynamicRGB(light: 0x7A5200, dark: 0xE0A33A)

    /// A colour that follows the interface style, from two packed RGB ints.
    private static func dynamicRGB(light: Int, dark: Int) -> Color {
        Color(uiColor: UIColor { traits in
            let rgb = traits.userInterfaceStyle == .dark ? dark : light
            return UIColor(
                red: CGFloat((rgb >> 16) & 0xFF) / 255,
                green: CGFloat((rgb >> 8) & 0xFF) / 255,
                blue: CGFloat(rgb & 0xFF) / 255,
                alpha: 1
            )
        })
    }
    /// Neutral container background for cards.
    static let cardBackground = Color(uiColor: .secondarySystemBackground)
}
