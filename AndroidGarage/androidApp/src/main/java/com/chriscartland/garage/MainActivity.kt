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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.util.trace
import com.chriscartland.garage.di.activityViewModel
import com.chriscartland.garage.ui.GarageApp

class MainActivity : ComponentActivity() {
    private val component by lazy { (application as GarageApplication).component }
    private val authViewModel by lazy { activityViewModel(this) { component.authViewModel } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Edge-to-edge required on Android 15+ (target SDK 35).
        trace("MainActivity.setContent") {
            setContent {
                GarageApp(
                    authViewModel = authViewModel,
                )
            }
        }
        component.appStartup.run()
    }
}
