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

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.chriscartland.garage.applogger.AppLoggerViewModelImpl
import com.chriscartland.garage.config.AppLoggerKeys
import com.chriscartland.garage.door.DoorViewModel
import com.chriscartland.garage.door.FcmRegistrationStatus

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
fun FCMRegistration(
    viewModel: DoorViewModel = hiltViewModel(),
    appLoggerViewModel: AppLoggerViewModelImpl = hiltViewModel(),
) {
    val context = LocalContext.current as ComponentActivity
    val fcmState by viewModel.fcmRegistrationStatus.collectAsState()
    LaunchedEffect(key1 = fcmState) {
        // Subscribe to FCM updates.
        when (fcmState) {
            FcmRegistrationStatus.UNKNOWN -> {
                Log.d(TAG, "Unknown FCM registration status, fetching...")
                viewModel.fetchFcmRegistrationStatus(context)
            }
            FcmRegistrationStatus.REGISTERED -> {
                Log.d(TAG, "FCM registration status is registered")
            }
            FcmRegistrationStatus.NOT_REGISTERED -> {
                Log.d(TAG, "FCM registration status is not registered, registering...")
            }
        }
    }
}

private const val TAG: String = "FcmRegistration"
