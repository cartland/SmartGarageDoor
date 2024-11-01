/*
 * Copyright 2021 Chris Cartland. All rights reserved.
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
import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.chriscartland.garage.door.DoorViewModel
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging

/**
 * Register for FCM updates.
 *
 * This composable does not emit UI.
 *
 * Just once, this composable will try to register for FCM updates.
 * 1) Fetch the build timestamp from the server.
 * 2) Subscribe to the FCM topic.
 */
@Composable
fun FCMRegistration(viewModel: DoorViewModel = hiltViewModel()) {
    val context = LocalContext.current as ComponentActivity
    val state by viewModel.buildTimestamp.collectAsState()
    LaunchedEffect(key1 = state) {
        // Subscribe to FCM updates.
        val buildTimestamp: String? = state
        if (buildTimestamp == null) {
            Log.d(TAG, "buildTimestamp is null, fetching...")
            viewModel.fetchBuildTimestampCached()
        } else {
            updateOpenDoorFcmSubscription(context, buildTimestamp)
        }
    }
}

fun getSharedPref(activity: Activity, key: String, default: String? = null): String? {
    val sharedPref = activity.getPreferences(Context.MODE_PRIVATE)
    return sharedPref.getString(key, default)
}

fun setSharedPref(activity: Activity, key: String, value: String) {
    val sharedPref = activity.getPreferences(Context.MODE_PRIVATE)
    with(sharedPref.edit()) {
        putString(key, value)
        apply()
    }
}

fun getFcmTopic(activity: Activity) =
    getSharedPref(activity = activity, key = FCM_DOOR_OPEN_TOPIC)

fun setFcmTopic(activity: Activity, topic: String) =
    setSharedPref(activity, key = FCM_DOOR_OPEN_TOPIC, value = topic)

fun updateOpenDoorFcmSubscription(activity: Activity, buildTimestamp: String) {
    Log.d(TAG, "updateOpenDoorFcmSubscription")
    val newFcmTopic = buildTimestamp.toDoorOpenFcmTopic()
    // Unsubscribe from old topic.
    val oldFcmTopic = getFcmTopic(activity)
    if (oldFcmTopic != null && newFcmTopic != oldFcmTopic) {
        Log.i(TAG, "Unsubscribing from old FCM Topic: $oldFcmTopic")
        Firebase.messaging.unsubscribeFromTopic(oldFcmTopic)
    }
    // Save new topic.
    setFcmTopic(activity, newFcmTopic)
    Log.i(TAG, "Subscribing to FCM Topic: $newFcmTopic")
    Firebase.messaging.subscribeToTopic(newFcmTopic)
        .addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.i(TAG, "Subscribed to FCM Topic $newFcmTopic")
            } else {
                Log.e(
                    TAG,
                    "Failed to subscribe to FCM Topic $newFcmTopic: " + task.exception.toString()
                )
            }
        }
    Firebase.messaging.token
        .addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result
            if (token == null) {
                Log.d(TAG, "Fetching FCM registration token null")
            } else {
                Log.d(TAG, "FCM Instance Token: $token")
            }
        }
}

private fun String.toDoorOpenFcmTopic(): String {
    val re = Regex("[^a-zA-Z0-9-_.~%]")
    val filtered = re.replace(this, ".")
    return "door_open-$filtered"
}

private const val TAG: String = "FcmRegistration"

const val FCM_DOOR_OPEN_TOPIC = "com.chriscartland.garage.repository.FCM_DOOR_OPEN_TOPIC"
