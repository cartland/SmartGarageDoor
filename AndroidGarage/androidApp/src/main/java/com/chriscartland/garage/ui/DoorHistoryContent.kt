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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.touchlab.kermit.Logger
import com.chriscartland.garage.R
import com.chriscartland.garage.di.rememberAppComponent
import com.chriscartland.garage.domain.model.AppLoggerKeys
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.LoadingResult
import com.chriscartland.garage.presentation.demoDoorEvents
import com.chriscartland.garage.ui.history.HistoryContent
import com.chriscartland.garage.ui.history.HistoryMapper
import com.chriscartland.garage.viewmodel.DoorHistoryViewModel
import java.time.Instant
import java.time.ZoneId

@Composable
fun DoorHistoryContent(
    modifier: Modifier = Modifier,
    doorHistoryViewModel: DoorHistoryViewModel? = null,
) {
    val component = rememberAppComponent()
    val resolved = doorHistoryViewModel ?: viewModel { component.doorHistoryViewModel }
    val recentDoorEvents by resolved.recentDoorEvents.collectAsState()
    val isCheckInStale by resolved.isCheckInStale.collectAsState()
    // `now` is driven by the VM's LiveClock-backed StateFlow (1s tick) —
    // `rememberLiveNow()` no longer exists; the ticker is owned by the
    // UseCase layer and lives across the app, not per-Composable.
    val nowEpochSeconds by resolved.nowEpochSeconds.collectAsState()
    val now = remember(nowEpochSeconds) { Instant.ofEpochSecond(nowEpochSeconds) }
    DoorHistoryContent(
        recentDoorEvents = recentDoorEvents,
        isCheckInStale = isCheckInStale,
        now = now,
        zone = ZoneId.systemDefault(),
        modifier = modifier,
        onFetchRecentDoorEvents = {
            resolved.log(AppLoggerKeys.USER_FETCH_RECENT_DOOR)
            resolved.fetchRecentDoorEvents()
        },
        onResetFcm = {
            resolved.deregisterFcm()
        },
    )
}

@Composable
fun DoorHistoryContent(
    recentDoorEvents: LoadingResult<List<DoorEvent>?>,
    now: Instant,
    zone: ZoneId,
    modifier: Modifier = Modifier,
    isCheckInStale: Boolean = false,
    onFetchRecentDoorEvents: () -> Unit = {},
    onResetFcm: () -> Unit = {},
) {
    val days = remember(recentDoorEvents.data, now, zone) {
        HistoryMapper.toHistoryDays(
            events = recentDoorEvents.data ?: emptyList(),
            now = now,
            zone = zone,
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (isCheckInStale) {
            ErrorCard(
                text = stringResource(R.string.home_history_stale_check_in_error),
                buttonText = stringResource(R.string.home_history_retry_button),
                onClick = {
                    Logger.e { "Trying to fix outdated info. Resetting FCM, and fetching data." }
                    onResetFcm()
                    onFetchRecentDoorEvents()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            )
        }
        if (recentDoorEvents is LoadingResult.Error) {
            ErrorCard(
                text = stringResource(
                    R.string.home_history_fetch_error,
                    recentDoorEvents.exception.toString().take(500),
                ),
                buttonText = stringResource(R.string.home_history_retry_button),
                onClick = { onFetchRecentDoorEvents() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            )
        }
        HistoryContent(
            days = days,
            zone = zone,
            isRefreshing = recentDoorEvents is LoadingResult.Loading,
            onRefresh = onFetchRecentDoorEvents,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    ReportDrawnWhen { recentDoorEvents is LoadingResult.Complete }
}

@PreviewScreenSizes
@Composable
fun DoorHistoryContentPreview() {
    Surface(modifier = Modifier.fillMaxSize()) {
        DoorHistoryContent(
            recentDoorEvents = LoadingResult.Complete(demoDoorEvents),
            now = Instant.parse("2026-04-29T10:27:00Z"),
            zone = ZoneId.of("UTC"),
        )
    }
}
