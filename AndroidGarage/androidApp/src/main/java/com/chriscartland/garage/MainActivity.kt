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

package com.chriscartland.garage

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.util.trace
import com.chriscartland.garage.applogger.AppLoggerViewModel
import com.chriscartland.garage.applogger.AppLoggerViewModelImpl
import com.chriscartland.garage.auth.AuthViewModel
import com.chriscartland.garage.auth.AuthViewModelImpl
import com.chriscartland.garage.config.AppLoggerKeys
import com.chriscartland.garage.door.DoorViewModel
import com.chriscartland.garage.door.DoorViewModelImpl
import com.chriscartland.garage.ui.GarageApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels<AuthViewModelImpl>()
    private val doorViewModel: DoorViewModel by viewModels<DoorViewModelImpl>()
    private val appLoggerViewModel: AppLoggerViewModel by viewModels<AppLoggerViewModelImpl>()

    private val oneTapSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { authViewModel.processGoogleSignInResult(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Edge-to-edge required on Android 15+ (target SDK 35).
        trace("MainActivity.setContent") {
            setContent {
                LaunchedEffect(authViewModel.googleSignInIntentSenderRequest) {
                    authViewModel.googleSignInIntentSenderRequest.collect { intentSenderRequest ->
                        oneTapSignInLauncher.launch(intentSenderRequest)
                    }
                }
                GarageApp(
                    doorViewModel = doorViewModel,
                    appLoggerViewModel = appLoggerViewModel,
                    authViewModel = authViewModel,
                )
            }
        }
        Log.d(TAG, "onCreate: Try to subscribe to FCM topic")
        doorViewModel.registerFcm(this)
        appLoggerViewModel.log(AppLoggerKeys.ON_CREATE_FCM_SUBSCRIBE_TOPIC)
    }
}

private const val TAG = "MainActivity"
