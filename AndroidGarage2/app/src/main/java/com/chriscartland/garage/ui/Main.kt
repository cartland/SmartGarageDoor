package com.chriscartland.garage.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.chriscartland.garage.model.generateDoorEventDemoData
import com.chriscartland.garage.ui.theme.AppTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Preview
@Composable
fun GarageApp() {
    AppTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
        ) { innerPadding ->
            Text(
                "Garage",
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}


@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DoorStatusCard(doorEvent: DoorEvent) {
    val doorPosition = doorEvent.doorPosition ?: DoorPosition.UNKNOWN

    val imageResource = when (doorPosition) {
        DoorPosition.OPEN -> R.drawable.baseline_houseboat_24
        DoorPosition.CLOSED -> R.drawable.baseline_house_24
        DoorPosition.OPENING -> R.drawable.baseline_house_siding_24
        DoorPosition.CLOSING -> R.drawable.baseline_house_siding_24
        DoorPosition.OPENING_TOO_LONG -> R.drawable.baseline_other_houses_24
        DoorPosition.CLOSING_TOO_LONG -> R.drawable.baseline_other_houses_24
        DoorPosition.OPEN_MISALIGNED -> R.drawable.baseline_other_houses_24
        DoorPosition.ERROR_SENSOR_CONFLICT -> R.drawable.baseline_other_houses_24
        DoorPosition.UNKNOWN -> R.drawable.baseline_other_houses_24
    }

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (doorEvent.lastChangeTimeSeconds == null) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "")
                    Spacer(modifier = Modifier.weight(1f))
                    Text(text = "")
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = Instant.ofEpochSecond(doorEvent.lastChangeTimeSeconds)
                            .atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = Instant.ofEpochSecond(doorEvent.lastChangeTimeSeconds)
                            .atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM))
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Image(
                painter = painterResource(id = imageResource),
                contentDescription = "Door Status",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "Door Status: ${doorPosition.name}", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
val demoDoorEvents = generateDoorEventDemoData()

@RequiresApi(Build.VERSION_CODES.O)
@Preview
@Composable
fun DoorStatusCardPreview() {
    LazyColumn {
        items(demoDoorEvents) { item ->
            DoorStatusCard(item)
        }
    }
}
