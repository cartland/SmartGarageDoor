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

package com.chriscartland.garage.wear.debug

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.FrameLayout
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ProtoLayoutScope
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.material3.ColorScheme
import androidx.wear.protolayout.material3.createMaterialScope
import androidx.wear.tiles.renderer.TileRenderer
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.presentation.GlanceStatusMapper
import com.chriscartland.garage.wear.tile.GarageDoorTileLayout
import java.util.concurrent.Executor

/**
 * Renders the garage TILE, for the screenshot gallery.
 *
 * The tile is the one surface on the watch that a preview cannot show and a
 * unit test cannot see: it is ProtoLayout rather than Compose, so there is no
 * `@Preview` for it, and building its layout needs a real `Context` while
 * inflating it needs a real renderer. Its decisions are covered on the JVM
 * (`WearTilePresenterTest`, `GarageTilePresentationTest`) — this is what
 * covers the part those cannot: that it actually draws, and what it looks
 * like when it does.
 *
 * Driven by `scripts/generate-wear-screenshots.sh` the same way the hero
 * stages are:
 *
 *   adb shell am start -n com.chriscartland.garage.debug/com.chriscartland.garage.wear.debug.TileStagesActivity \
 *     -e stage tile_closed|tile_open|tile_stale|tile_no_signal
 *
 * The stages are chosen as PAIRS to review together, which is the same way
 * the hero stages earn their keep:
 *
 *   tile_open     vs  tile_stale      — identical door, one muted. A
 *                                       regression that makes the muted look
 *                                       appear or disappear between them is
 *                                       the thing to catch.
 *   tile_closed   vs  tile_no_signal  — something known vs nothing known.
 *
 * Fixtures, not live data: the renderer must not depend on what the garage
 * happens to be doing while a screenshot is taken.
 */
class TileStagesActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val stage = intent.getStringExtra(STAGE_EXTRA) ?: STAGE_TILE_CLOSED
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(root)

        val status = when (stage) {
            STAGE_TILE_OPEN -> GlanceStatusMapper.forGlance(
                doorPosition = DoorPosition.OPEN,
                lastCheckInEpochSeconds = NOW - 120,
                // Open for eight minutes. The tile renders this as a running
                // duration, so the capture shows whatever the renderer makes
                // of it rather than a number we baked in.
                lastChangeEpochSeconds = NOW - 8 * 60,
                nowEpochSeconds = NOW,
                isFetchError = false,
            )
            // Six hours since the garage last said anything: the muted
            // treatment plus an age that explains it.
            STAGE_TILE_STALE -> GlanceStatusMapper.forGlance(
                doorPosition = DoorPosition.OPEN,
                lastCheckInEpochSeconds = NOW - 6 * 3_600,
                // Deliberately present, to prove it is WITHHELD rather than
                // merely absent: a door we cannot vouch for gets no duration.
                lastChangeEpochSeconds = NOW - 6 * 3_600,
                nowEpochSeconds = NOW,
                isFetchError = false,
            )
            STAGE_TILE_NO_SIGNAL -> GlanceStatusMapper.forGlance(
                doorPosition = null,
                lastCheckInEpochSeconds = null,
                lastChangeEpochSeconds = null,
                nowEpochSeconds = NOW,
                isFetchError = true,
            )
            else -> GlanceStatusMapper.forGlance(
                doorPosition = DoorPosition.CLOSED,
                lastCheckInEpochSeconds = NOW - 20,
                // Closed for three hours: exercises the hours unit, so the
                // pair of captures shows both sides of the dynamic switch.
                lastChangeEpochSeconds = NOW - 3 * 3_600,
                nowEpochSeconds = NOW,
                isFetchError = false,
            )
        }

        val metrics = resources.displayMetrics
        val deviceConfiguration = DeviceParametersBuilders.DeviceParameters
            .Builder()
            .setScreenWidthDp((metrics.widthPixels / metrics.density).toInt())
            .setScreenHeightDp((metrics.heightPixels / metrics.density).toInt())
            .setScreenDensity(metrics.density)
            .setScreenShape(DeviceParametersBuilders.SCREEN_SHAPE_ROUND)
            .setDevicePlatform(DeviceParametersBuilders.DEVICE_PLATFORM_WEAR_OS)
            .build()

        val scope = createMaterialScope(
            context = this,
            deviceConfiguration = deviceConfiguration,
            // Pinned off so the screenshots do not change with the emulator's
            // system theme — the same determinism the hero stages get from
            // fixing the clock.
            allowDynamicTheme = false,
            defaultColorScheme = ColorScheme(),
            // A real scope rather than the default null: MaterialScope's
            // accessor is a requireNotNull, so anything that reached for it
            // (image resource registration) would throw here even though the
            // real service always has one. The tile draws no images today,
            // which makes this cheap insurance rather than a fix.
            protoLayoutScope = ProtoLayoutScope(),
        )

        val renderer = TileRenderer
            .Builder(this, Executor { it.run() }) { }
            .build()
        renderer.inflateAsync(
            LayoutElementBuilders.Layout
                .fromLayoutElement(GarageDoorTileLayout.build(scope, this, status)),
            ResourceBuilders.Resources
                .Builder()
                .setVersion("1")
                .build(),
            root,
        )
    }

    companion object {
        const val STAGE_EXTRA = "stage"
        const val STAGE_TILE_CLOSED = "tile_closed"
        const val STAGE_TILE_OPEN = "tile_open"
        const val STAGE_TILE_STALE = "tile_stale"
        const val STAGE_TILE_NO_SIGNAL = "tile_no_signal"

        /**
         * The DEVICE's clock, not a fixed epoch.
         *
         * The tile's duration is rendered from ProtoLayout's platform time
         * source, so it is measured against whatever the watch thinks the
         * time is — a fixture anchored to a fixed epoch renders the distance
         * from that epoch to today, which is how the first capture of this
         * came out as "778 d…". Anchoring the fixtures RELATIVE to real now
         * keeps the rendered text stable across regens ("8 min" is always
         * "8 min") while agreeing with the clock doing the rendering.
         */
        private val NOW: Long get() = System.currentTimeMillis() / 1_000
    }
}
