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
import com.chriscartland.garage.data.MessagingBridge
import com.chriscartland.garage.domain.model.DOOR_RESOLVED_FCM_TOPIC_PREFIX
import com.chriscartland.garage.domain.model.toDoorResolvedFcmTopic
import com.chriscartland.garage.domain.repository.DoorResolvedFcmRepository

/**
 * FCM topic subscription for the additive resolved-on-close door notification
 * (`door_open_v2-*`). Mirrors [FirebaseButtonHealthFcmRepository] (in-memory
 * last-subscribed topic, idempotent subscribe, restart-recovery on next start).
 *
 * Safety: a [requireOwnTopic] guard refuses to pass any non-`door_open_v2-`
 * string to the messaging bridge, so a corrupted input can never unsubscribe
 * the production `door_open-` / `buttonHealth-` / `testNotification-` topics.
 * Completely separate from [FirebaseDoorFcmRepository] — never touches the
 * legacy door subscription.
 */
class FirebaseDoorResolvedFcmRepository(
    private val messagingBridge: MessagingBridge,
) : DoorResolvedFcmRepository {
    private var lastSubscribedTopic: String? = null

    override suspend fun subscribe(buildTimestamp: String) {
        val topic = buildTimestamp.toDoorResolvedFcmTopic()
        if (topic == lastSubscribedTopic) {
            Logger.d { "Already subscribed to $topic; skipping" }
            return
        }
        // Unsubscribe from prior topic if any (e.g., buildTimestamp rotation).
        lastSubscribedTopic?.let {
            requireOwnTopic(it)
            Logger.i { "Unsubscribing from prior door-resolved topic: $it" }
            messagingBridge.unsubscribeFromTopic(it)
        }
        requireOwnTopic(topic)
        Logger.i { "Subscribing to door-resolved topic: $topic" }
        val ok = messagingBridge.subscribeToTopic(topic)
        if (ok) {
            lastSubscribedTopic = topic
        } else {
            Logger.e { "Failed to subscribe to door-resolved topic: $topic" }
        }
    }

    override suspend fun unsubscribeAll() {
        val topic = lastSubscribedTopic ?: return
        requireOwnTopic(topic)
        Logger.i { "Unsubscribing from door-resolved topic: $topic" }
        messagingBridge.unsubscribeFromTopic(topic)
        lastSubscribedTopic = null
    }

    private fun requireOwnTopic(topic: String) {
        require(topic.startsWith(DOOR_RESOLVED_FCM_TOPIC_PREFIX)) {
            "DoorResolvedFcmRepository refused to touch non-v2 topic: $topic"
        }
    }
}
