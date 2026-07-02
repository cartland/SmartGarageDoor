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

/// Shared fixtures for SwiftUI `#Preview`s / the snapshot gallery.
///
/// `internal` (not `private`) on purpose: a `#Preview` body is embedded verbatim
/// into the generated `PreviewTests` (test target, `@testable import GarageControl`), so
/// anything it references must be visible at that access level.
enum PreviewFixtures {
    /// A fixed "now" injected into time-dependent previews so their snapshots are
    /// deterministic (a live `Date()` would make e.g. relative timestamps churn on
    /// every regen). Production paths use the real clock; only previews pass this.
    /// Arbitrary fixed instant: 2023-11-14 22:13:20 UTC.
    static let now = Date(timeIntervalSince1970: 1_700_000_000)
}
