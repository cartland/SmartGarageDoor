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

@preconcurrency import shared

/// What the UI should say about sign-in right now.
///
/// The shared `AuthState` has three arms and the third one matters: `Unknown`
/// means Firebase has not answered yet, which is not the same claim as
/// "signed out". Collapsing it to a Bool renders the sign-in call to action at
/// every launch — including to users who are already signed in — until the
/// listener resolves. Android models this explicitly (`AccountRowState.Checking`
/// / `HomeAuthLoadingBody`, added for exactly that flash); this is the iOS
/// counterpart, shared by Home and Settings so the two screens cannot disagree
/// about what "not signed in" means.
enum AuthDisplayState {
    /// Firebase has not resolved the session yet — say nothing committal.
    case checking
    case signedIn
    case signedOut

    /// Maps the shared sealed `AuthState`. Written as an exhaustive switch so a
    /// fourth arm becomes a compile error rather than a silent "signed out".
    static func from(_ state: AuthState) -> AuthDisplayState {
        switch onEnum(of: state) {
        case .authenticated: return .signedIn
        case .unauthenticated: return .signedOut
        case .unknown: return .checking
        }
    }

    var isSignedIn: Bool { self == .signedIn }
}
