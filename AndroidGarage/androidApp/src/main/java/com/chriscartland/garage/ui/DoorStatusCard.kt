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

package com.chriscartland.garage.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chriscartland.garage.R
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.presentation.demoDoorEvents
import com.chriscartland.garage.ui.theme.DoorColorState
import com.chriscartland.garage.ui.theme.DoorStatusColorScheme
import com.chriscartland.garage.ui.theme.LocalDoorStatusColorScheme
import com.chriscartland.garage.ui.theme.doorColorSet
import com.chriscartland.garage.ui.theme.doorColorState
import java.time.Instant

@Composable
fun DoorStatusCard(
    doorEvent: DoorEvent?,
    modifier: Modifier = Modifier,
    isCheckInStale: Boolean = false,
) {
    val doorPosition = doorEvent?.doorPosition ?: DoorPosition.UNKNOWN
    val lastChangeTimeSeconds = doorEvent?.lastChangeTimeSeconds
    val date = lastChangeTimeSeconds?.toFriendlyDate()
    val time = lastChangeTimeSeconds?.toFriendlyTime()
    // Select the door color based on the door state.
    val color = doorEvent.color(LocalDoorStatusColorScheme.current, isStale = isCheckInStale)
    // Blend the door color into the color of the text on background.
    val contentColor = GarageColors.blendColors(color, MaterialTheme.colorScheme.onBackground, 0.5f)
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(
            modifier = modifier,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = doorPosition.toFriendlyName(),
                    style = MaterialTheme.typography.titleLarge,
                )
                val lastChangeDuration by rememberDurationSince(
                    lastChangeTimeSeconds?.let { Instant.ofEpochSecond(it) },
                )
                Row(
                    modifier = Modifier,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    PreviewSafeIcon(
                        id = R.drawable.clock_icon,
                        contentDescription = "Date icon",
                        tint = contentColor,
                        modifier = Modifier
                            .size(32.dp)
                            .padding(start = 8.dp, end = 8.dp),
                    )
                    Text(
                        text = lastChangeDuration.toFriendlyDuration(),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                GarageIcon(
                    doorPosition = doorPosition,
                    modifier = Modifier
                        .weight(1f),
                    static = false,
                    color = color,
                )
                Row(
                    modifier = Modifier,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    PreviewSafeIcon(
                        id = R.drawable.calendar_icon,
                        contentDescription = "Time icon",
                        tint = contentColor,
                        modifier = Modifier
                            .size(32.dp)
                            .padding(start = 8.dp, end = 8.dp),
                    )
                    Text(
                        text = "$date, $time",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                when (doorPosition) {
                    in listOf(
                        DoorPosition.UNKNOWN,
                        DoorPosition.OPENING_TOO_LONG,
                        DoorPosition.OPEN_MISALIGNED,
                        DoorPosition.CLOSING_TOO_LONG,
                        DoorPosition.ERROR_SENSOR_CONFLICT,
                    ),
                    -> doorEvent?.message?.let { msg ->
                        if (msg.isNotBlank()) {
                            Text(
                                text = msg,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }

                    else -> {
                        // Nothing
                    }
                }
            }
        }
    }
}

fun DoorEvent?.color(
    scheme: DoorStatusColorScheme,
    isStale: Boolean,
): Color {
    val colorSet = scheme.doorColorSet(isStale = isStale)
    return when (doorColorState()) {
        DoorColorState.OPEN -> colorSet.open
        DoorColorState.CLOSED -> colorSet.closed
        DoorColorState.UNKNOWN -> colorSet.unknown
    }
}

fun DoorEvent?.onColor(
    scheme: DoorStatusColorScheme,
    isStale: Boolean,
): Color {
    val colorSet = scheme.doorColorSet(isStale = isStale)
    return when (doorColorState()) {
        DoorColorState.OPEN -> colorSet.onOpen
        DoorColorState.CLOSED -> colorSet.onClosed
        DoorColorState.UNKNOWN -> colorSet.onUnknown
    }
}

@Composable
fun RecentDoorEventListItem(
    doorEvent: DoorEvent,
    modifier: Modifier = Modifier,
) {
    val doorPosition = doorEvent.doorPosition ?: DoorPosition.UNKNOWN
    val colorSet = LocalDoorStatusColorScheme.current.doorColorSet(isStale = false)
    val doorColor = when (doorEvent.doorColorState()) {
        DoorColorState.OPEN -> colorSet.open
        DoorColorState.CLOSED -> colorSet.closed
        DoorColorState.UNKNOWN -> colorSet.unknown
    }
    val cardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        disabledContentColor = MaterialTheme.colorScheme.onSurface,
    )
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier,
        colors = cardColors,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = doorPosition.toFriendlyName(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
                GarageIcon(
                    doorPosition = doorPosition,
                    modifier = Modifier.size(84.dp),
                    static = true,
                    color = doorColor,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val eventTimeSeconds = doorEvent.lastChangeTimeSeconds
                val date = eventTimeSeconds?.toFriendlyDate()
                val time = eventTimeSeconds?.toFriendlyTime()
                Text(
                    text = "$time",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "$date",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                )
                val eventDuration by rememberDurationSince(
                    eventTimeSeconds?.let { Instant.ofEpochSecond(it) },
                )
                Text(
                    text = "${eventDuration.toFriendlyDuration()} ago",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private fun DoorPosition.toFriendlyName(): String =
    when (this) {
        DoorPosition.OPEN -> "Open"
        DoorPosition.CLOSED -> "Closed"
        DoorPosition.UNKNOWN -> "Unknown"
        DoorPosition.OPENING -> "Opening"
        DoorPosition.OPENING_TOO_LONG -> "Opening (Taking too long)"
        DoorPosition.OPEN_MISALIGNED -> "Open (Misaligned)"
        DoorPosition.CLOSING -> "Closing"
        DoorPosition.CLOSING_TOO_LONG -> "Closing (Taking too long)"
        DoorPosition.ERROR_SENSOR_CONFLICT -> "Error: Sensor Conflict"
    }

@Preview(showBackground = true)
@Composable
fun DoorStatusCardPreview() {
    Surface(modifier = Modifier.fillMaxWidth().height(300.dp)) {
        DoorStatusCard(
            demoDoorEvents.firstOrNull(),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RecentDoorEventListItemPreview() {
    RecentDoorEventListItem(demoDoorEvents[1])
}

/**
 * Tinted icon that gracefully degrades to a tinted square in screenshot tests.
 * The clock_icon and calendar_icon vector drawables fail to load under
 * Layoutlib (`Only VectorDrawables and rasterized asset types are supported`),
 * which collapses the entire DoorStatusCard render to 1×1. Production code
 * is unaffected — `LocalInspectionMode` is only true during preview rendering.
 */
@Composable
private fun PreviewSafeIcon(
    @DrawableRes id: Int,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    if (LocalInspectionMode.current) {
        Box(modifier = modifier.background(tint.copy(alpha = 0.3f)))
    } else {
        Image(
            painter = painterResource(id = id),
            contentDescription = contentDescription,
            modifier = modifier,
            colorFilter = ColorFilter.tint(tint),
        )
    }
}
