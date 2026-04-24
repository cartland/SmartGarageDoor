/**
 * Copyright 2026 Chris Cartland. All Rights Reserved.
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
 */

import * as firebase from 'firebase-admin';

/**
 * Swappable wrapper around `firebase.auth().verifyIdToken()`.
 *
 * Shape matches src/database/*Database.ts and src/controller/fcm/EventFCM.ts
 * (interface + default impl + singleton + setImpl/resetImpl). Tests use
 * FakeAuthService to control token verification without touching Firebase
 * Auth's network-calling real implementation.
 *
 * **Intended consumers (handler testing plan — H3 HTTP + H4 write):**
 * - `http/RemoteButton.ts` → `httpAddRemoteButtonCommand`
 * - `http/Snooze.ts`       → `httpSnoozeNotificationsRequest`
 *
 * Those handlers currently call `firebase.auth().verifyIdToken(token)`
 * directly. After the H3/H4 extractions they will call
 * `AuthService.verifyIdToken(token)` instead. The migration is mechanical
 * — behavior is byte-identical; only the seam for tests changes.
 *
 * **verifyIdToken throw behavior differs between callers.** Preserve it
 * when wiring:
 * - `httpSnoozeNotificationsRequest` wraps in try/catch → returns 401 on
 *   throw.
 * - `httpAddRemoteButtonCommand` does NOT wrap → exception propagates to
 *   the outer catch → returns 500.
 * Tests must exercise both paths (fake throws, caller reacts) to pin the
 * asymmetry.
 */
export interface AuthService {
  verifyIdToken(idToken: string): Promise<firebase.auth.DecodedIdToken>;
}

class DefaultAuthService implements AuthService {
  verifyIdToken(idToken: string): Promise<firebase.auth.DecodedIdToken> {
    return firebase.auth().verifyIdToken(idToken);
  }
}

let _instance: AuthService = new DefaultAuthService();

export const SERVICE: AuthService = {
  verifyIdToken: (token) => _instance.verifyIdToken(token),
};

/** TEST-ONLY: swap in a fake implementation. */
export function setImpl(impl: AuthService): void { _instance = impl; }

/** TEST-ONLY: restore the default (Firebase-Auth-calling) implementation. */
export function resetImpl(): void { _instance = new DefaultAuthService(); }
