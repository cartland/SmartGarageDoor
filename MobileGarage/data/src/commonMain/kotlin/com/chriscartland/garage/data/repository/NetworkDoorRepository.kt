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

package com.chriscartland.garage.data.repository

import co.touchlab.kermit.Logger
import com.chriscartland.garage.data.LocalDoorDataSource
import com.chriscartland.garage.data.NetworkDoorDataSource
import com.chriscartland.garage.data.NetworkResult
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorEventPage
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.FetchError
import com.chriscartland.garage.domain.model.PaginationState
import com.chriscartland.garage.domain.repository.DoorRepository
import com.chriscartland.garage.domain.repository.ServerConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class NetworkDoorRepository(
    private val localDoorDataSource: LocalDoorDataSource,
    private val networkDoorDataSource: NetworkDoorDataSource,
    private val serverConfigRepository: ServerConfigRepository,
    private val recentEventCount: Int,
    externalScope: CoroutineScope,
) : DoorRepository {
    override val currentDoorPosition: Flow<DoorPosition>
        get() =
            localDoorDataSource.currentDoorEvent
                .map {
                    it?.doorPosition ?: DoorPosition.UNKNOWN
                }.distinctUntilChanged()

    // ADR-022: repository-owned StateFlow backed by an always-on collector
    // over the Room flow. `stateIn(WhileSubscribed(5s))` is explicitly banned
    // — it causes Room queries to restart and drops subscribers' state on a
    // configuration change.
    private val _currentDoorEvent = MutableStateFlow<DoorEvent?>(null)
    override val currentDoorEvent: StateFlow<DoorEvent?> = _currentDoorEvent

    // Same StateFlow + always-on collector pattern as currentDoorEvent.
    // Exposing this as StateFlow lets `DoorHistoryViewModel` synchronously
    // read `.value` to seed its initial loading-result with the cached
    // events list, avoiding a one-frame `Loading(emptyList())` render on
    // every fresh screen entry.
    private val _recentDoorEvents = MutableStateFlow<List<DoorEvent>>(emptyList())
    override val recentDoorEvents: StateFlow<List<DoorEvent>> = _recentDoorEvents

    // ADR-022: repo-owned state-y pagination cursor. The token is server state
    // tied to the cache contents, so it lives next to the cache (not the VM).
    private val _paginationState = MutableStateFlow(PaginationState.Initial)
    override val paginationState: StateFlow<PaginationState> = _paginationState

    init {
        externalScope.launch {
            localDoorDataSource.currentDoorEvent.collect { event ->
                _currentDoorEvent.value = event
                Logger.i { "currentDoorEvent <- $event (source=local)" }
            }
        }
        externalScope.launch {
            localDoorDataSource.recentDoorEvents.collect { events ->
                _recentDoorEvents.value = events
            }
        }
    }

    override suspend fun fetchBuildTimestampCached(): String? {
        val cached = serverConfigRepository.serverConfig.value
            ?: serverConfigRepository.fetchServerConfig()
        return cached?.buildTimestamp
    }

    override suspend fun insertDoorEvent(doorEvent: DoorEvent) {
        Logger.d { "Inserting DoorEvent: $doorEvent" }
        localDoorDataSource.insertDoorEvent(doorEvent)
    }

    override suspend fun fetchCurrentDoorEvent(): AppResult<DoorEvent, FetchError> {
        val buildTimestamp = fetchBuildTimestampCached()
        if (buildTimestamp == null) {
            Logger.e { "Server config is null" }
            return AppResult.Error(FetchError.NotReady)
        }
        return when (val result = networkDoorDataSource.fetchCurrentDoorEvent(buildTimestamp)) {
            is NetworkResult.Success -> {
                Logger.d { "Success: ${result.data}" }
                localDoorDataSource.insertDoorEvent(result.data)
                AppResult.Success(result.data)
            }
            is NetworkResult.HttpError -> {
                Logger.e { "HTTP ${result.code} fetching current door event" }
                AppResult.Error(FetchError.NetworkFailed)
            }
            NetworkResult.ConnectionFailed -> {
                Logger.e { "Connection failed fetching current door event" }
                AppResult.Error(FetchError.NetworkFailed)
            }
        }
    }

    override suspend fun fetchRecentDoorEvents(): AppResult<List<DoorEvent>, FetchError> {
        val buildTimestamp = fetchBuildTimestampCached()
        if (buildTimestamp == null) {
            Logger.e { "Server config is null" }
            return AppResult.Error(FetchError.NotReady)
        }
        return when (
            val result = networkDoorDataSource.fetchDoorEventPage(
                buildTimestamp = buildTimestamp,
                pageSize = recentEventCount,
                pageToken = null,
            )
        ) {
            is NetworkResult.Success -> {
                Logger.d { "Success: ${result.data.events.size} events" }
                // First page / pull-to-refresh: replace the cache and reset paging.
                localDoorDataSource.replaceDoorEvents(result.data.events)
                _paginationState.value = result.data.toPaginationState()
                AppResult.Success(result.data.events)
            }
            is NetworkResult.HttpError -> {
                Logger.e { "HTTP ${result.code} fetching recent door events" }
                AppResult.Error(FetchError.NetworkFailed)
            }
            NetworkResult.ConnectionFailed -> {
                Logger.e { "Connection failed fetching recent door events" }
                AppResult.Error(FetchError.NetworkFailed)
            }
        }
    }

    override suspend fun fetchOlderDoorEvents(): AppResult<List<DoorEvent>, FetchError> {
        val current = _paginationState.value
        val token = current.nextPageToken
        if (token == null || current.isLoadingMore) {
            // Nothing more to load, or a load is already in flight (reentrancy guard).
            return AppResult.Success(emptyList())
        }
        val buildTimestamp = fetchBuildTimestampCached()
        if (buildTimestamp == null) {
            Logger.e { "Server config is null" }
            return AppResult.Error(FetchError.NotReady)
        }
        _paginationState.value = current.copy(isLoadingMore = true)
        Logger.i { "paginationState <- isLoadingMore=true (loadMore)" }
        return when (
            val result = networkDoorDataSource.fetchDoorEventPage(
                buildTimestamp = buildTimestamp,
                pageSize = recentEventCount,
                pageToken = token,
            )
        ) {
            is NetworkResult.Success -> {
                Logger.d { "Older page: ${result.data.events.size} events" }
                localDoorDataSource.appendDoorEvents(result.data.events)
                _paginationState.value = result.data.toPaginationState()
                AppResult.Success(result.data.events)
            }
            is NetworkResult.HttpError -> {
                Logger.e { "HTTP ${result.code} fetching older door events" }
                _paginationState.value = current.copy(isLoadingMore = false)
                AppResult.Error(FetchError.NetworkFailed)
            }
            NetworkResult.ConnectionFailed -> {
                Logger.e { "Connection failed fetching older door events" }
                _paginationState.value = current.copy(isLoadingMore = false)
                AppResult.Error(FetchError.NetworkFailed)
            }
        }
    }

    private fun DoorEventPage.toPaginationState(): PaginationState =
        PaginationState(
            nextPageToken = nextPageToken,
            canLoadMore = hasMore && nextPageToken != null,
            isLoadingMore = false,
        )
}
