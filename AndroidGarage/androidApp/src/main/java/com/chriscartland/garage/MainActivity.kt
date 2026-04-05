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

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.util.trace
import com.chriscartland.garage.auth.RC_ONE_TAP_SIGN_IN
import com.chriscartland.garage.config.AppLoggerKeys
import com.chriscartland.garage.di.activityViewModel
import com.chriscartland.garage.ui.GarageApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /**
     * Activity-scoped ViewModels shared between Compose and Activity callbacks.
     *
     * Created via [activityViewModel] to ensure the same instance is stored in the
     * Activity's ViewModelStore. Compose receives these as parameters and uses the
     * same instances. This prevents bugs where instance state (like SignInClient)
     * exists on one ViewModel but is accessed from a different instance.
     */
    private val component by lazy { (application as GarageApplication).component }
    private val authViewModel by lazy { activityViewModel(this) { component.authViewModel } }
    private val doorViewModel by lazy { activityViewModel(this) { component.doorViewModel } }
    private val appLoggerViewModel by lazy { activityViewModel(this) { component.appLoggerViewModel } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Edge-to-edge required on Android 15+ (target SDK 35).
        trace("MainActivity.setContent") {
            setContent {
                GarageApp(
                    authViewModel = authViewModel,
                    doorViewModel = doorViewModel,
                    appLoggerViewModel = appLoggerViewModel,
                )
            }
        }
        Log.d(TAG, "onCreate: Try to subscribe to FCM topic")
        doorViewModel.registerFcm(this)
        appLoggerViewModel.log(AppLoggerKeys.ON_CREATE_FCM_SUBSCRIBE_TOPIC)
    }

    // TODO: Migrate away from onActivityResult with Activity Result API and ActivityResultContract.
    @Deprecated(
        "This method has been deprecated in favor of using the Activity Result API",
        level = DeprecationLevel.WARNING,
    )
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d("MainActivity", "onActivityResult")
        when (requestCode) {
            RC_ONE_TAP_SIGN_IN -> {
                Log.d("MainActivity", "RC_ONE_TAP_SIGN_IN")
                if (data == null) {
                    Log.e("MainActivity", "onActivityResult: data is null")
                    return
                }
                authViewModel.processGoogleSignInResult(data)
            }
        }
    }
}

private const val TAG = "MainActivity"
