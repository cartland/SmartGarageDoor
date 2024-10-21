package com.chriscartland.garage.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier,
        colors = cardColors,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = doorEvent?.lastChangeTimeSeconds?.toFriendlyDate() ?: "",
                    color = cardColors.contentColor,
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = doorEvent?.lastChangeTimeSeconds?.toFriendlyTime() ?: "",
                    color = cardColors.contentColor,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Image(
                painter = painterResource(id = doorPosition.toImageResourceId()),
                contentDescription = "Door Status",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = doorPosition.toFriendlyName(),
                style = MaterialTheme.typography.titleMedium,
                color = cardColors.contentColor,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = doorEvent?.message ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = cardColors.contentColor,
            )

            val lastCheckInTime = doorEvent?.lastCheckInTimeSeconds
            val date = lastCheckInTime?.toFriendlyDate()
            val time = lastCheckInTime?.toFriendlyTime()
            Text(
                text = if (lastCheckInTime == null) {
                    "Missing check-in time"
                } else {
                    "Check-in: $date $time"
                },
                style = MaterialTheme.typography.labelSmall,
            )
            // Show time every second.
            var checkInDuration by remember { mutableStateOf<Duration?>(null) }
            LaunchedEffect(key1 = lastCheckInTime) {
                while (true) {
                    if (lastCheckInTime != null) {
                        checkInDuration = Duration.ofSeconds(
                            System.currentTimeMillis() / 1000 - lastCheckInTime,
                        )
                    }
                    delay(1000L) // Update every 1 second
                }
            }
            checkInDuration?.let { duration ->
                Text(
                    text = "Time since check-in: " + duration.toFriendlyDuration() ?: "",
                    style = MaterialTheme.typography.labelSmall,
                )
                // TODO: Nullable checkInDuration is var
                if (duration > Duration.ofMinutes(15)) {
                    Text(
                        text = "Warning: Time since check-in is over 15 minutes",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
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
        colors = CardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
            disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(id = doorPosition.toImageResourceId()),
                    contentDescription = "Door Status",
                    modifier = Modifier
                        .height(96.dp),
                )
                Text(
                    text = doorEvent.lastChangeTimeSeconds?.toFriendlyDate() ?: ""
                )
                Text(
                    text = doorEvent.lastChangeTimeSeconds?.toFriendlyTime() ?: ""
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = doorPosition.toFriendlyName(),
                    style = MaterialTheme.typography.titleMedium,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = doorEvent.message ?: "",
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
