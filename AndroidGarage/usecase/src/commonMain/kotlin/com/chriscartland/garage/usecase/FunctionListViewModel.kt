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
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.GoogleIdToken
import com.chriscartland.garage.domain.model.SnoozeDurationUIOption
import com.chriscartland.garage.domain.model.toServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the Function List screen: a flat list of user-triggered actions that
 * also exist elsewhere in the app. Depends only on UseCases (Phase 43 rule —
 * no repositories, no other ViewModels).
 */
interface FunctionListViewModel {
    fun openOrCloseDoor()

    fun refreshDoorStatus()

    fun refreshDoorHistory()

    fun snoozeNotificationsForOneHour()

    fun signInWithGoogle(idToken: GoogleIdToken)

    fun signOut()
}

class DefaultFunctionListViewModel(
    private val pushRemoteButtonUseCase: PushRemoteButtonUseCase,
    private val fetchCurrentDoorEventUseCase: FetchCurrentDoorEventUseCase,
    private val fetchRecentDoorEventsUseCase: FetchRecentDoorEventsUseCase,
    private val snoozeNotificationsUseCase: SnoozeNotificationsUseCase,
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val observeDoorEventsUseCase: ObserveDoorEventsUseCase,
    private val dispatchers: DispatcherProvider,
    private val appVersion: String,
) : ViewModel(),
    FunctionListViewModel {
    // Cached so the snooze action can attach the latest door change time
    // without the UI having to thread it through.
    private val currentDoorEvent = MutableStateFlow<DoorEvent?>(null)

    init {
        viewModelScope.launch(dispatchers.io) {
            observeDoorEventsUseCase.current().collect { currentDoorEvent.value = it }
        }
    }

    override fun openOrCloseDoor() {
        Logger.d { "openOrCloseDoor" }
        viewModelScope.launch(dispatchers.io) {
            pushRemoteButtonUseCase(
                buttonAckToken = ButtonAckToken.create(
                    currentTimeMillis = System.currentTimeMillis(),
                    appVersion = appVersion,
                ),
            )
        }
    }

    override fun refreshDoorStatus() {
        Logger.d { "refreshDoorStatus" }
        viewModelScope.launch(dispatchers.io) { fetchCurrentDoorEventUseCase() }
    }

    override fun refreshDoorHistory() {
        Logger.d { "refreshDoorHistory" }
        viewModelScope.launch(dispatchers.io) { fetchRecentDoorEventsUseCase() }
    }

    override fun snoozeNotificationsForOneHour() {
        Logger.d { "snoozeNotificationsForOneHour" }
        viewModelScope.launch(dispatchers.io) {
            snoozeNotificationsUseCase(
                snoozeDurationHours = SnoozeDurationUIOption.OneHour.toServer().duration,
                lastChangeTimeSeconds = currentDoorEvent.value?.lastChangeTimeSeconds,
            )
        }
    }

    override fun signInWithGoogle(idToken: GoogleIdToken) {
        Logger.d { "signInWithGoogle" }
        viewModelScope.launch(dispatchers.io) {
            signInWithGoogleUseCase(idToken)
        }
    }

    override fun signOut() {
        Logger.d { "signOut" }
        viewModelScope.launch(dispatchers.io) {
            signOutUseCase()
        }
    }
}
