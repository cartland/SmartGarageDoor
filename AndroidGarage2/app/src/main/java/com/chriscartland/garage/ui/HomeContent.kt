package com.chriscartland.garage.ui

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
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
import com.chriscartland.garage.model.DoorEvent
import com.chriscartland.garage.model.Result
import com.chriscartland.garage.model.dataOrNull
import com.chriscartland.garage.ui.theme.LocalDoorStatusColorScheme
import com.chriscartland.garage.ui.theme.doorButtonColors
import com.chriscartland.garage.ui.theme.doorCardColors
import com.chriscartland.garage.viewmodel.DoorViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted

@Composable
fun HomeContent(
    modifier: Modifier = Modifier,
    viewModel: DoorViewModel = hiltViewModel(),
) {
    val currentDoorEvent = viewModel.currentDoorEvent.collectAsState()
    HomeContent(
        currentDoorEvent = currentDoorEvent.value,
        modifier = modifier,
        onFetchCurrentDoorEvent = { viewModel.fetchCurrentDoorEvent() },
        onRemoteButtonClick = { viewModel.pushRemoteButton() }
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeContent(
    currentDoorEvent: Result<DoorEvent?>,
    modifier: Modifier = Modifier,
    onFetchCurrentDoorEvent: () -> Unit = {},
    onRemoteButtonClick: () -> Unit = {},
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
        val doorEvent = currentDoorEvent.dataOrNull()
        item {
            DoorStatusCard(
                doorEvent = doorEvent,
                modifier = Modifier
                    .clickable { onFetchCurrentDoorEvent() }, // Fetch on click.
                cardColors = doorCardColors(LocalDoorStatusColorScheme.current, doorEvent),
            )
        }

        item {
            RemoteButtonContent(
                onClick = {
                    Log.d("HomeContent", "Remote button clicked")
                    onRemoteButtonClick()
                },
                buttonColors = doorButtonColors(LocalDoorStatusColorScheme.current, doorEvent),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeContentPreview() {
    HomeContent(
        currentDoorEvent = Result.Complete(demoDoorEvents.firstOrNull()),
    )
}
