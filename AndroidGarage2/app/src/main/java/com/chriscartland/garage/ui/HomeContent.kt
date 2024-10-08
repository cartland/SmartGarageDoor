package com.chriscartland.garage.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chriscartland.garage.APP_CONFIG
import com.chriscartland.garage.model.DoorEvent
import com.chriscartland.garage.repository.Result
import com.chriscartland.garage.repository.dataOrNull
import com.chriscartland.garage.viewmodel.DoorViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted

@Composable
fun HomeContent(
    modifier: Modifier = Modifier,
    viewModel: DoorViewModel = hiltViewModel(),
) {
    val currentDoorEvent = viewModel.currentDoorEvent.collectAsState()
    val recentDoorEvents = viewModel.recentDoorEvents.collectAsState()
    HomeContent(
        currentDoorEvent = currentDoorEvent.value,
        recentDoorEvents = recentDoorEvents.value,
        modifier = modifier,
        onFetchCurrentDoorEvent = { viewModel.fetchCurrentDoorEvent() },
        onFetchRecentDoorEvents = { viewModel.fetchRecentDoorEvents() },
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeContent(
    currentDoorEvent: Result<DoorEvent?>,
    recentDoorEvents: Result<List<DoorEvent>?>,
    modifier: Modifier = Modifier,
    onFetchCurrentDoorEvent: () -> Unit = {},
    onFetchRecentDoorEvents: () -> Unit = {},
) {
    // Manage permission state.
    val notificationPermissionState = rememberNotificationPermissionState()
    var permissionRequestCount by remember { mutableIntStateOf(0) }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Add a card at the top if the notification permission is not granted.
        if (!notificationPermissionState.status.isGranted) {
            item {
                ErrorRequestCard(
                    text = notificationJustificationText(permissionRequestCount),
                    buttonText = "Allow",
                    onClick = {
                        permissionRequestCount++
                        notificationPermissionState.launchPermissionRequest()
                    },
                )
            }
        } else {
            // Snooze notifications.
            if (APP_CONFIG.snoozeNotificationsOption) {
                item {
                    SnoozeNotificationCard(
                        text = "Snooze notifications",
                        snoozeText = "Snooze",
                        saveText = "Save",
                    )
                }
            }
        }

        // If the current event is loading, show a loading indicator.
        if (currentDoorEvent is Result.Loading) {
            item {
                Text(text = "Loading...")
            }
        }
        // If the current event had an error, show an error card.
        if (currentDoorEvent is Result.Error) {
            item {
                ErrorRequestCard(
                    text = "Error fetching current door event: " +
                            currentDoorEvent.exception.toString().take(500),
                    buttonText = "Retry",
                    onClick = { onFetchCurrentDoorEvent() },
                )
            }
        }
        // Show the current door event.
        item {
            DoorStatusCard(
                doorEvent = currentDoorEvent.dataOrNull(),
                modifier = Modifier
                    .clickable { onFetchCurrentDoorEvent() }, // Fetch on click.
            )
        }

        // If the recent events are loading, show a loading indicator.
        if (recentDoorEvents is Result.Loading) {
            item {
                Text(text = "Loading...")
            }
        }
        // If the recent events had an error, show an error card.
        if (recentDoorEvents is Result.Error) {
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
        items(recentDoorEvents.dataOrNull() ?: emptyList()) { item ->
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
fun HomeContentPreview() {
    HomeContent(
        currentDoorEvent = Result.Complete(demoDoorEvents.firstOrNull()),
        recentDoorEvents = Result.Complete(demoDoorEvents),
    )
}
