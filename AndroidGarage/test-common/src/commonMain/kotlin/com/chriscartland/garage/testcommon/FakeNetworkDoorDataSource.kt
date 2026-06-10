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

package com.chriscartland.garage.testcommon

import com.chriscartland.garage.data.NetworkDoorDataSource
import com.chriscartland.garage.data.NetworkResult
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorEventPage

/**
 * Fake [NetworkDoorDataSource] for unit testing.
 *
 * Configure responses with `setX()` methods. Tracks each call via `*Calls`
 * lists (ADR-017 Rule 5 — call-list pattern), so tests can assert on the
 * exact arguments passed (build timestamps, page sizes, tokens). The `*Count`
 * accessors are convenience reads backed by the lists.
 */
class FakeNetworkDoorDataSource : NetworkDoorDataSource {
    data class FetchPageCall(
        val buildTimestamp: String,
        val pageSize: Int,
        val pageToken: String?,
    )

    private var currentDoorEventResult: NetworkResult<DoorEvent> = NetworkResult.ConnectionFailed
    private var doorEventPageResult: NetworkResult<DoorEventPage> = NetworkResult.ConnectionFailed

    // When set, the next fetchDoorEventPage call returns this instead, then clears.
    private var nextDoorEventPageResult: NetworkResult<DoorEventPage>? = null

    private val _fetchCurrentBuildTimestamps = mutableListOf<String>()
    val fetchCurrentBuildTimestamps: List<String> get() = _fetchCurrentBuildTimestamps
    val fetchCurrentCount: Int get() = _fetchCurrentBuildTimestamps.size

    private val _fetchPageCalls = mutableListOf<FetchPageCall>()
    val fetchPageCalls: List<FetchPageCall> get() = _fetchPageCalls
    val fetchPageCount: Int get() = _fetchPageCalls.size

    fun setCurrentDoorEventResult(value: NetworkResult<DoorEvent>) {
        currentDoorEventResult = value
    }

    fun setDoorEventPageResult(value: NetworkResult<DoorEventPage>) {
        doorEventPageResult = value
    }

    /** Arm the NEXT fetchDoorEventPage call to return [value] (e.g. an older page). */
    fun setNextDoorEventPageResult(value: NetworkResult<DoorEventPage>) {
        nextDoorEventPageResult = value
    }

    override suspend fun fetchCurrentDoorEvent(buildTimestamp: String): NetworkResult<DoorEvent> {
        _fetchCurrentBuildTimestamps.add(buildTimestamp)
        return currentDoorEventResult
    }

    override suspend fun fetchDoorEventPage(
        buildTimestamp: String,
        pageSize: Int,
        pageToken: String?,
    ): NetworkResult<DoorEventPage> {
        _fetchPageCalls.add(FetchPageCall(buildTimestamp = buildTimestamp, pageSize = pageSize, pageToken = pageToken))
        nextDoorEventPageResult?.let {
            nextDoorEventPageResult = null
            return it
        }
        return doorEventPageResult
    }
}
