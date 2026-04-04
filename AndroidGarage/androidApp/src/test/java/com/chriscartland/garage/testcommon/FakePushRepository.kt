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

import com.chriscartland.garage.domain.model.PushStatus
import com.chriscartland.garage.domain.model.SnoozeRequestStatus
import com.chriscartland.garage.remotebutton.PushRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakePushRepository : PushRepository {
    private val _pushButtonStatus = MutableStateFlow(PushStatus.IDLE)
    override val pushButtonStatus: StateFlow<PushStatus> = _pushButtonStatus

    private val _snoozeRequestStatus = MutableStateFlow(SnoozeRequestStatus.IDLE)
    override val snoozeRequestStatus: StateFlow<SnoozeRequestStatus> = _snoozeRequestStatus

    private val _snoozeEndTimeSeconds = MutableStateFlow(0L)
    override val snoozeEndTimeSeconds: StateFlow<Long> = _snoozeEndTimeSeconds

    var pushCount = 0
        private set
    var lastIdToken: String? = null
        private set
    var snoozeCount = 0
        private set

    fun setPushStatus(status: PushStatus) {
        _pushButtonStatus.value = status
    }

    fun setSnoozeStatus(status: SnoozeRequestStatus) {
        _snoozeRequestStatus.value = status
    }

    fun setSnoozeEndTime(seconds: Long) {
        _snoozeEndTimeSeconds.value = seconds
    }

    override suspend fun push(
        idToken: String,
        buttonAckToken: String,
    ) {
        pushCount++
        lastIdToken = idToken
        _pushButtonStatus.value = PushStatus.SENDING
        _pushButtonStatus.value = PushStatus.IDLE
    }

    override suspend fun fetchSnoozeEndTimeSeconds() {
        // No-op in fake
    }

    override suspend fun snoozeOpenDoorsNotifications(
        snoozeDurationHours: String,
        idToken: String,
        snoozeEventTimestampSeconds: Long,
    ) {
        snoozeCount++
        _snoozeRequestStatus.value = SnoozeRequestStatus.SENDING
        _snoozeRequestStatus.value = SnoozeRequestStatus.IDLE
    }
}
