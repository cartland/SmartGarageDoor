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

package com.chriscartland.garage.wear.tile

import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import co.touchlab.kermit.Logger
import com.chriscartland.garage.wear.GarageWearApplication
import kotlinx.coroutines.launch

/**
 * The garage door as a Wear **tile** — swipe right from the watch face.
 *
 * **Read-only, by design.** Tapping anywhere opens the app on the door
 * screen; there is no route from this tile to the garage button. That is the
 * same reasoning that makes the app's own button a press-and-HOLD rather than
 * a tap (see `WearHomeViewModel`): a watch screen is easy to touch by
 * accident, and a tile — which lives in a carousel the user swipes through —
 * is the easiest surface in the system to touch without meaning to. A tile
 * also cannot express a continuous hold, so putting the door on one would
 * mean replacing the strongest guard in the app with the weakest gesture
 * available. `GarageDoorTileSafetyTest` asserts the absence rather than
 * trusting it.
 *
 * **What makes it reliable is that it never pretends.** The SYSTEM renders a
 * tile, in a process that may have been started for the purpose, and the
 * system — not the app — decides when it is re-rendered
 * ([FRESHNESS_INTERVAL_MILLIS] is a request: throttled to at most once a
 * minute, inexact, and counted in elapsed rather than wall-clock time). So no
 * tile can promise to show the current state of anything, and one that
 * implied otherwise would be worse than useless on a door. What it can
 * promise is never to show a state it cannot vouch for: every render says how
 * old the reading is and drains the door to grey once the answer stops being
 * trustworthy — the same [com.chriscartland.garage.presentation.GlanceStatus]
 * verdict, from the same shared mappers, that the door screen renders.
 *
 * **Answer first, refresh second.** `onTileRequest` must return promptly, and
 * the watch's network path (a Bluetooth relay, or Wi-Fi at the garage) is the
 * slowest in the system — so this never waits on it. It renders what is
 * already known (hydrated from disk by `PersistedLocalDoorDataSource`, which
 * is what made a tile possible at all), then refreshes and asks for a
 * re-render only if the door turned out to be somewhere else. That gate is
 * what stops it looping; see [WearTilePresenter.refreshAndReportChange].
 *
 * Signed out is fine: door status needs no token, so the tile works on a
 * watch that has never signed in — it simply cannot offer the button, which
 * it was never going to offer.
 */
class GarageDoorTileService : Material3TileService() {
    override suspend fun MaterialScope.tileResponse(requestParams: RequestBuilders.TileRequest): TileBuilders.Tile {
        val component = (applicationContext as GarageWearApplication).component
        val presenter = component.wearTilePresenter
        // Suspends only for the disk read (see WearTilePresenter.status), so
        // the tile answers with the door it already knows rather than with
        // "No signal" because a file had not been read yet.
        val status = presenter.status()

        // On the application scope, not this request: a TileService is torn
        // down as soon as it has answered, and the answer has already been
        // built by the time the refresh matters.
        component.applicationScope.launch {
            if (presenter.refreshAndReportChange()) {
                Logger.i { "GarageDoorTile: door moved since the last render; asking for a re-render" }
                TileService.getUpdater(applicationContext).requestUpdate(GarageDoorTileService::class.java)
            }
        }

        return TileBuilders.Tile
            .Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(
                TimelineBuilders.Timeline.fromLayoutElement(
                    GarageDoorTileLayout.build(this, applicationContext, status),
                ),
            ).setFreshnessIntervalMillis(FRESHNESS_INTERVAL_MILLIS)
            .build()
    }

    private companion object {
        /**
         * Bumped only when the tile's IMAGE resources change. There are none
         * today — everything is drawn from text and fills — but the field is
         * required and the renderer caches against it.
         */
        const val RESOURCES_VERSION = "1"

        /**
         * How often to ask the platform to re-render.
         *
         * One minute is the documented floor (below it the system throttles),
         * and it is also the cadence the garage itself reports at, so asking
         * more often could not surface anything newer. The platform honours
         * this only for a tile that is on screen or about to be, and treats it
         * as elapsed time rather than wall clock — which is precisely why the
         * age line exists instead of the freshness being assumed.
         */
        const val FRESHNESS_INTERVAL_MILLIS = 60_000L
    }
}
