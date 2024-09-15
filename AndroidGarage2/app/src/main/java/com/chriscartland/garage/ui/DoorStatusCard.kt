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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chriscartland.garage.R
import com.chriscartland.garage.model.DoorEvent
import com.chriscartland.garage.model.DoorPosition
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun DoorStatusCard(
    doorEvent: DoorEvent?,
    modifier: Modifier = Modifier,
) {
    val doorPosition = doorEvent?.doorPosition ?: DoorPosition.UNKNOWN

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = doorEvent?.lastChangeTimeSeconds?.toFriendlyDate() ?: ""
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = doorEvent?.lastChangeTimeSeconds?.toFriendlyTime() ?: ""
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
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = doorEvent?.message ?: "",
                style = MaterialTheme.typography.labelSmall,
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

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier,
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

private fun Long.toFriendlyDate(): String =
    Instant.ofEpochSecond(this)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

private fun Long.toFriendlyTime(): String? =
    Instant.ofEpochSecond(this)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM))

private fun DoorPosition.toImageResourceId(): Int = when (this) {
    DoorPosition.OPEN -> R.drawable.ic_garage_simple_open
    DoorPosition.CLOSED -> R.drawable.ic_garage_simple_closed
    DoorPosition.OPENING -> R.drawable.ic_garage_simple_moving
    DoorPosition.CLOSING -> R.drawable.ic_garage_simple_moving
    DoorPosition.OPENING_TOO_LONG -> R.drawable.ic_garage_simple_unknown
    DoorPosition.CLOSING_TOO_LONG -> R.drawable.ic_garage_simple_unknown
    DoorPosition.OPEN_MISALIGNED -> R.drawable.ic_garage_simple_unknown
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
