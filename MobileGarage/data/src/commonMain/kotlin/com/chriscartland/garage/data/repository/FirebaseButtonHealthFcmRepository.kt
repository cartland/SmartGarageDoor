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

package com.chriscartland.garage.data.repository

import co.touchlab.kermit.Logger
import com.chriscartland.garage.data.ButtonHealthFcmTopic
import com.chriscartland.garage.data.MessagingBridge
import com.chriscartland.garage.domain.repository.ButtonHealthFcmRepository

/**
 * FCM topic subscription manager for the button-health channel.
 *
 * Tracks the most-recently-subscribed topic in memory so [unsubscribeAll]
 * can clean up without persisted state. App-restart recovery is handled
 * by re-subscribing on next manager start (Firebase tolerates idempotent
 * subscribe).
 *
 * Separate from [FirebaseDoorFcmRepository] (different feature, different
 * lifecycle, different topic prefix).
 */
class FirebaseButtonHealthFcmRepository(
    private val messagingBridge: MessagingBridge,
) : ButtonHealthFcmRepository {
    private var lastSubscribedTopic: String? = null

    override suspend fun subscribe(buildTimestamp: String) {
        val topic = ButtonHealthFcmTopic.fromBuildTimestamp(buildTimestamp)
        if (topic == lastSubscribedTopic) {
            Logger.d { "Already subscribed to $topic; skipping" }
            return
        }
        // Unsubscribe from prior topic if any (e.g., buildTimestamp rotation).
        lastSubscribedTopic?.let {
            Logger.i { "Unsubscribing from prior button-health topic: $it" }
            messagingBridge.unsubscribeFromTopic(it)
        }
        Logger.i { "Subscribing to button-health topic: $topic" }
        val ok = messagingBridge.subscribeToTopic(topic)
        if (ok) {
            lastSubscribedTopic = topic
        } else {
            Logger.e { "Failed to subscribe to button-health topic: $topic" }
        }
    }

    override suspend fun unsubscribeAll() {
        val topic = lastSubscribedTopic ?: return
        Logger.i { "Unsubscribing from button-health topic: $topic" }
        messagingBridge.unsubscribeFromTopic(topic)
        lastSubscribedTopic = null
    }
}
