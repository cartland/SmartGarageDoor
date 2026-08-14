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

package com.chriscartland.garage.data

import com.chriscartland.garage.domain.model.DoorCommandVerdict

/**
 * Reads a verdict from the server's `doorCommand` endpoint.
 *
 * Deliberately NOT a method on [NetworkButtonDataSource]: that one presses the
 * button, this one only asks a question, and the whole point of the endpoint is
 * that asking and acting are different things reached by different code.
 */
interface NetworkDoorCommandDataSource {
    /**
     * @param command the wire value, `"open"` or `"close"`
     */
    suspend fun checkDoorCommand(
        command: String,
        remoteButtonPushKey: String,
        idToken: String,
    ): NetworkResult<DoorCommandVerdict>
}
