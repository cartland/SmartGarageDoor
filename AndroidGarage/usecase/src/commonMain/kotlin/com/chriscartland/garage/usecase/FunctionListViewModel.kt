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
import com.chriscartland.garage.domain.model.AppLoggerLimits
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.GoogleIdToken
import com.chriscartland.garage.domain.model.SnoozeDurationUIOption
import com.chriscartland.garage.domain.model.toServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Drives the Function List screen: a flat list of user-triggered actions that
 * also exist elsewhere in the app. Depends only on UseCases (Phase 43 rule —
 * no repositories, no other ViewModels).
 */
interface FunctionListViewModel {
    /**
     * Per-user access decision for the Function List feature.
     *
     * - `null` — not yet fetched, fetch failed, or signed out (gate closed).
     * - `false` — server says this user is NOT on the allowlist (gate closed).
     * - `true` — server says this user IS on the allowlist (gate open).
     *
     * **Tri-state is load-bearing.** Both `null` and `false` deny, but the
     * distinction must be preserved by any future migration. A naïve
     * conversion to `LoadingResult<Boolean>` collapses `null` into
     * `Loading(null)` and a "show buttons during loading" rendering would
     * silently open the gate at startup. Gate UI on `== true` only;
     * never on `!= false`. Full convention in `docs/FEATURE_FLAGS.md`.
     */
    val accessGranted: StateFlow<Boolean?>

    fun openOrCloseDoor()

    fun refreshDoorStatus()

    fun refreshDoorHistory()

    fun refreshSnoozeStatus()

    fun snoozeNotificationsForOneHour()

    fun signInWithGoogle(idToken: GoogleIdToken)

    fun signOut()

    fun clearDiagnostics()

    fun pruneAppLog()

    fun registerFcm()

    fun deregisterFcm()
}

class DefaultFunctionListViewModel(
    private val pushRemoteButtonUseCase: PushRemoteButtonUseCase,
    private val fetchCurrentDoorEventUseCase: FetchCurrentDoorEventUseCase,
    private val fetchRecentDoorEventsUseCase: FetchRecentDoorEventsUseCase,
    private val fetchSnoozeStatusUseCase: FetchSnoozeStatusUseCase,
    private val snoozeNotificationsUseCase: SnoozeNotificationsUseCase,
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val observeDoorEventsUseCase: ObserveDoorEventsUseCase,
    private val observeFeatureAccessUseCase: ObserveFeatureAccessUseCase,
    private val clearDiagnosticsUseCase: ClearDiagnosticsUseCase,
    private val pruneAppLogUseCase: PruneAppLogUseCase,
    private val registerFcmUseCase: RegisterFcmUseCase,
    private val deregisterFcmUseCase: DeregisterFcmUseCase,
    private val dispatchers: DispatcherProvider,
    private val appVersion: String,
) : ViewModel(),
    FunctionListViewModel {
    // Cached so the snooze action can attach the latest door change time
    // without the UI having to thread it through.
    private val currentDoorEvent = MutableStateFlow<DoorEvent?>(null)

    private val _accessGranted = MutableStateFlow<Boolean?>(null)
    override val accessGranted: StateFlow<Boolean?> = _accessGranted

    init {
        viewModelScope.launch(dispatchers.io) {
            observeDoorEventsUseCase.current().collect { currentDoorEvent.value = it }
        }
        viewModelScope.launch(dispatchers.io) {
            observeFeatureAccessUseCase.functionList().collect { _accessGranted.value = it }
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

    override fun refreshSnoozeStatus() {
        Logger.d { "refreshSnoozeStatus" }
        viewModelScope.launch(dispatchers.io) { fetchSnoozeStatusUseCase() }
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

    override fun clearDiagnostics() {
        Logger.d { "clearDiagnostics" }
        viewModelScope.launch(dispatchers.io) {
            clearDiagnosticsUseCase()
        }
    }

    override fun pruneAppLog() {
        Logger.d { "pruneAppLog" }
        viewModelScope.launch(dispatchers.io) {
            pruneAppLogUseCase(AppLoggerLimits.DEFAULT_PER_KEY_LIMIT)
        }
    }

    override fun registerFcm() {
        Logger.d { "registerFcm" }
        viewModelScope.launch(dispatchers.io) {
            registerFcmUseCase()
        }
    }

    override fun deregisterFcm() {
        Logger.d { "deregisterFcm" }
        viewModelScope.launch(dispatchers.io) {
            deregisterFcmUseCase()
        }
    }
}
