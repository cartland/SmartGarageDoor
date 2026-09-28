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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.chriscartland.garage.GarageApplication
import com.chriscartland.garage.MainActivity
import com.chriscartland.garage.R
import com.chriscartland.garage.domain.model.DoorColorState
import com.chriscartland.garage.presentation.GlanceStatus
import com.chriscartland.garage.ui.home.HomeStatusFormatter
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * The garage door on the phone's home screen.
 *
 * The fourth surface driven by one shared
 * [com.chriscartland.garage.presentation.GlanceStatus] — after the door screen,
 * the Wear tile and the watch-face complication. It contributes no verdict of
 * its own; it only words and draws one.
 *
 * ## READ-ONLY, and do not "improve" this
 *
 * Tapping opens the app. There is no route from this widget to the garage
 * button, asserted by `GarageDoorWidgetSafetyTest`.
 *
 * This is the same decision the Wear tile made, and the argument is stronger
 * here. The app's own button is a two-tap confirm and the watch's is a
 * press-and-HOLD, both deliberately continuous or deliberately repeated,
 * because the thing on the other end is a real door on a real house. A home
 * screen is swiped across, pocket-dialled, and handed to children; a widget tap
 * is a single tap with no room for a confirm gesture and no way to express a
 * hold. Putting the door behind it would trade the strongest guard in the app
 * for the weakest gesture available on the platform.
 *
 * ## Answer first, refresh second
 *
 * [provideGlance] renders whatever is on disk immediately and only then asks the
 * server, pushing a second frame if the answer differs. A widget that waited on
 * the network would show the previous render — possibly for seconds, possibly
 * stale — at the one moment somebody glanced at their home screen. The refresh
 * still matters: without it a widget the system rarely updates would present a
 * remembered door as current, which is the failure this whole family of surfaces
 * is designed against.
 *
 * Failure is not silent: a refresh that could not reach the server flips the
 * reading to STALE via [WidgetGlanceStatus], and the subline says so instead of
 * claiming a span.
 */
class GarageDoorWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val component = (context.applicationContext as GarageApplication).component
        val reader = WidgetGlanceStatus(
            localDoorDataSource = component.localDoorDataSource,
            fetchCurrentDoorEvent = component.fetchCurrentDoorEventUseCase,
            clock = component.appClock,
        )

        // Read the cache BEFORE composing so the first frame is the real door
        // rather than a placeholder that has to be corrected.
        val status = MutableStateFlow(reader.current())

        coroutineScope {
            launch {
                reader.refresh()
                // Re-read rather than trusting the refresh's own return: the
                // verdict depends on the fetch outcome AND on what landed in
                // the cache, and current() is the one place that combines them.
                status.value = reader.current()
            }
            provideContent {
                val shown by status.collectAsState()
                GarageDoorWidgetContent(shown)
            }
        }
    }
}

/**
 * The two lines, already resolved to words.
 *
 * A separate type from [GlanceStatus] so [GarageDoorWidgetBody] can be rendered
 * with no [android.content.Context] at all — which is what lets the DRAWING be
 * unit-tested on the JVM rather than only on a device. Glance cannot be rendered
 * by Layoutlib, so without this split the layout would be the one part of this
 * surface nothing verified.
 */
internal data class GarageWidgetText(
    val headline: String,
    val subline: String?,
)

/**
 * Resolves a [GlanceStatus] into words, then draws it.
 *
 * The only Context-dependent step, kept to one place so everything below it is
 * testable without one.
 */
@Composable
internal fun GarageDoorWidgetContent(status: GlanceStatus) {
    GarageDoorWidgetBody(
        text = GarageWidgetText(
            headline = LocalContext.current.getString(GarageWidgetWords.headline(status.headline)),
            subline = sublineText(status),
        ),
        colorState = status.colorState,
        isMuted = status.freshness.isMuted,
    )
}

/**
 * The drawing. Takes plain values, so a test can render it and assert on the
 * nodes without a widget host or a Context.
 */
@Composable
internal fun GarageDoorWidgetBody(
    text: GarageWidgetText,
    colorState: DoorColorState,
    isMuted: Boolean,
) {
    val textColor = GarageWidgetColors.onBackground(colorState, isMuted)
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GarageWidgetColors.background(colorState, isMuted))
            .cornerRadius(WIDGET_CORNER_RADIUS)
            .padding(WIDGET_PADDING)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        Text(
            text = text.headline,
            style = TextStyle(
                color = textColor,
                fontSize = HEADLINE_SIZE,
                fontWeight = FontWeight.Medium,
            ),
        )
        val subline = text.subline
        if (subline != null) {
            Text(
                text = subline,
                style = TextStyle(color = textColor, fontSize = SUBLINE_SIZE),
            )
        }
    }
}

/**
 * The second line, resolved to words.
 *
 * The CHOICE of line is [GarageWidgetSubline]'s (JVM-tested); this only renders
 * it. `formatTimeOrDate` is the Home screen's own formatter, reused so that
 * "3:42 PM" on the widget and "3:42 PM" on the door screen cannot come out
 * differently — and so a door older than today picks up the same "Apr 28, 9:47
 * PM" qualifier rather than a second, vaguer convention.
 */
@Composable
private fun sublineText(status: GlanceStatus): String? {
    val context = LocalContext.current
    return when (val subline = GarageWidgetSubline.forStatus(status)) {
        is WidgetSubline.Since ->
            context.getString(
                R.string.widget_since_format,
                HomeStatusFormatter.formatTimeOrDate(
                    instant = Instant.ofEpochSecond(subline.epochSeconds),
                    now = Instant.now(),
                    zone = ZoneId.systemDefault(),
                ),
            )

        WidgetSubline.Stale -> context.getString(R.string.widget_liveness_stale)
        WidgetSubline.Silent -> null
    }
}

// Named rather than inlined so the box metrics and the two text sizes are
// visible together. Deliberately not Spacing.kt tokens: those are Material 3
// values for a scrolling app surface, and a widget is sized by the launcher's
// grid, not by the app's rhythm.
private val WIDGET_CORNER_RADIUS = 16.dp
private val WIDGET_PADDING = 12.dp
private val HEADLINE_SIZE = 20.sp
private val SUBLINE_SIZE = 13.sp
