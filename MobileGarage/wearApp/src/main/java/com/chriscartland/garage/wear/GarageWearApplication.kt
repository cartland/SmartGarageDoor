/*
 * Copyright 2026 Chris Cartland. All rights reserved.
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

package com.chriscartland.garage.wear

import android.app.Application
import com.chriscartland.garage.wear.auth.DataLayerWearAuthRelayClient
import com.chriscartland.garage.wear.auth.FirebaseAuthBridge
import com.chriscartland.garage.wear.auth.RelayFallbackAuthBridge
import com.chriscartland.garage.wear.config.WearAppConfigFactory
import com.chriscartland.garage.wear.di.WearComponent
import com.chriscartland.garage.wear.di.WearSignInConfig
import com.chriscartland.garage.wear.di.create

/**
 * Wear OS application. Owns the kotlin-inject [WearComponent] — the
 * Wear analog of the phone's `GarageApplication` + `AppComponent`.
 */
class GarageWearApplication : Application() {
    val component: WearComponent by lazy {
        WearComponent::class.create(
            // Local Firebase auth wins when present; otherwise the phone
            // relay supplies identity + tokens (Credential Manager sign-in
            // fails on some watches — see docs/WEAR_OS.md).
            authBridge = RelayFallbackAuthBridge(
                local = FirebaseAuthBridge(),
                relay = DataLayerWearAuthRelayClient(this),
            ),
            appConfig = WearAppConfigFactory.create(),
            signInConfig = WearSignInConfig(
                googleServerClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID,
            ),
            appVersion = "wear-${BuildConfig.VERSION_NAME}",
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Materialize the graph eagerly so always-on collectors
        // (auth state, door cache) start with the process.
        component
    }
}
