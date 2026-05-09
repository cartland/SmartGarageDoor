/*
 * Copyright 2026 Chris Cartland. All rights reserved.
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

import com.chriscartland.garage.data.NetworkButtonHealthDataSource
import com.chriscartland.garage.data.NetworkResult
import com.chriscartland.garage.domain.model.ButtonHealth
import com.chriscartland.garage.domain.model.ButtonHealthState

/**
 * Fake [NetworkButtonHealthDataSource] for repository + propagation tests.
 *
 * Configure the response with [setResult]; defaults to a neutral
 * `Success(UNKNOWN, null)` so a never-set fake doesn't crash.
 */
class FakeNetworkButtonHealthDataSource : NetworkButtonHealthDataSource {
    private var result: NetworkResult<ButtonHealth> =
        NetworkResult.Success(ButtonHealth(ButtonHealthState.UNKNOWN, null))

    fun setResult(value: NetworkResult<ButtonHealth>) {
        result = value
    }

    override suspend fun fetchButtonHealth(
        buildTimestamp: String,
        remoteButtonPushKey: String,
        idToken: String,
    ): NetworkResult<ButtonHealth> = result
}
