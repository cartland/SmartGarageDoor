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

import com.chriscartland.garage.domain.model.DoorFcmState
import com.chriscartland.garage.domain.model.DoorFcmTopic
import com.chriscartland.garage.domain.repository.DoorFcmRepository

/**
 * Fake [DoorFcmRepository] for unit testing.
 *
 * Configure responses with `setX()` methods. Tracks register calls via
 * `registerCalls` (ADR-017 Rule 5 — call-list pattern). The `registerCount`
 * and `lastRegisteredTopic` accessors are convenience reads backed by the
 * list, so existing tests continue to work without changes.
 */
class FakeDoorFcmRepository : DoorFcmRepository {
    private var fetchStatusResult: DoorFcmState = DoorFcmState.Unknown
    private var registerResult: DoorFcmState = DoorFcmState.NotRegistered
    private var deregisterResult: DoorFcmState = DoorFcmState.NotRegistered

    private val _registerCalls = mutableListOf<DoorFcmTopic>()
    val registerCalls: List<DoorFcmTopic> get() = _registerCalls
    val registerCount: Int get() = _registerCalls.size
    val lastRegisteredTopic: DoorFcmTopic? get() = _registerCalls.lastOrNull()

    fun setFetchStatusResult(value: DoorFcmState) {
        fetchStatusResult = value
    }

    fun setRegisterResult(value: DoorFcmState) {
        registerResult = value
    }

    fun setDeregisterResult(value: DoorFcmState) {
        deregisterResult = value
    }

    override suspend fun fetchStatus(): DoorFcmState = fetchStatusResult

    override suspend fun registerDoor(fcmTopic: DoorFcmTopic): DoorFcmState {
        _registerCalls.add(fcmTopic)
        return registerResult
    }

    override suspend fun deregisterDoor(): DoorFcmState = deregisterResult
}
