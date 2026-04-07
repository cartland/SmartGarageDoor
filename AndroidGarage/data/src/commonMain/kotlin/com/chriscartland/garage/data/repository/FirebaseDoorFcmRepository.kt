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

package com.chriscartland.garage.data.repository

import co.touchlab.kermit.Logger
import com.chriscartland.garage.data.MessagingBridge
import com.chriscartland.garage.domain.model.AppLoggerKeys
import com.chriscartland.garage.domain.model.DoorFcmState
import com.chriscartland.garage.domain.model.DoorFcmTopic
import com.chriscartland.garage.domain.repository.AppLoggerRepository
import com.chriscartland.garage.domain.repository.AppSettingsRepository
import com.chriscartland.garage.domain.repository.DoorFcmRepository
import kotlinx.coroutines.flow.first

/**
 * FCM repository that delegates all messaging calls to [MessagingBridge].
 *
 * No Firebase imports — all Firebase interaction happens through the bridge.
 */
class FirebaseDoorFcmRepository(
    private val messagingBridge: MessagingBridge,
    private val settings: AppSettingsRepository,
    private val appLoggerRepository: AppLoggerRepository,
) : DoorFcmRepository {
    override suspend fun fetchStatus(): DoorFcmState {
        Logger.d { "fetchStatus" }
        val topic = getFcmTopic()
        Logger.d { "fetchStatus: $topic" }
        return if (topic == null) {
            DoorFcmState.NotRegistered
        } else {
            DoorFcmState.Registered(topic = topic)
        }
    }

    override suspend fun registerDoor(fcmTopic: DoorFcmTopic): DoorFcmState {
        Logger.d { "registerDoor: $fcmTopic" }
        // Unsubscribe from old topic.
        val oldFcmTopic = getFcmTopic()
        if (oldFcmTopic != null && fcmTopic != oldFcmTopic) {
            Logger.i { "Unsubscribing from old FCM Topic: $oldFcmTopic" }
            messagingBridge.unsubscribeFromTopic(oldFcmTopic.string)
        }
        // Save new topic.
        setFcmTopic(fcmTopic)
        Logger.i { "Subscribing to FCM Topic: $fcmTopic" }
        appLoggerRepository.log(AppLoggerKeys.FCM_SUBSCRIBE_TOPIC)
        val subscriptionSuccess = messagingBridge.subscribeToTopic(fcmTopic.string)
        if (!subscriptionSuccess) {
            return DoorFcmState.NotRegistered.also {
                Logger.d { "Failed to subscribe to topic $fcmTopic, returning state $it" }
            }
        }
        val token = messagingBridge.getToken()
        if (token == null) {
            return DoorFcmState.NotRegistered.also {
                Logger.d { "Failed to get FCM registration token, returning state $it" }
            }
        }
        return DoorFcmState.Registered(topic = fcmTopic).also {
            Logger.d { "Successfully registered for topic $fcmTopic, returning state $it" }
        }
    }

    override suspend fun deregisterDoor(): DoorFcmState {
        Logger.d { "deregisterDoor" }
        val oldFcmTopic = getFcmTopic()
        if (oldFcmTopic == null) {
            return DoorFcmState.NotRegistered.also {
                Logger.d { "No FCM topic to deregister, returning state $it" }
            }
        }
        removeFcmTopic()
        Logger.i { "Unsubscribing from old FCM Topic: $oldFcmTopic" }
        messagingBridge.unsubscribeFromTopic(oldFcmTopic.string)
        return DoorFcmState.NotRegistered.also {
            Logger.d { "Successfully deregistered for topic $oldFcmTopic, returning state $it" }
        }
    }

    private suspend fun getFcmTopic(): DoorFcmTopic? {
        Logger.d { "getFcmTopic" }
        return settings.fcmDoorTopic.flow.first().let {
            DoorFcmTopic(it)
        }
    }

    private suspend fun setFcmTopic(topic: DoorFcmTopic) {
        Logger.d { "setFcmTopic: $topic" }
        settings.fcmDoorTopic.set(topic.string)
    }

    private suspend fun removeFcmTopic() {
        Logger.d { "removeFcmTopic" }
        settings.fcmDoorTopic.restoreDefault()
    }
}
