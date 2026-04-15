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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chriscartland.garage.di.rememberAppComponent
import com.chriscartland.garage.usecase.DoorViewModel
import com.chriscartland.garage.usecase.FcmRegistrationAction
import com.chriscartland.garage.usecase.toAction

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
fun FCMRegistration() {
    val component = rememberAppComponent()
    val viewModel: DoorViewModel = viewModel { component.doorViewModel }
    val fcmState by viewModel.fcmRegistrationStatus.collectAsState()
    LaunchedEffect(key1 = fcmState) {
        when (fcmState.toAction()) {
            FcmRegistrationAction.FETCH_STATUS -> viewModel.fetchFcmRegistrationStatus()
            FcmRegistrationAction.REGISTER -> viewModel.registerFcm()
            FcmRegistrationAction.NONE -> { /* Already registered */ }
        }
    }
}
