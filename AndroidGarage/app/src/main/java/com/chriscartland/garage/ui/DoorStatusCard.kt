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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chriscartland.garage.door.DoorEvent
import com.chriscartland.garage.door.DoorPosition
import java.time.Instant

@Composable
fun DoorStatusCard(
    doorEvent: DoorEvent?,
    modifier: Modifier = Modifier,
    cardColors: CardColors = CardColors(
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
        disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ),
) {
    val doorPosition = doorEvent?.doorPosition ?: DoorPosition.UNKNOWN
    val lastChangeTimeSeconds = doorEvent?.lastChangeTimeSeconds
    val date = lastChangeTimeSeconds?.toFriendlyDate()
    val time = lastChangeTimeSeconds?.toFriendlyTime()
    CompositionLocalProvider(LocalContentColor provides cardColors.containerColor) {
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
                DurationSince(lastChangeTimeSeconds?.let { Instant.ofEpochSecond(it) }) { duration ->
                    Text(
                        text = duration.toFriendlyDuration(),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                doorPosition.Composable(
                    modifier = Modifier
                        .weight(1f),
                    container = true,
                )
                when (doorPosition) {
                    in listOf(
                        DoorPosition.UNKNOWN,
                        DoorPosition.OPENING_TOO_LONG,
                        DoorPosition.OPEN_MISALIGNED,
                        DoorPosition.CLOSING_TOO_LONG,
                        DoorPosition.ERROR_SENSOR_CONFLICT
                    ),
                        -> doorEvent?.message?.let {
                        if (it.isNotBlank()) {
                            Text(
                                text = doorEvent.message,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }

                    else -> { /* Nothing */
                    }
                }
                Text(
                    text = "Since $date - $time",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
fun RecentDoorEventListItem(
    doorEvent: DoorEvent,
    modifier: Modifier = Modifier,
) {
    val doorPosition = doorEvent.doorPosition ?: DoorPosition.UNKNOWN
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = doorPosition.toFriendlyName(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                )
                doorPosition.Composable(
                    modifier = Modifier
                        .height(96.dp),
                    static = true,
                    container = true,
                )
                when (doorPosition) {
                    in listOf(
                        DoorPosition.UNKNOWN,
                        DoorPosition.OPENING_TOO_LONG,
                        DoorPosition.OPEN_MISALIGNED,
                        DoorPosition.CLOSING_TOO_LONG,
                        DoorPosition.ERROR_SENSOR_CONFLICT
                    ),
                        -> doorEvent.message?.let {
                        if (it.isNotBlank()) {
                            Text(
                                text = doorEvent.message,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }

                    else -> { /* Nothing */
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

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
                DurationSince(eventTimeSeconds?.let { Instant.ofEpochSecond(it) }) { duration ->
                    Text(
                        text = "${duration.toFriendlyDuration()} ago",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun DoorPosition.Composable(
    modifier: Modifier = Modifier,
    static: Boolean = false,
    container: Boolean = false,
) {
    when (this) {
        DoorPosition.UNKNOWN -> Midway(modifier = modifier, container = container)
        DoorPosition.CLOSED -> Closed(modifier = modifier, container = container)
        DoorPosition.OPENING -> Opening(modifier = modifier, static = static, container = container)
        DoorPosition.OPENING_TOO_LONG -> Midway(modifier = modifier, container = container)
        DoorPosition.OPEN -> Open(modifier = modifier, container = container)
        DoorPosition.OPEN_MISALIGNED -> Open(modifier = modifier, container = container)
        DoorPosition.CLOSING -> Closing(modifier = modifier, static = static, container = container)
        DoorPosition.CLOSING_TOO_LONG -> Midway(modifier = modifier, container = container)
        DoorPosition.ERROR_SENSOR_CONFLICT -> Midway(modifier = modifier, container = container)
    }
}

private fun DoorPosition.toFriendlyName(): String = when (this) {
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
    DoorStatusCard(demoDoorEvents.firstOrNull())
}

@Preview(showBackground = true)
@Composable
fun RecentDoorEventListItemPreview() {
    RecentDoorEventListItem(demoDoorEvents[1])
}
