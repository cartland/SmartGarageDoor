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

import androidx.activity.compose.ReportDrawnWhen
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
                ErrorCard(
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
    ReportDrawnWhen { recentDoorEvents is LoadingResult.Complete }
}

@Preview(showBackground = true)
@Composable
fun DoorHistoryContentPreview() {
    DoorHistoryContent(
        recentDoorEvents = LoadingResult.Complete(demoDoorEvents),
    )
}
