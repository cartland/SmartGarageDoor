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

package com.chriscartland.garage.usecase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.chriscartland.garage.domain.coroutines.DispatcherProvider
import com.chriscartland.garage.domain.model.ActionError
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.RequestStatus
import com.chriscartland.garage.domain.model.SnoozeDurationUIOption
import com.chriscartland.garage.domain.model.SnoozeRequestStatus
import com.chriscartland.garage.domain.model.toServer
import com.chriscartland.garage.domain.repository.DoorRepository
import com.chriscartland.garage.domain.repository.RemoteButtonRepository
import com.chriscartland.garage.domain.repository.SnoozeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

interface RemoteButtonViewModel {
    val requestStatus: StateFlow<RequestStatus>
    val snoozeRequestStatus: StateFlow<SnoozeRequestStatus>
    val snoozeEndTimeSeconds: StateFlow<Long>

    fun pushRemoteButton()

    fun resetRemoteButton()

    fun snoozeOpenDoorsNotifications(snoozeDuration: SnoozeDurationUIOption)

    fun fetchSnoozeEndTimeSeconds()
}

class DefaultRemoteButtonViewModel(
    private val remoteButtonRepository: RemoteButtonRepository,
    private val snoozeRepository: SnoozeRepository,
    private val doorRepository: DoorRepository,
    private val dispatchers: DispatcherProvider,
    private val pushRemoteButtonUseCase: PushRemoteButtonUseCase,
    private val snoozeNotificationsUseCase: SnoozeNotificationsUseCase,
) : ViewModel(),
    RemoteButtonViewModel {
    private val stateMachine = RemoteButtonStateMachine(
        pushButtonStatus = remoteButtonRepository.pushButtonStatus,
        doorPosition = doorRepository.currentDoorPosition,
        scope = viewModelScope,
        dispatcher = dispatchers.io,
    )

    override val requestStatus: StateFlow<RequestStatus> = stateMachine.requestStatus

    private val _snoozeRequestStatus = MutableStateFlow(SnoozeRequestStatus.IDLE)
    override val snoozeRequestStatus: StateFlow<SnoozeRequestStatus> = _snoozeRequestStatus

    override val snoozeEndTimeSeconds: StateFlow<Long> = snoozeRepository.snoozeEndTimeSeconds

    private val currentDoorEvent = MutableStateFlow<DoorEvent?>(null)

    init {
        listenToDoorEvent()
        listenToSnoozeStatus()
    }

    private fun listenToDoorEvent() {
        viewModelScope.launch(dispatchers.io) {
            doorRepository.currentDoorEvent.collect {
                currentDoorEvent.value = it
            }
        }
    }

    private fun listenToSnoozeStatus() {
        viewModelScope.launch(dispatchers.io) {
            snoozeRepository.snoozeRequestStatus.collect {
                _snoozeRequestStatus.value = it
            }
        }
    }

    override fun pushRemoteButton() {
        Logger.d { "pushRemoteButton" }
        viewModelScope.launch(dispatchers.io) {
            when (
                val result = pushRemoteButtonUseCase(
                    buttonAckToken = createButtonAckToken(
                        currentTimeMillis = System.currentTimeMillis(),
                    ),
                )
            ) {
                is AppResult.Success -> { /* State machine tracks via pushButtonStatus flow */ }
                is AppResult.Error -> when (result.error) {
                    ActionError.NotAuthenticated -> Logger.w { "Push failed — not authenticated" }
                    ActionError.MissingData -> Logger.w { "Push failed — missing data" }
                }
            }
        }
    }

    override fun snoozeOpenDoorsNotifications(snoozeDuration: SnoozeDurationUIOption) {
        Logger.d { "snoozeOpenDoorsNotifications" }
        viewModelScope.launch(dispatchers.io) {
            when (
                val result = snoozeNotificationsUseCase(
                    snoozeDurationHours = snoozeDuration.toServer().duration,
                    lastChangeTimeSeconds = currentDoorEvent.value?.lastChangeTimeSeconds,
                )
            ) {
                is AppResult.Success -> snoozeRepository.fetchSnoozeEndTimeSeconds()
                is AppResult.Error -> when (result.error) {
                    ActionError.NotAuthenticated ->
                        Logger.e { "Snooze failed — not authenticated" }
                    ActionError.MissingData ->
                        Logger.e { "Snooze failed — no door event timestamp" }
                }
            }
        }
    }

    override fun resetRemoteButton() {
        stateMachine.reset()
    }

    override fun fetchSnoozeEndTimeSeconds() {
        viewModelScope.launch(dispatchers.io) {
            snoozeRepository.fetchSnoozeEndTimeSeconds()
        }
    }
}

/**
 * Create a button ack token from the current time.
 *
 * This token is created by the client so the server can acknowledge the remote button push.
 * The client can send the same token to the server multiple times and the server is
 * responsible for only processing the token once.
 */
fun createButtonAckToken(currentTimeMillis: Long): String {
    val appVersion = "AppVersionTODO"
    val buttonAckTokenData = "android-$appVersion-$currentTimeMillis"
    val re = Regex("[^a-zA-Z0-9-_.]")
    val filtered = re.replace(buttonAckTokenData, ".")
    return filtered
}
