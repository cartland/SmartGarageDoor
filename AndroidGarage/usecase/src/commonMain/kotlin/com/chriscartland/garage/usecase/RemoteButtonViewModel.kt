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
import com.chriscartland.garage.domain.model.RemoteButtonState
import com.chriscartland.garage.domain.model.SnoozeAction
import com.chriscartland.garage.domain.model.SnoozeDurationUIOption
import com.chriscartland.garage.domain.model.SnoozeState
import com.chriscartland.garage.domain.model.toServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val SNOOZE_ACTION_RESET_DELAY_MS = 10_000L

interface RemoteButtonViewModel {
    val buttonState: StateFlow<RemoteButtonState>
    val snoozeState: StateFlow<SnoozeState>
    val snoozeAction: StateFlow<SnoozeAction>

    fun onButtonTap()

    fun resetButton()

    fun snoozeOpenDoorsNotifications(snoozeDuration: SnoozeDurationUIOption)

    fun fetchSnoozeStatus()
}

class DefaultRemoteButtonViewModel(
    observePushButtonStatus: ObservePushButtonStatusUseCase,
    private val observeDoorEvents: ObserveDoorEventsUseCase,
    private val dispatchers: DispatcherProvider,
    private val pushRemoteButtonUseCase: PushRemoteButtonUseCase,
    private val snoozeNotificationsUseCase: SnoozeNotificationsUseCase,
    private val fetchSnoozeStatusUseCase: FetchSnoozeStatusUseCase,
    private val observeSnoozeStateUseCase: ObserveSnoozeStateUseCase,
) : ViewModel(),
    RemoteButtonViewModel {
    private val stateMachine = ButtonStateMachine(
        pushButtonStatus = observePushButtonStatus(),
        doorPosition = observeDoorEvents.position(),
        onSubmit = ::submitButtonPress,
        scope = viewModelScope,
        dispatcher = dispatchers.io,
    )

    override val buttonState: StateFlow<RemoteButtonState> = stateMachine.state

    override val snoozeState: StateFlow<SnoozeState> = observeSnoozeStateUseCase()

    private val _snoozeAction = MutableStateFlow<SnoozeAction>(SnoozeAction.Idle)
    override val snoozeAction: StateFlow<SnoozeAction> = _snoozeAction

    private val currentDoorEvent = MutableStateFlow<DoorEvent?>(null)

    init {
        listenToDoorEvent()
    }

    private fun listenToDoorEvent() {
        viewModelScope.launch(dispatchers.io) {
            observeDoorEvents.current().collect {
                currentDoorEvent.value = it
            }
        }
    }

    override fun onButtonTap() {
        stateMachine.onTap()
    }

    override fun resetButton() {
        stateMachine.reset()
    }

    private fun submitButtonPress() {
        Logger.d { "submitButtonPress" }
        viewModelScope.launch(dispatchers.io) {
            when (
                val result = pushRemoteButtonUseCase(
                    buttonAckToken = createButtonAckToken(
                        currentTimeMillis = System.currentTimeMillis(),
                    ),
                )
            ) {
                is AppResult.Success -> { /* State machine tracks via pushButtonStatus flow */ }
                is AppResult.Error -> {
                    // The state machine is in SendingToServer but PushStatus.SENDING
                    // will never arrive because the UseCase failed before calling the
                    // repository. Reset to avoid being stuck forever.
                    stateMachine.reset()
                    when (result.error) {
                        ActionError.NotAuthenticated -> Logger.w { "Push failed — not authenticated" }
                        ActionError.MissingData -> Logger.w { "Push failed — missing data" }
                        ActionError.NetworkFailed -> Logger.w { "Push failed — network error" }
                    }
                }
            }
        }
    }

    override fun snoozeOpenDoorsNotifications(snoozeDuration: SnoozeDurationUIOption) {
        Logger.d { "snoozeOpenDoorsNotifications" }
        _snoozeAction.value = SnoozeAction.Sending
        viewModelScope.launch(dispatchers.io) {
            when (
                val result = snoozeNotificationsUseCase(
                    snoozeDurationHours = snoozeDuration.toServer().duration,
                    lastChangeTimeSeconds = currentDoorEvent.value?.lastChangeTimeSeconds,
                )
            ) {
                is AppResult.Success -> {
                    _snoozeAction.value = if (snoozeDuration == SnoozeDurationUIOption.None) {
                        SnoozeAction.Succeeded.Cleared
                    } else {
                        // Compute optimistic snooze end time from duration.
                        val durationSeconds = snoozeDuration.duration.inWholeSeconds
                        val optimisticEnd = System.currentTimeMillis() / 1000 + durationSeconds
                        SnoozeAction.Succeeded.Set(optimisticEnd)
                    }
                    fetchSnoozeStatusUseCase()
                    scheduleActionReset()
                }
                is AppResult.Error -> {
                    _snoozeAction.value = when (result.error) {
                        ActionError.NotAuthenticated -> SnoozeAction.Failed.NotAuthenticated
                        ActionError.MissingData -> SnoozeAction.Failed.MissingData
                        ActionError.NetworkFailed -> SnoozeAction.Failed.NetworkError
                    }
                    scheduleActionReset()
                }
            }
        }
    }

    override fun fetchSnoozeStatus() {
        viewModelScope.launch(dispatchers.io) {
            fetchSnoozeStatusUseCase()
        }
    }

    private fun scheduleActionReset() {
        viewModelScope.launch {
            delay(SNOOZE_ACTION_RESET_DELAY_MS)
            _snoozeAction.value = SnoozeAction.Idle
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
