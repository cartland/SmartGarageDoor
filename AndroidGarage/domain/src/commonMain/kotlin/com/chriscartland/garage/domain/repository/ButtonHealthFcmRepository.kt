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

package com.chriscartland.garage.domain.repository

/**
 * Manages FCM topic subscription for the remote-button health channel.
 *
 * Separate from [DoorFcmRepository] (different feature, different
 * lifecycle, different topic prefix). The button-health subscription
 * is gated by the same allowlist that gates the button feature itself
 * — see [ButtonHealthFcmSubscriptionManager] (PR 7).
 *
 * Implementations track the most-recently-subscribed topic in memory
 * so [unsubscribeAll] can clean up without persisted state. App-restart
 * recovery is handled by re-subscribing on next manager start
 * (Firebase tolerates idempotent subscribe).
 */
interface ButtonHealthFcmRepository {
    /** Subscribe to the topic derived from [buildTimestamp]. Idempotent. */
    suspend fun subscribe(buildTimestamp: String)

    /** Unsubscribe from any topic this repository previously subscribed to. */
    suspend fun unsubscribeAll()
}
