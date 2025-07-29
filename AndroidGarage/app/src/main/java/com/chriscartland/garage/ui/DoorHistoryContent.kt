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

import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chriscartland.garage.applogger.AppLoggerViewModel
import com.chriscartland.garage.applogger.AppLoggerViewModelImpl
import com.chriscartland.garage.config.AppLoggerKeys
import com.chriscartland.garage.door.DoorEvent
import com.chriscartland.garage.door.DoorViewModel
import com.chriscartland.garage.door.DoorViewModelImpl
import com.chriscartland.garage.door.LoadingResult
import java.time.Instant

@Composable
fun DoorHistoryContent(
    modifier: Modifier = Modifier,
    viewModel: DoorViewModel = hiltViewModel<DoorViewModelImpl>(),
    appLoggerViewModel: AppLoggerViewModel = hiltViewModel<AppLoggerViewModelImpl>(),
) {
    val activity = LocalActivity.current
    val recentDoorEvents by viewModel.recentDoorEvents.collectAsState()
    DoorHistoryContent(
        recentDoorEvents = recentDoorEvents,
        modifier = modifier,
        onFetchRecentDoorEvents = {
            appLoggerViewModel.log(AppLoggerKeys.USER_FETCH_RECENT_DOOR)
            viewModel.fetchRecentDoorEvents()
        },
        onResetFcm = {
            if (activity != null) {
                viewModel.deregisterFcm(activity)
            } else {
                Log.e(TAG, "Activity is null, cannot deregister FCM")
            }
        },
    )
}

@Composable
fun DoorHistoryContent(
    modifier: Modifier = Modifier,
    recentDoorEvents: LoadingResult<List<DoorEvent>?>,
    onFetchRecentDoorEvents: () -> Unit = {},
    onResetFcm: () -> Unit = {},
) {
    val lastCheckInTime = recentDoorEvents.data?.firstOrNull()?.lastCheckInTimeSeconds
    DurationSince(lastCheckInTime?.let { Instant.ofEpochSecond(it) }) { duration ->
        val isOld = lastCheckInTime != null && duration > OLD_DURATION_FOR_DOOR_CHECK_IN
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isOld) {
                item {
                    OldLastCheckInBanner(
                        modifier = Modifier.fillMaxWidth(),
                        action = {
                            Log.e(
                                TAG,
                                "Trying to fix outdated info. Resetting FCM, and fetching data.",
                            )
                            onResetFcm()
                            onFetchRecentDoorEvents()
                        },
                    )
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
                        modifier = Modifier.fillMaxWidth(),
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
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
    ReportDrawnWhen { recentDoorEvents is LoadingResult.Complete }
}

@PreviewScreenSizes
@Composable
fun DoorHistoryContentPreview() {
    DoorHistoryContent(
        recentDoorEvents = LoadingResult.Complete(demoDoorEvents),
    )
}

private const val TAG = "DoorHistoryContent"
