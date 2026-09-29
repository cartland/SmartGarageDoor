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
import com.chriscartland.garage.ui.GarageApp
import com.chriscartland.garage.ui.LaunchTarget

class MainActivity : ComponentActivity() {
    private val component by lazy { (application as GarageApplication).component }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Edge-to-edge required on Android 15+ (target SDK 35).
        // A static app shortcut names where to land (LaunchTarget); a plain
        // launch names nothing and lands on Home. Read once, here: the
        // launcher starts a shortcut with CLEAR_TASK, so a shortcut tap always
        // arrives through onCreate, never onNewIntent. On recreation the saved
        // back stack outranks this seed, as it should.
        val launchTarget = LaunchTarget.from(intent.getStringExtra(LaunchTarget.EXTRA))
        trace("MainActivity.setContent") {
            setContent {
                GarageApp(launchTarget = launchTarget)
            }
        }
        component.appStartup.run()
    }
}
