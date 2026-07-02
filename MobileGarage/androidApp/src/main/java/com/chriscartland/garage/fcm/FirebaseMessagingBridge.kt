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

import co.touchlab.kermit.Logger
import com.chriscartland.garage.data.MessagingBridge
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Firebase implementation of [MessagingBridge].
 *
 * Wraps all Firebase Messaging SDK calls.
 */
class FirebaseMessagingBridge : MessagingBridge {
    override suspend fun subscribeToTopic(topic: String): Boolean =
        suspendCoroutine { continuation ->
            Firebase.messaging
                .subscribeToTopic(topic)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Logger.i { "Subscribed to FCM Topic $topic" }
                    } else {
                        Logger.e { "Failed to subscribe to FCM Topic $topic: ${task.exception}" }
                    }
                    continuation.resume(task.isSuccessful)
                }
        }

    override suspend fun unsubscribeFromTopic(topic: String) {
        Firebase.messaging.unsubscribeFromTopic(topic)
    }

    override suspend fun getToken(): String? =
        suspendCoroutine { continuation ->
            Firebase.messaging.token
                .addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Logger.w { "Fetching FCM registration token failed: ${task.exception}" }
                        continuation.resume(null)
                    } else {
                        val token = task.result
                        Logger.d { "FCM Instance Token: $token" }
                        continuation.resume(token)
                    }
                }
        }
}
