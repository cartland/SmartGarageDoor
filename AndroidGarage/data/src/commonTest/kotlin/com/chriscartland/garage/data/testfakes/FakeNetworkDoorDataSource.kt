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

package com.chriscartland.garage.data.testfakes

import com.chriscartland.garage.data.NetworkDoorDataSource
import com.chriscartland.garage.data.NetworkResult
import com.chriscartland.garage.domain.model.DoorEvent

class FakeNetworkDoorDataSource : NetworkDoorDataSource {
    var currentDoorEventResult: NetworkResult<DoorEvent> = NetworkResult.ConnectionFailed
    var recentDoorEventsResult: NetworkResult<List<DoorEvent>> = NetworkResult.ConnectionFailed

    var fetchCurrentCount = 0
        private set
    var fetchRecentCount = 0
        private set

    override suspend fun fetchCurrentDoorEvent(buildTimestamp: String): NetworkResult<DoorEvent> {
        fetchCurrentCount++
        return currentDoorEventResult
    }

    override suspend fun fetchRecentDoorEvents(
        buildTimestamp: String,
        count: Int,
    ): NetworkResult<List<DoorEvent>> {
        fetchRecentCount++
        return recentDoorEventsResult
    }
}
