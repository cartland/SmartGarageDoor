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

package com.chriscartland.garage.widget

import android.content.Context
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidgetManager
import co.touchlab.kermit.Logger
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.presentation.GlanceStatus
import com.chriscartland.garage.presentation.GlanceStatusMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The widget picker's image — the real widget, not a drawing of it.
 *
 * A hand-drawn preview drifts the first time the widget changes, and a
 * launcher-rendered one that shows placeholder text tells the user nothing
 * about what they are placing. So the picker gets the widget's own composable
 * over a canned reading ([status]): on Android 15+ through
 * [androidx.glance.appwidget.GlanceAppWidget.providePreview] + [publish], and
 * on older launchers through `android:previewImage`, which is the emulator
 * CAPTURE of that same reading (`scripts/generate-widget-screenshots.sh` copies
 * `widget-closed_4x1-light.png` into `drawable-nodpi`). Either way the picker
 * shows what the widget draws (strategy 3.2).
 */
object GarageWidgetPreview {
    /** A closed door, confirmed twenty seconds ago, closed for three hours: the widget on a good day. */
    fun status(nowEpochSeconds: Long): GlanceStatus =
        GlanceStatusMapper.forGlance(
            doorPosition = DoorPosition.CLOSED,
            lastCheckInEpochSeconds = nowEpochSeconds - CHECK_IN_AGE_SECONDS,
            lastChangeEpochSeconds = nowEpochSeconds - CLOSED_FOR_SECONDS,
            nowEpochSeconds = nowEpochSeconds,
            isFetchError = false,
        )

    /**
     * Hands the launcher the composed preview (Android 15+). Called at process
     * start because the picker is consulted BEFORE any widget is placed, so no
     * widget callback would run early enough. The result is logged, not acted
     * on: `RATE_LIMITED` means the last publish still stands.
     */
    fun publish(
        context: Context,
        scope: CoroutineScope,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        scope.launch {
            val result = GlanceAppWidgetManager(context).setWidgetPreviews(GarageDoorWidgetReceiver::class)
            Logger.d { "Widget preview published: $result" }
        }
    }

    const val CHECK_IN_AGE_SECONDS = 20L
    const val CLOSED_FOR_SECONDS = 3L * 3_600L
}
