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
import android.content.ComponentName
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.chriscartland.garage.usecase.SystemSurfaceRefresher
import com.chriscartland.garage.wear.auth.DataLayerWearAuthRelayClient
import com.chriscartland.garage.wear.auth.FirebaseAuthBridge
import com.chriscartland.garage.wear.auth.RelayFallbackAuthBridge
import com.chriscartland.garage.wear.complication.GarageDoorComplicationService
import com.chriscartland.garage.wear.config.WearAppConfigFactory
import com.chriscartland.garage.wear.data.WearStatusCache
import com.chriscartland.garage.wear.di.WearComponent
import com.chriscartland.garage.wear.di.WearSignInConfig
import com.chriscartland.garage.wear.di.create
import com.chriscartland.garage.wear.ongoing.DoorActivityService
import com.chriscartland.garage.wear.tile.GarageDoorTileService
import kotlinx.coroutines.launch

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
            // One DataStore for the process, however the system chooses to
            // start it — see WearStatusCache.
            statusCacheStorage = WearStatusCache.storage(applicationContext),
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Materialize the graph eagerly so always-on collectors
        // (auth state, door cache) start with the process.
        component
        nudgeTheGlanceSurfacesWhenTheDoorChanges()
        // The channel the watch face's door-progress chip is filed under, made
        // before any press can need it. Idempotent.
        DoorActivityService.ensureChannel(this)

        // Tell the phone which build is on the wrist. Fire-and-forget on an
        // application-lifetime scope: nothing on the watch waits for it, and it
        // must not be tied to a screen — the phone reads this most often when
        // the watch app is not open at all.
        component.applicationScope.launch {
            WearAppInfoPublisher.publish(
                context = this@GarageWearApplication,
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE.toLong(),
            )
        }
    }

    /**
     * The tile redraws on a throttled freshness request and the complication
     * every ten minutes; neither hears about a change the app's own poll just
     * saw. So the face could show a door the user had just WATCHED move in the
     * app (2026-09-28 audit, finding 3.2; strategy 0.3). The shared
     * [SystemSurfaceRefresher] observes the door and asks both to redraw on a
     * change. Their schedules stay the floor; this is the ceiling, and it
     * cannot loop because an equal re-fetched event does not emit.
     */
    private fun nudgeTheGlanceSurfacesWhenTheDoorChanges() {
        SystemSurfaceRefresher(
            doorEvents = component.doorRepository.currentDoorEvent,
            refresh = { requestGlanceSurfaceUpdates() },
            scope = component.applicationScope,
        ).start()
    }

    private fun requestGlanceSurfaceUpdates() {
        TileService.getUpdater(this).requestUpdate(GarageDoorTileService::class.java)
        ComplicationDataSourceUpdateRequester
            .create(this, ComponentName(this, GarageDoorComplicationService::class.java))
            .requestUpdateAll()
    }
}
