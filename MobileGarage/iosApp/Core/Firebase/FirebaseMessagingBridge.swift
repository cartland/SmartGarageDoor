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

import FirebaseMessaging
@preconcurrency import shared

/// iOS implementation of the Kotlin `MessagingBridge` (mirrors Android's
/// `FirebaseMessagingBridge`). Wraps the Firebase Messaging iOS SDK.
///
/// Token/subscription delivery requires an APNs key uploaded to Firebase Cloud
/// Messaging; until that lands these calls return/throw inertly on the
/// simulator, but the wiring compiles and the shared `FcmRegistrationManager`
/// drives them unchanged.
final class FirebaseMessagingBridge: DataMessagingBridge {
    func __getToken() async throws -> String? {
        try await Messaging.messaging().token()
    }

    func __subscribeToTopic(topic: String) async throws -> KotlinBoolean {
        do {
            try await Messaging.messaging().subscribe(toTopic: topic)
            return KotlinBoolean(bool: true)
        } catch {
            return KotlinBoolean(bool: false)
        }
    }

    func __unsubscribeFromTopic(topic: String) async throws {
        try await Messaging.messaging().unsubscribe(fromTopic: topic)
    }
}
