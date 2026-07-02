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

import FirebaseAuth
@preconcurrency import shared

/// iOS implementation of the Kotlin `AuthBridge` (mirrors Android's
/// `FirebaseAuthBridge`). Wraps the Firebase Auth iOS SDK so the shared
/// `FirebaseAuthRepository` never imports Firebase types.
///
/// SKIE conformance contract (verified against the generated framework):
///   - suspend funcs are implemented as `__`-prefixed `async throws` methods,
///   - `observeAuthUser()` returns `SkieSwiftOptionalFlow<DataAuthUserInfo>`,
///     produced by bridging `IosAuthUserStateHolder.asFlow()` (Swift cannot
///     construct a Kotlin Flow directly — see that holder's KDoc).
///
/// The auth-state listener is registered once in `init` and pushes into the
/// holder; this single shared StateFlow replaces Android's per-collector
/// `callbackFlow` (ADR-018) with equivalent observable semantics.
final class FirebaseAuthBridge: DataAuthBridge {
    private let holder = IosAuthUserStateHolder()
    private var listenerHandle: AuthStateDidChangeListenerHandle?

    init() {
        listenerHandle = Auth.auth().addStateDidChangeListener { [holder] _, user in
            holder.update(user: user.map {
                DataAuthUserInfo(displayName: $0.displayName ?? "", email: $0.email ?? "")
            })
        }
    }

    deinit {
        if let handle = listenerHandle {
            Auth.auth().removeStateDidChangeListener(handle)
        }
    }

    func getCurrentUser() -> DataAuthUserInfo? {
        guard let user = Auth.auth().currentUser else { return nil }
        return DataAuthUserInfo(displayName: user.displayName ?? "", email: user.email ?? "")
    }

    func observeAuthUser() -> SkieSwiftOptionalFlow<DataAuthUserInfo> {
        holder.asFlow()
    }

    func __getIdToken(forceRefresh: Bool) async throws -> FirebaseIdToken? {
        guard let user = Auth.auth().currentUser else { return nil }
        let result = try await user.getIDTokenResult(forcingRefresh: forceRefresh)
        return FirebaseIdToken(
            idToken: result.token,
            // Match Android (`GetTokenResult.getExpirationTimestamp()` = seconds).
            exp: Int64(result.expirationDate.timeIntervalSince1970)
        )
    }

    func __signInWithGoogleToken(idToken: Any) async throws -> KotlinBoolean {
        // The Kotlin `GoogleIdToken` value class is erased to its wrapped String
        // at the ObjC boundary, so `idToken` arrives as an NSString.
        guard let token = idToken as? String else { return KotlinBoolean(bool: false) }
        let credential = GoogleAuthProvider.credential(withIDToken: token, accessToken: "")
        do {
            _ = try await Auth.auth().signIn(with: credential)
            return KotlinBoolean(bool: true)
        } catch {
            return KotlinBoolean(bool: false)
        }
    }

    func __signOut() async throws {
        try? Auth.auth().signOut()
    }
}
