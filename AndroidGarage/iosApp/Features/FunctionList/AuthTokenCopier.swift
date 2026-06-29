/*
 * Copyright 2026 Chris Cartland. All rights reserved.
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
import UniformTypeIdentifiers
@preconcurrency import shared

/// Outcome of a copy-auth-token attempt, so the UI can flash the right message.
enum AuthTokenCopyOutcome {
    case copied
    case notSignedIn
}

/// Single source of truth for the developer-only "copy auth token" action — the
/// iOS analog of Android's `rememberAuthTokenCopier` + `SensitiveClipboard`.
/// Fetches the current Firebase ID token via the shared `GetAuthTokenForCopyUseCase`;
/// on success writes it to the pasteboard with a short expiration.
///
/// iOS has no `ClipDescription.EXTRA_IS_SENSITIVE` equivalent (and shows no
/// post-copy preview chip), so the **expiration is the sensitivity posture** —
/// the JWT auto-clears from the pasteboard — and the caller flashes a "Copied"
/// confirmation in place of the OS chip Android relies on.
enum AuthTokenCopier {
    /// Pasteboard auto-clear window for the copied JWT.
    private static let expirySeconds: TimeInterval = 120

    @MainActor
    static func copy(using useCase: UsecaseGetAuthTokenForCopyUseCase) async -> AuthTokenCopyOutcome {
        // `invoke()` is `async throws` over the SKIE bridge; any thrown error
        // (or the typed `AppResult.Error` for "not signed in") means no copy.
        do {
            switch onEnum(of: try await useCase.invoke()) {
            case .success(let success):
                UIPasteboard.general.setItems(
                    [[UTType.utf8PlainText.identifier: success.data]],
                    options: [.expirationDate: Date().addingTimeInterval(expirySeconds)]
                )
                return .copied
            case .error:
                return .notSignedIn
            }
        } catch {
            return .notSignedIn
        }
    }
}

/// Self-contained "Copy auth token" button with a transient confirmation flash.
/// The flash `@State` is internal to this view, so it doesn't lower the
/// synthesized memberwise init of the screen content views that embed it (same
/// isolation rationale as `SettingsRow`'s copy flash). Mirrors Android's
/// developer-panel button; the fetch + clipboard write live in [AuthTokenCopier].
struct CopyAuthTokenButton: View {
    /// Performs the copy and returns the outcome (typically `wrapper.copyAuthToken`).
    let copy: () async -> AuthTokenCopyOutcome

    @State private var flash: Flash?

    private struct Flash {
        let text: String
        let systemImage: String
    }

    var body: some View {
        Button {
            Task {
                let outcome = await copy()
                let next: Flash = outcome == .copied
                    ? Flash(text: "Copied", systemImage: "checkmark")
                    : Flash(text: "Sign in to copy auth token", systemImage: "exclamationmark.triangle")
                withAnimation { flash = next }
                try? await Task.sleep(nanoseconds: 1_500_000_000)
                withAnimation { flash = nil }
            }
        } label: {
            if let flash {
                Label(flash.text, systemImage: flash.systemImage)
            } else {
                Text("Copy auth token")
            }
        }
    }
}
