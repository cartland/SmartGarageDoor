package com.chriscartland.garage.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chriscartland.garage.door.DoorEvent
import com.chriscartland.garage.door.DoorViewModelImpl
import com.chriscartland.garage.door.LoadingResult
import java.time.Duration
import java.time.Instant

@Composable
fun DoorHistoryContent(
    modifier: Modifier = Modifier,
    viewModel: DoorViewModelImpl = hiltViewModel(),
) {
    val recentDoorEvents = viewModel.recentDoorEvents.collectAsState()
    DoorHistoryContent(
        recentDoorEvents = recentDoorEvents.value,
        modifier = modifier,
        onFetchRecentDoorEvents = { viewModel.fetchRecentDoorEvents() },
    )
}

@Composable
fun DoorHistoryContent(
    modifier: Modifier = Modifier,
    recentDoorEvents: LoadingResult<List<DoorEvent>?>,
    onFetchRecentDoorEvents: () -> Unit = {},
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            val lastCheckInTime = recentDoorEvents.data?.get(0)?.lastCheckInTimeSeconds
            DurationSince(lastCheckInTime?.let { Instant.ofEpochSecond(lastCheckInTime) }) { duration ->
                Text(
                    text = ("Time since check-in: " + duration.toFriendlyDuration()) ?: "",
                    style = MaterialTheme.typography.labelSmall,
                )
                if (duration > Duration.ofMinutes(15)) {
                    Text(
                        text = "Warning: Time since check-in is over 15 minutes",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        // If the recent events are loading, show a loading indicator.
        if (recentDoorEvents is LoadingResult.Loading) {
            item {
                Text(text = "Loading...")
            }
        }
        // If the recent events had an error, show an error card.
        if (recentDoorEvents is LoadingResult.Error) {
            item {
                ErrorRequestCard(
                    text = "Error fetching recent door events:" +
                            recentDoorEvents.exception.toString().take(500),
                    buttonText = "Retry",
                    onClick = { onFetchRecentDoorEvents() },
                )
            }
        }
        // Show the recent door events.
        items(recentDoorEvents.data ?: emptyList()) { item ->
            RecentDoorEventListItem(
                doorEvent = item,
                modifier = Modifier
                    .clickable { onFetchRecentDoorEvents() }, // Fetch on click.
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DoorHistoryContentPreview() {
    DoorHistoryContent(
        recentDoorEvents = LoadingResult.Complete(demoDoorEvents),
    )
}
