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

import androidx.compose.foundation.Image
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chriscartland.garage.R
import com.chriscartland.garage.door.DoorEvent
import com.chriscartland.garage.door.DoorPosition
import kotlinx.coroutines.delay
import java.time.Duration

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
    // Update time since last change every second.
    var lastChangeDuration by remember { mutableStateOf<Duration>(Duration.ZERO) }
    LaunchedEffect(key1 = lastChangeTimeSeconds) {
        while (true) {
            if (lastChangeTimeSeconds != null) {
                lastChangeDuration = Duration.ofSeconds(
                    System.currentTimeMillis() / 1000 - lastChangeTimeSeconds,
                )
            }
            delay(1000L) // Update every 1 second
        }
    }
    val date = lastChangeTimeSeconds?.toFriendlyDate()
    val time = lastChangeTimeSeconds?.toFriendlyTime()
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier,
        colors = cardColors,
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
                color = cardColors.contentColor,
            )
            Text(
                text = lastChangeDuration.toFriendlyDuration(),
                style = MaterialTheme.typography.labelSmall,
                color = cardColors.contentColor,
            )
            Image(
                painter = painterResource(id = doorPosition.toImageResourceId()),
                contentDescription = "Door Status",
                modifier = Modifier
                    .weight(1f)
            )
            Text(
                text = doorEvent?.message ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = cardColors.contentColor,
            )
            Text(
                text = "Since $date - $time",
                style = MaterialTheme.typography.labelSmall,
                color = cardColors.contentColor,
            )
        }
    }
}

@Composable
fun RecentDoorEventListItem(
    doorEvent: DoorEvent,
    modifier: Modifier = Modifier,
) {
    val doorPosition = doorEvent.doorPosition ?: DoorPosition.UNKNOWN
    val date = doorEvent.lastChangeTimeSeconds?.toFriendlyDate()
    val time = doorEvent.lastChangeTimeSeconds?.toFriendlyTime()
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
                Image(
                    painter = painterResource(id = doorPosition.toImageResourceId()),
                    contentDescription = "Door Status",
                    modifier = Modifier
                        .height(96.dp)
                )
                Text(
                    text = doorEvent?.message ?: "",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
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
            }
        }
    }
}

private fun DoorPosition.toImageResourceId(): Int = when (this) {
    DoorPosition.OPEN -> R.drawable.ic_garage_simple_open
    DoorPosition.CLOSED -> R.drawable.ic_garage_simple_closed
    DoorPosition.OPENING -> R.drawable.ic_garage_simple_moving
    DoorPosition.CLOSING -> R.drawable.ic_garage_simple_moving
    DoorPosition.OPENING_TOO_LONG -> R.drawable.ic_garage_simple_unknown
    DoorPosition.CLOSING_TOO_LONG -> R.drawable.ic_garage_simple_unknown
    DoorPosition.OPEN_MISALIGNED -> R.drawable.ic_garage_simple_open
    DoorPosition.ERROR_SENSOR_CONFLICT -> R.drawable.ic_garage_simple_unknown
    DoorPosition.UNKNOWN -> R.drawable.ic_garage_simple_unknown
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
