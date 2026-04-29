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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.touchlab.kermit.Logger
import com.chriscartland.garage.di.rememberAppComponent
import com.chriscartland.garage.domain.model.AppLoggerKeys
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.LoadingResult
import com.chriscartland.garage.presentation.demoDoorEvents
import com.chriscartland.garage.ui.history.HistoryContent
import com.chriscartland.garage.ui.history.HistoryMapper
import com.chriscartland.garage.usecase.AppLoggerViewModel
import com.chriscartland.garage.usecase.DoorViewModel
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

@Composable
fun DoorHistoryContent(
    modifier: Modifier = Modifier,
    doorViewModel: DoorViewModel? = null,
    appLoggerViewModel: AppLoggerViewModel? = null,
) {
    val component = rememberAppComponent()
    val resolvedDoorViewModel = doorViewModel ?: viewModel { component.doorViewModel }
    val resolvedAppLoggerViewModel = appLoggerViewModel ?: viewModel { component.appLoggerViewModel }
    val recentDoorEvents by resolvedDoorViewModel.recentDoorEvents.collectAsState()
    val isCheckInStale by resolvedDoorViewModel.isCheckInStale.collectAsState()
    DoorHistoryContent(
        recentDoorEvents = recentDoorEvents,
        isCheckInStale = isCheckInStale,
        now = rememberLiveNow(),
        zone = ZoneId.systemDefault(),
        modifier = modifier,
        onFetchRecentDoorEvents = {
            resolvedAppLoggerViewModel.log(AppLoggerKeys.USER_FETCH_RECENT_DOOR)
            resolvedDoorViewModel.fetchRecentDoorEvents()
        },
        onResetFcm = {
            resolvedDoorViewModel.deregisterFcm()
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
            OldLastCheckInBanner(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                action = {
                    Logger.e { "Trying to fix outdated info. Resetting FCM, and fetching data." }
                    onResetFcm()
                    onFetchRecentDoorEvents()
                },
            )
        }
        if (recentDoorEvents is LoadingResult.Error) {
            ErrorCard(
                text = "Error fetching recent door events:" +
                    recentDoorEvents.exception.toString().take(500),
                buttonText = "Retry",
                onClick = { onFetchRecentDoorEvents() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            )
        }
        HistoryContent(
            days = days,
            isRefreshing = recentDoorEvents is LoadingResult.Loading,
            onRefresh = onFetchRecentDoorEvents,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    ReportDrawnWhen { recentDoorEvents is LoadingResult.Complete }
}

/**
 * Returns an [Instant] that updates every 30 seconds (so the most-recent
 * row's "X and counting" duration stays live without spamming
 * recomposition). Returns a fixed timestamp under [LocalInspectionMode] so
 * screenshot tests and IDE previews stay deterministic.
 */
@Composable
private fun rememberLiveNow(): Instant {
    if (LocalInspectionMode.current) {
        return remember { Instant.parse("2026-04-29T10:27:00Z") }
    }
    val state = produceState(initialValue = Instant.now()) {
        while (true) {
            delay(30_000L)
            value = Instant.now()
        }
    }
    return state.value
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
