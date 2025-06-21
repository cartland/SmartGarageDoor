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

package com.chriscartland.garage.fcm

import android.app.Activity
import android.util.Log
import com.chriscartland.garage.applogger.AppLoggerRepository
import com.chriscartland.garage.config.AppLoggerKeys
import com.chriscartland.garage.settings.AppSettings
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

interface DoorFcmRepository {
    suspend fun fetchStatus(activity: Activity): DoorFcmState

    suspend fun registerDoor(
        activity: Activity,
        fcmTopic: DoorFcmTopic,
    ): DoorFcmState

    suspend fun deregisterDoor(activity: Activity): DoorFcmState
}

class DoorFcmRepositoryImpl
@Inject
constructor(
    private val settings: AppSettings,
    private val appLoggerRepository: AppLoggerRepository,
) : DoorFcmRepository {
    override suspend fun fetchStatus(activity: Activity): DoorFcmState {
        Log.d(TAG, "fetchStatus")
        val topic = getFcmTopic()
        Log.d(TAG, "fetchStatus: $topic")
        return if (topic == null) {
            DoorFcmState.NotRegistered
        } else {
            DoorFcmState.Registered(topic = topic)
        }
    }

    override suspend fun registerDoor(
        activity: Activity,
        topic: DoorFcmTopic,
    ): DoorFcmState {
        Log.d(TAG, "registerDoor: $topic")
        // Unsubscribe from old topic.
        val oldFcmTopic = getFcmTopic()
        if (oldFcmTopic != null && topic != oldFcmTopic) {
            Log.i(TAG, "Unsubscribing from old FCM Topic: $oldFcmTopic")
            Firebase.messaging.unsubscribeFromTopic(oldFcmTopic.string)
        }
        // Save new topic.
        setFcmTopic(topic)
        Log.i(TAG, "Subscribing to FCM Topic: $topic")
        appLoggerRepository.log(AppLoggerKeys.FCM_SUBSCRIBE_TOPIC)
        val subscriptionSuccess =
            suspendCoroutine { continuation ->
                Firebase.messaging
                    .subscribeToTopic(topic.string)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.i(TAG, "Subscribed to FCM Topic $topic")
                            continuation.resume(true)
                        } else {
                            Log.e(
                                TAG,
                                "Failed to subscribe to FCM Topic $topic: " + task.exception.toString(),
                            )
                            continuation.resume(false)
                        }
                    }
            }
        if (!subscriptionSuccess) {
            return DoorFcmState.NotRegistered.also {
                Log.d(TAG, "Failed to subscribe to topic $topic, returning state $it")
            }
        }
        val token =
            suspendCoroutine { continuation ->
                Firebase.messaging.token
                    .addOnCompleteListener { task ->
                        if (!task.isSuccessful) {
                            Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                            continuation.resume(null)
                        }
                        val token = task.result
                        if (token == null) {
                            Log.d(TAG, "Fetching FCM registration token null")
                            continuation.resume(null)
                        } else {
                            Log.d(TAG, "FCM Instance Token: $token")
                            continuation.resume(token)
                        }
                    }
            }
        if (token == null) {
            return DoorFcmState.NotRegistered.also {
                Log.d(TAG, "Failed to get FCM registration token, returning state $it")
            }
        }
        return DoorFcmState.Registered(topic = topic).also {
            Log.d(TAG, "Successfully registered for topic $topic, returning state $it")
        }
    }

    override suspend fun deregisterDoor(activity: Activity): DoorFcmState {
        Log.d(TAG, "deregisterDoor")
        val oldFcmTopic = getFcmTopic()
        if (oldFcmTopic == null) {
            return DoorFcmState.NotRegistered.also {
                Log.d(TAG, "No FCM topic to deregister, returning state $it")
            }
        }
        removeFcmTopic()
        Log.i(TAG, "Unsubscribing from old FCM Topic: $oldFcmTopic")
        Firebase.messaging.unsubscribeFromTopic(oldFcmTopic.string)
        return DoorFcmState.NotRegistered.also {
            Log.d(TAG, "Successfully deregistered for topic $oldFcmTopic, returning state $it")
        }
    }

    private fun getFcmTopic(): DoorFcmTopic? {
        Log.d(TAG, "getFcmTopic")
        return settings.fcmDoorTopic.get().let {
            DoorFcmTopic(it)
        }
    }

    private fun setFcmTopic(topic: DoorFcmTopic) {
        Log.d(TAG, "setFcmTopic: $topic")
        settings.fcmDoorTopic.set(topic.string)
    }

    private fun removeFcmTopic() {
        Log.d(TAG, "removeFcmTopic")
        settings.fcmDoorTopic.restoreDefault()
    }
}

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class DoorFcmRepositoryModule {
    @Binds
    abstract fun bindDoorFcmRepository(impl: DoorFcmRepositoryImpl): DoorFcmRepository
}

private const val TAG = "DoorFcmRepository"
