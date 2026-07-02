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

import CoreGraphics

/// Spacing tokens mirroring Android's `ui/theme/Spacing.kt`. Names describe
/// role, not value — reach for a token before a raw literal.
enum GarageSpacing {
    /// Horizontal padding between screen content and the device edges.
    static let screen: CGFloat = 16
    /// Spacing between items in a screen-level list.
    static let betweenItems: CGFloat = 16
    /// Vertical breathing room above the first / below the last list item.
    static let listVertical: CGFloat = 16
    /// Tight grouping inside a single visual unit (icon ↔ text, label ↔ control).
    static let tight: CGFloat = 4
    /// Padding inside a card.
    static let card: CGFloat = 16
    /// Max readable content width on wide screens.
    static let contentWidth: CGFloat = 640
}
