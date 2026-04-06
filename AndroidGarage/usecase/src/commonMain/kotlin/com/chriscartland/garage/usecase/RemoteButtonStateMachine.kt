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

import co.touchlab.kermit.Logger
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.PushStatus
import com.chriscartland.garage.domain.model.RequestStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * Pure state machine for remote button request tracking.
 *
 * Coordinates three input signals — push status, door position, and timeouts —
 * to produce a single [requestStatus] flow representing the current state of
 * a remote button press request.
 *
 * State transitions:
 * - NONE → SENDING (push network request starts)
 * - SENDING → SENT (push network request completes)
 * - SENDING/SENT → RECEIVED (door position changes)
 * - SENDING → SENDING_TIMEOUT (10s with no response)
 * - SENT → SENT_TIMEOUT (10s with no door movement)
 * - RECEIVED/SENDING_TIMEOUT/SENT_TIMEOUT → NONE (10s display timeout)
 */
class RemoteButtonStateMachine(
    pushButtonStatus: Flow<PushStatus>,
    doorPosition: Flow<DoorPosition>,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {
    private val _requestStatus = MutableStateFlow(RequestStatus.NONE)
    val requestStatus: StateFlow<RequestStatus> = _requestStatus

    init {
        listenToButtonStatus(pushButtonStatus)
        listenToDoorPosition(doorPosition)
        listenToRequestTimeouts()
    }

    fun reset() {
        _requestStatus.value = RequestStatus.NONE
    }

    /**
     * Listen to push status and update [requestStatus].
     *
     * When [PushStatus] becomes [PushStatus.SENDING], then [RequestStatus] is SENDING.
     *
     * When [PushStatus] becomes [PushStatus.IDLE]:
     *   - if the [RequestStatus] is SENDING, [RequestStatus] becomes SENT.
     *   - otherwise [RequestStatus] becomes NONE (reset state machine).
     */
    private fun listenToButtonStatus(pushButtonStatus: Flow<PushStatus>) {
        scope.launch(dispatcher) {
            pushButtonStatus.collect { sendStatus ->
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
    private fun listenToDoorPosition(doorPosition: Flow<DoorPosition>) {
        scope.launch(dispatcher) {
            doorPosition.collect {
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

    /**
     * State machine to handle timeouts.
     *
     * Track coroutine job to ensure only 1 is running at a time.
     */
    private fun listenToRequestTimeouts() {
        var job: Job? = null // Job to track coroutines.
        val mutex = Mutex()
        scope.launch(dispatcher) {
            requestStatus.collect {
                when (it) {
                    RequestStatus.NONE -> {
                        job?.cancel()
                    } // Do nothing.
                    RequestStatus.SENDING -> {
                        mutex.lock()
                        job?.cancel()
                        job = scope.launch(dispatcher) {
                            delay(timeoutMillis)
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
                        job = scope.launch(dispatcher) {
                            delay(timeoutMillis)
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
                        job = scope.launch(dispatcher) {
                            delay(timeoutMillis)
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
                        job = scope.launch(dispatcher) {
                            delay(timeoutMillis)
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
                        job = scope.launch(dispatcher) {
                            delay(timeoutMillis)
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

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 10_000L
    }
}
