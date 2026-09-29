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

package com.chriscartland.garage.debug

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.view.doOnLayout
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.presentation.GlanceStatus
import com.chriscartland.garage.presentation.GlanceStatusMapper
import com.chriscartland.garage.widget.GarageDoorWidgetContent
import com.chriscartland.garage.widget.GarageWidgetPreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/**
 * Draws the home-screen WIDGET, for the screenshot gallery and the picker image.
 *
 * The widget is the one surface on the phone that a preview cannot show and a
 * unit test cannot see drawn: it is Glance rather than Compose, so Layoutlib
 * has no host for it, and `GarageDoorWidgetBodyTest` asserts on the composition
 * rather than on pixels. This composes the widget to RemoteViews exactly as the
 * launcher would ([GlanceRemoteViews.compose] at a declared size), inflates
 * the result into a frame of that size, and then copies exactly that frame
 * plus a margin off the rendered window ([PixelCopy]) into a PNG
 * (`widget-<stage>.png` in the app's external files dir, which the script
 * pulls). Copying the frame's own rectangle is what makes the capture exact —
 * no crop arithmetic, no system-bar offset, no dependence on the emulator's
 * density — and copying the RENDERED surface rather than drawing the view to a
 * software canvas is what keeps the corner radius: Glance rounds the card by
 * outline clipping, which only the hardware renderer applies.
 *
 * Driven by `scripts/generate-widget-screenshots.sh`:
 *
 *   adb shell am start -n com.chriscartland.garage.debug/com.chriscartland.garage.debug.WidgetStagesActivity \
 *     -e stage closed_2x1|closed_4x1|open_4x1|stale_4x1|no_signal_4x1
 *
 * Stages are PAIRS to review together:
 *
 *   closed_2x1  vs  closed_4x1  — same words, stacked vs one row. The layout
 *                                 choice is GarageWidgetLayout's; this shows it.
 *   open_4x1    vs  stale_4x1   — identical door, one muted.
 *   no_signal_4x1               — nothing known and nothing reachable.
 *
 * The closed reading IS [GarageWidgetPreview.status], so the capture of
 * `closed_4x1` doubles as the honest `android:previewImage`. Fixtures are
 * anchored relative to the device clock (the script pins it to 10:10) so the
 * since-line renders the same time on every regen.
 *
 * Rendered at LAUNCHER-SIZED cells, not at [GarageWidgetLayout.SIZES]. Those
 * are Glance's breakpoints — the minimum a size class needs, which is also
 * what the provider's `minWidth`/`minHeight` promise — and a launcher lays the
 * chosen composition out in its actual cell, which is larger: a 5-column
 * Pixel-style grid gives a 1-row widget roughly [CAPTURE_HEIGHT_DP] tall and
 * [COMPACT_CAPTURE_WIDTH_DP] / [WIDE_CAPTURE_WIDTH_DP] wide for two / four
 * columns. Rendering at the bare breakpoint (a first attempt did) clips the
 * headline and drops the subline below the frame, which is not what any
 * launcher shows. The width passed is what [GarageWidgetLayout.forSize] reads,
 * so each capture still exercises the arrangement its size class gets.
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class WidgetStagesActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val stage = intent.getStringExtra(STAGE_EXTRA) ?: STAGE_CLOSED_WIDE
        val size = sizeFor(stage)
        val status = statusFor(stage)

        val density = resources.displayMetrics.density
        val host = FrameLayout(this)
        val root = FrameLayout(this).apply {
            addView(
                host,
                FrameLayout.LayoutParams(
                    (size.width.value * density).toInt(),
                    (size.height.value * density).toInt(),
                    Gravity.CENTER,
                ),
            )
        }
        setContentView(root)

        scope.launch {
            val composed = GlanceRemoteViews().compose(context = this@WidgetStagesActivity, size = size) {
                GarageDoorWidgetContent(status)
            }
            host.addView(
                composed.remoteViews.apply(this@WidgetStagesActivity, host),
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            // The inflated views need a layout pass, and the surface a moment
            // to present it, before what is on screen is what a launcher shows.
            host.doOnLayout { laidOut -> laidOut.postDelayed({ writeCapture(stage, laidOut) }, CAPTURE_SETTLE_MILLIS) }
        }
    }

    /**
     * The frame plus a margin, copied off the rendered window so the margin is
     * the theme's own window background in either theme and the card keeps
     * its rounded corners.
     */
    private fun writeCapture(
        stage: String,
        frame: View,
    ) {
        val margin = (MARGIN_DP * resources.displayMetrics.density).toInt()
        val origin = IntArray(2).also(frame::getLocationInWindow)
        val rect = Rect(
            origin[0] - margin,
            origin[1] - margin,
            origin[0] + frame.width + margin,
            origin[1] + frame.height + margin,
        )
        val bitmap = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
        PixelCopy.request(
            window,
            rect,
            bitmap,
            { result ->
                if (result == PixelCopy.SUCCESS) {
                    val out = File(getExternalFilesDir(null), "$CAPTURE_PREFIX$stage.png")
                    out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } else {
                    Log.e(TAG, "PixelCopy failed for stage $stage: $result")
                }
            },
            Handler(Looper.getMainLooper()),
        )
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun sizeFor(stage: String): DpSize =
        if (stage.endsWith(SUFFIX_COMPACT)) {
            DpSize(COMPACT_CAPTURE_WIDTH_DP.dp, CAPTURE_HEIGHT_DP.dp)
        } else {
            DpSize(WIDE_CAPTURE_WIDTH_DP.dp, CAPTURE_HEIGHT_DP.dp)
        }

    private fun statusFor(stage: String): GlanceStatus =
        when (stage.removeSuffix(SUFFIX_COMPACT).removeSuffix(SUFFIX_WIDE)) {
            STAGE_OPEN -> GlanceStatusMapper.forGlance(
                doorPosition = DoorPosition.OPEN,
                lastCheckInEpochSeconds = NOW - TWO_MINUTES,
                lastChangeEpochSeconds = NOW - EIGHT_MINUTES,
                nowEpochSeconds = NOW,
                isFetchError = false,
            )
            STAGE_STALE -> GlanceStatusMapper.forGlance(
                doorPosition = DoorPosition.OPEN,
                lastCheckInEpochSeconds = NOW - SIX_HOURS,
                lastChangeEpochSeconds = NOW - SIX_HOURS,
                nowEpochSeconds = NOW,
                isFetchError = false,
            )
            STAGE_NO_SIGNAL -> GlanceStatusMapper.forGlance(
                doorPosition = null,
                lastCheckInEpochSeconds = null,
                lastChangeEpochSeconds = null,
                nowEpochSeconds = NOW,
                isFetchError = true,
            )
            else -> GarageWidgetPreview.status(nowEpochSeconds = NOW)
        }

    companion object {
        const val STAGE_EXTRA = "stage"
        const val STAGE_OPEN = "open"
        const val STAGE_STALE = "stale"
        const val STAGE_NO_SIGNAL = "no_signal"
        const val SUFFIX_COMPACT = "_2x1"
        const val SUFFIX_WIDE = "_4x1"
        const val STAGE_CLOSED_WIDE = "closed_4x1"

        /** Written to `getExternalFilesDir(null)`; the script pulls `widget-<stage>.png`. */
        const val CAPTURE_PREFIX = "widget-"
        const val MARGIN_DP = 16
        private const val CAPTURE_SETTLE_MILLIS = 250L
        private const val TAG = "WidgetStages"

        /** A 1-row cell on a 5-column Pixel-style grid; see the class KDoc. */
        const val CAPTURE_HEIGHT_DP = 100
        const val COMPACT_CAPTURE_WIDTH_DP = 160
        const val WIDE_CAPTURE_WIDTH_DP = 330

        private const val TWO_MINUTES = 2L * 60L
        private const val EIGHT_MINUTES = 8L * 60L
        private const val SIX_HOURS = 6L * 3_600L

        /** The device's clock, so the since-line agrees with the clock that renders it. */
        private val NOW: Long get() = System.currentTimeMillis() / 1_000L
    }
}
