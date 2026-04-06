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
import com.chriscartland.garage.coroutines.DispatcherProvider
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.PushStatus
import com.chriscartland.garage.domain.model.RequestStatus
import com.chriscartland.garage.domain.model.SnoozeRequestStatus
import com.chriscartland.garage.domain.repository.DoorRepository
import com.chriscartland.garage.domain.repository.PushRepository
import com.chriscartland.garage.snoozenotifications.SnoozeDurationUIOption
import com.chriscartland.garage.snoozenotifications.toServer
import com.chriscartland.garage.usecase.PushRemoteButtonUseCase
import com.chriscartland.garage.usecase.SnoozeNotificationsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.time.delay
import me.tatarka.inject.annotations.Inject
import java.time.Duration
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
    // Remote button repository focused on sending the request over the Internet.
    private val pushRepository: PushRepository,
    // Watch the door status, because we consider the request delivered when the door moves.
    private val doorRepository: DoorRepository,
    private val dispatchers: DispatcherProvider,
    private val pushRemoteButtonUseCase: PushRemoteButtonUseCase,
    private val snoozeNotificationsUseCase: SnoozeNotificationsUseCase,
) : ViewModel(),
    RemoteButtonViewModel {
    // Listen to network events and door status updates.
    private val _requestStatus = MutableStateFlow(RequestStatus.NONE)
    override val requestStatus: StateFlow<RequestStatus> = _requestStatus

    private val _snoozeRequestStatus = MutableStateFlow(SnoozeRequestStatus.IDLE)
    override val snoozeRequestStatus: StateFlow<SnoozeRequestStatus> = _snoozeRequestStatus

    override val snoozeEndTimeSeconds: StateFlow<Long> = pushRepository.snoozeEndTimeSeconds

    private val currentDoorEvent = MutableStateFlow<DoorEvent?>(null)

    init {
        setupRequestStateMachine()
    }

    /**
     * State machine for [requestStatus].
     */
    private fun setupRequestStateMachine() {
        listenToButtonRepository()
        listenToDoorPosition()
        listenToDoorEvent()
        listenToRequestTimeouts()
        listenToSnoozeStatus()
    }

    /**
     * Listen to button pushes and update [requestStatus].
     *
     * When [PushStatus] becomes [PushStatus.SENDING], then [RequestStatus] is SENDING.
     *
     * When [PushStatus] becomes [PushStatus.IDLE]:
     *   - if the [RequestStatus] is SENDING, [RequestStatus] becomes SENT.
     *   - otherwise [RequestStatus] becomes NONE (reset state machine).
     */
    private fun listenToButtonRepository() {
        viewModelScope.launch(dispatchers.io) {
            pushRepository.pushButtonStatus.collect { sendStatus ->
                val old = _requestStatus.value
                _requestStatus.value = when (sendStatus) {
                    PushStatus.SENDING -> {
                        RequestStatus.SENDING
                    }

                    PushStatus.IDLE -> {
                        when (old) {
                            RequestStatus.SENDING -> RequestStatus.SENT
                            // All others -> NONE
                            RequestStatus.NONE -> RequestStatus.NONE
                            RequestStatus.SENT -> RequestStatus.NONE
                            RequestStatus.RECEIVED -> RequestStatus.NONE
                            RequestStatus.SENDING_TIMEOUT -> RequestStatus.NONE
                            RequestStatus.SENT_TIMEOUT -> RequestStatus.NONE
                        }
                    }
                }
                Logger.d { "ButtonRequestStateMachine network: old $old -> new ${_requestStatus.value.name}" }
            }
        }
    }

    /**
     * Listen to door position changes. Assume any change means the request was received.
     *
     * When the door moves:
     *   - If [RequestStatus] is NONE, ignore the door movement.
     *   - Otherwise, [RequestStatus] becomes [RequestStatus.RECEIVED].
     */
    private fun listenToDoorPosition() {
        viewModelScope.launch(dispatchers.io) {
            doorRepository.currentDoorPosition.collect {
                val old = _requestStatus.value
                when (_requestStatus.value) {
                    RequestStatus.NONE -> {} // Do nothing.
                    // All others -> RECEIVED
                    RequestStatus.SENDING -> _requestStatus.value = RequestStatus.RECEIVED

                    RequestStatus.SENDING_TIMEOUT -> _requestStatus.value = RequestStatus.RECEIVED

                    RequestStatus.SENT -> _requestStatus.value = RequestStatus.RECEIVED

                    RequestStatus.SENT_TIMEOUT -> _requestStatus.value = RequestStatus.RECEIVED

                    RequestStatus.RECEIVED -> _requestStatus.value = RequestStatus.RECEIVED
                }
                Logger.d { "ButtonRequestStateMachine door: old $old -> new ${_requestStatus.value.name}" }
            }
        }
    }

    private fun listenToDoorEvent() {
        viewModelScope.launch(dispatchers.io) {
            doorRepository.currentDoorEvent.collect {
                currentDoorEvent.value = it
            }
        }
    }

    /**
     * State machine to handle timeouts.
     *
     * Track coroutine job to ensure only 1 is running at a time.
     */
    private fun listenToRequestTimeouts() {
        var job: Job? = null // Job to track coroutines.
        val mutex = Mutex()
        viewModelScope.launch(dispatchers.io) {
            requestStatus.collect {
                when (it) {
                    RequestStatus.NONE -> {
                        job?.cancel()
                    } // Do nothing.
                    RequestStatus.SENDING -> {
                        mutex.lock()
                        job?.cancel()
                        job = viewModelScope.launch(dispatchers.io) {
                            delay(Duration.ofSeconds(10))
                            // Check to make sure state has not changed.
                            if (_requestStatus.value != RequestStatus.SENDING) {
                                Logger.e { "ButtonRequestStatus unexpectedly changed" }
                            } else {
                                _requestStatus.value = RequestStatus.SENDING_TIMEOUT
                            }
                        }
                        mutex.unlock()
                    }

                    RequestStatus.SENT -> {
                        mutex.lock()
                        job?.cancel()
                        job = viewModelScope.launch(dispatchers.io) {
                            delay(Duration.ofSeconds(10))
                            if (_requestStatus.value != RequestStatus.SENT) {
                                Logger.e { "ButtonRequestStatus unexpectedly changed" }
                            } else {
                                _requestStatus.value = RequestStatus.SENT_TIMEOUT
                            }
                        }
                        mutex.unlock()
                    }

                    RequestStatus.RECEIVED -> {
                        mutex.lock()
                        job?.cancel()
                        job = viewModelScope.launch(dispatchers.io) {
                            delay(Duration.ofSeconds(10))
                            if (_requestStatus.value != RequestStatus.RECEIVED) {
                                Logger.e { "ButtonRequestStatus unexpectedly changed" }
                            }
                            _requestStatus.value = RequestStatus.NONE
                        }
                        mutex.unlock()
                    }

                    RequestStatus.SENDING_TIMEOUT -> {
                        mutex.lock()
                        job?.cancel()
                        job = viewModelScope.launch(dispatchers.io) {
                            delay(Duration.ofSeconds(10))
                            if (_requestStatus.value != RequestStatus.SENDING_TIMEOUT) {
                                Logger.e { "ButtonRequestStatus unexpectedly changed" }
                            }
                            _requestStatus.value = RequestStatus.NONE
                        }
                        mutex.unlock()
                    }

                    RequestStatus.SENT_TIMEOUT -> {
                        mutex.lock()
                        job?.cancel()
                        job = viewModelScope.launch(dispatchers.io) {
                            delay(Duration.ofSeconds(10))
                            if (_requestStatus.value != RequestStatus.SENT_TIMEOUT) {
                                Logger.e { "ButtonRequestStatus unexpectedly changed" }
                            }
                            _requestStatus.value = RequestStatus.NONE
                        }
                        mutex.unlock()
                    }
                }
                Logger.d { "ButtonRequestStateMachine timeouts: ${_requestStatus.value.name}" }
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

    /**
     * Push the button.
     *
     * Requires an authenticated user.
     */
    override fun pushRemoteButton() {
        Logger.d { "pushRemoteButton" }
        viewModelScope.launch(dispatchers.io) {
            pushRemoteButtonUseCase(
                buttonAckToken = createButtonAckToken(Date()),
            )
        }
    }

    override fun snoozeOpenDoorsNotifications(snoozeDuration: SnoozeDurationUIOption) {
        Logger.d { "snoozeOpenDoorsNotifications" }
        viewModelScope.launch(dispatchers.io) {
            val result = snoozeNotificationsUseCase(
                snoozeDurationHours = snoozeDuration.toServer().duration,
                lastChangeTimeSeconds = currentDoorEvent.value?.lastChangeTimeSeconds,
            )
            if (!result) {
                Logger.e { "Snooze failed — not authenticated or no door event" }
            }
        }
    }

    override fun resetRemoteButton() {
        _requestStatus.value = RequestStatus.NONE
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
