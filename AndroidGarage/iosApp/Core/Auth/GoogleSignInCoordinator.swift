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

import GoogleSignIn
import UIKit

/// Presents the Google Sign-In UI and returns the Google ID token, mirroring
/// Android's `GoogleSignInState`: the platform UI obtains the token, then hands
/// it to the shared `ProfileViewModel.signInWithGoogle(idToken:)`, which drives
/// the shared `SignInWithGoogleUseCase` -> `AuthBridge.signInWithGoogleToken`.
///
/// The `GIDConfiguration` (client ID) is set once at launch in `AppDelegate`
/// from the Firebase app options, so no per-call configuration is needed here.
@MainActor
enum GoogleSignInCoordinator {
    /// Presents the sign-in sheet and returns the Google ID token string, or
    /// `nil` if the user cancels or the flow fails.
    static func signIn() async -> String? {
        guard let presenter = topViewController() else { return nil }
        do {
            let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: presenter)
            return result.user.idToken?.tokenString
        } catch {
            return nil
        }
    }

    private static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .first { $0.activationState == .foregroundActive } as? UIWindowScene
        var top = scene?.keyWindow?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }
}
