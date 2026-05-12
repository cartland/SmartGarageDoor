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

package com.chriscartland.garage.iosframework

import com.chriscartland.garage.data.MessagingBridge

/**
 * NoOp `MessagingBridge` for the iOS framework. No subscriptions,
 * no tokens. Placeholder pending the Swift implementation
 * (`FirebaseMessagingBridge.swift`), which will use FCM iOS SDK
 * + APNs registration via the iOS AppDelegate.
 */
object NoOpMessagingBridge : MessagingBridge {
    override suspend fun subscribeToTopic(topic: String): Boolean = false

    override suspend fun unsubscribeFromTopic(topic: String) = Unit

    override suspend fun getToken(): String? = null
}
