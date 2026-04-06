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

package com.chriscartland.garage.remotebutton

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
import com.chriscartland.garage.domain.repository.PushRepository
import com.chriscartland.garage.usecase.PushRemoteButtonUseCase
import com.chriscartland.garage.usecase.RemoteButtonStateMachine
import com.chriscartland.garage.usecase.SnoozeNotificationsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import java.util.Date

interface RemoteButtonViewModel {
    val requestStatus: StateFlow<RequestStatus>
    val snoozeRequestStatus: StateFlow<SnoozeRequestStatus>
    val snoozeEndTimeSeconds: StateFlow<Long>

    fun pushRemoteButton()

    fun resetRemoteButton()

    fun snoozeOpenDoorsNotifications(snoozeDuration: SnoozeDurationUIOption)

    fun fetchSnoozeEndTimeSeconds()
}

@Inject
class DefaultRemoteButtonViewModel(
    private val pushRepository: PushRepository,
    private val doorRepository: DoorRepository,
    private val dispatchers: DispatcherProvider,
    private val pushRemoteButtonUseCase: PushRemoteButtonUseCase,
    private val snoozeNotificationsUseCase: SnoozeNotificationsUseCase,
) : ViewModel(),
    RemoteButtonViewModel {
    private val stateMachine = RemoteButtonStateMachine(
        pushButtonStatus = pushRepository.pushButtonStatus,
        doorPosition = doorRepository.currentDoorPosition,
        scope = viewModelScope,
        dispatcher = dispatchers.io,
    )

    override val requestStatus: StateFlow<RequestStatus> = stateMachine.requestStatus

    private val _snoozeRequestStatus = MutableStateFlow(SnoozeRequestStatus.IDLE)
    override val snoozeRequestStatus: StateFlow<SnoozeRequestStatus> = _snoozeRequestStatus

    override val snoozeEndTimeSeconds: StateFlow<Long> = pushRepository.snoozeEndTimeSeconds

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
            pushRepository.snoozeRequestStatus.collect {
                _snoozeRequestStatus.value = it
            }
        }
    }

    override fun pushRemoteButton() {
        Logger.d { "pushRemoteButton" }
        viewModelScope.launch(dispatchers.io) {
            when (
                val result = pushRemoteButtonUseCase(
                    buttonAckToken = createButtonAckToken(Date()),
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
                is AppResult.Success -> pushRepository.fetchSnoozeEndTimeSeconds()
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
            pushRepository.fetchSnoozeEndTimeSeconds()
        }
    }
}

/**
 * Create a button ack token.
 *
 * This token is created by the client so the server can acknowledge the remote button push.
 * The client can send the same token to the server multiple times and the server is
 * responsible for only processing the token once.
 * When the server receives a button press, it will respond with the token to the client.
 */
fun createButtonAckToken(now: Date): String {
    val humanReadable = java.text.SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", java.util.Locale.US).format(now)
    val timestampMillis = now.time
    val appVersion = "AppVersionTODO"
    val buttonAckTokenData = "android-$appVersion-$humanReadable-$timestampMillis"
    val re = Regex("[^a-zA-Z0-9-_.]")
    val filtered = re.replace(buttonAckTokenData, ".")
    return filtered
}
