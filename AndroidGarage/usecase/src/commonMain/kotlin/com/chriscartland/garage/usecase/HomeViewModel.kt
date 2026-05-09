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
import com.chriscartland.garage.domain.model.AppLoggerKeys
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.FetchError
import com.chriscartland.garage.domain.model.GoogleIdToken
import com.chriscartland.garage.domain.model.LoadingResult
import com.chriscartland.garage.domain.model.RemoteButtonState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Drives the Home screen — the canonical example for ADR-026
 * (one ViewModel per screen). Aggregates everything `HomeContent.kt`
 * needs from below, depending on UseCases only (Phase 43 rule).
 *
 * Replaces the previous direct use of `DoorViewModel` + `AuthViewModel` +
 * `RemoteButtonViewModel` + `AppLoggerViewModel` from `HomeContent.kt`.
 * Those VMs still exist and back the legacy multi-VM screens that haven't
 * yet been refactored to one-VM-per-screen (`DoorHistoryContent`,
 * `ProfileContent`).
 */
interface HomeViewModel {
    val authState: StateFlow<AuthState>

    val currentDoorEvent: StateFlow<LoadingResult<DoorEvent?>>

    /** True when the last check-in is older than the staleness threshold (11 min). */
    val isCheckInStale: StateFlow<Boolean>

    /**
     * Wall-clock time as epoch seconds, ticking on the [LiveClock] cadence
     * (10s by default). Pass-through of [LiveClock.nowEpochSeconds] (ADR-022).
     */
    val nowEpochSeconds: StateFlow<Long>

    val buttonState: StateFlow<RemoteButtonState>

    /**
     * Display state for the remote-button device's online/offline pill.
     * Per ADR-022, derived [Flow]; the Composable consumer collects via
     * [androidx.compose.runtime.collectAsState] with an initial value.
     */
    val buttonHealthDisplay: Flow<ButtonHealthDisplay>

    fun signInWithGoogle(idToken: GoogleIdToken)

    fun fetchCurrentDoorEvent()

    fun deregisterFcm()

    fun onButtonTap()

    fun log(key: String)
}

class DefaultHomeViewModel(
    observeDoorEvents: ObserveDoorEventsUseCase,
    observeAuthState: ObserveAuthStateUseCase,
    private val logAppEvent: LogAppEventUseCase,
    private val dispatchers: DispatcherProvider,
    private val fetchCurrentDoorEventUseCase: FetchCurrentDoorEventUseCase,
    private val deregisterFcmUseCase: DeregisterFcmUseCase,
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    private val pushRemoteButtonUseCase: PushRemoteButtonUseCase,
    private val checkInStalenessManager: CheckInStalenessManager,
    private val liveClock: LiveClock,
    override val buttonHealthDisplay: Flow<ButtonHealthDisplay>,
    private val appVersion: String,
    private val fetchOnInit: Boolean = true,
) : ViewModel(),
    HomeViewModel {
    // ADR-022: pass through the repository's StateFlow by reference — no mirror.
    override val authState: StateFlow<AuthState> = observeAuthState()

    // ADR-022: pass through LiveClock's StateFlow — no mirror.
    override val nowEpochSeconds: StateFlow<Long> = liveClock.nowEpochSeconds

    private val _currentDoorEvent =
        MutableStateFlow<LoadingResult<DoorEvent?>>(LoadingResult.Loading(null))
    override val currentDoorEvent: StateFlow<LoadingResult<DoorEvent?>> = _currentDoorEvent

    private val _isCheckInStale = MutableStateFlow(false)
    override val isCheckInStale: StateFlow<Boolean> = _isCheckInStale

    private val stateMachine = ButtonStateMachine(
        doorPosition = observeDoorEvents.position(),
        onSubmit = ::submitButtonPress,
        scope = viewModelScope,
        dispatcher = dispatchers.io,
    )
    override val buttonState: StateFlow<RemoteButtonState> = stateMachine.state

    init {
        Logger.d { "init" }
        viewModelScope.launch(dispatchers.io) {
            checkInStalenessManager.isCheckInStale.collect {
                _isCheckInStale.value = it
            }
        }
        viewModelScope.launch(dispatchers.io) {
            observeDoorEvents.current().collect {
                Logger.d { "currentDoorEvent collect: $it" }
                _currentDoorEvent.value = LoadingResult.Complete(it)
            }
        }
        if (fetchOnInit) {
            viewModelScope.launch(dispatchers.io) {
                logAppEvent(AppLoggerKeys.INIT_CURRENT_DOOR)
            }
            fetchCurrentDoorEvent()
        }
    }

    override fun signInWithGoogle(idToken: GoogleIdToken) {
        viewModelScope.launch(dispatchers.io) {
            logAppEvent(AppLoggerKeys.BEGIN_GOOGLE_SIGN_IN)
            Logger.d { "signInWithGoogle" }
            signInWithGoogleUseCase(idToken)
        }
    }

    override fun fetchCurrentDoorEvent() {
        Logger.d { "fetchCurrentDoorEvent" }
        viewModelScope.launch(dispatchers.io) {
            // ADR-023: explicit `Complete(...)` write on success — relying on
            // the repo StateFlow to fire is unsafe because MutableStateFlow
            // dedups by equality.
            _currentDoorEvent.value = LoadingResult.Loading(_currentDoorEvent.value.data)
            when (val result = fetchCurrentDoorEventUseCase()) {
                is AppResult.Success -> {
                    _currentDoorEvent.value = LoadingResult.Complete(result.data)
                }
                is AppResult.Error -> {
                    // Restore previous data so UI exits Loading state.
                    _currentDoorEvent.value = LoadingResult.Complete(_currentDoorEvent.value.data)
                    when (result.error) {
                        FetchError.NotReady -> Logger.w { "Server config not ready" }
                        FetchError.NetworkFailed -> Logger.w { "Network request failed" }
                    }
                }
            }
        }
    }

    override fun deregisterFcm() {
        Logger.d { "deregisterFcm" }
        viewModelScope.launch(dispatchers.io) {
            deregisterFcmUseCase()
        }
    }

    override fun onButtonTap() {
        stateMachine.onTap()
    }

    override fun log(key: String) {
        viewModelScope.launch(dispatchers.io) {
            logAppEvent(key)
        }
    }

    private fun submitButtonPress() {
        Logger.d { "submitButtonPress" }
        stateMachine.onNetworkStarted()
        viewModelScope.launch(dispatchers.io) {
            when (
                val result = pushRemoteButtonUseCase(
                    buttonAckToken = ButtonAckToken.create(
                        currentTimeMillis = System.currentTimeMillis(),
                        appVersion = appVersion,
                    ),
                )
            ) {
                is AppResult.Success -> stateMachine.onNetworkCompleted()
                is AppResult.Error -> when (result.error) {
                    ActionError.NotAuthenticated -> {
                        Logger.w { "Push failed — not authenticated" }
                        stateMachine.reset()
                    }
                    ActionError.MissingData -> {
                        Logger.w { "Push failed — missing data" }
                        stateMachine.reset()
                    }
                    ActionError.NetworkFailed -> {
                        Logger.w { "Push failed — network error" }
                        stateMachine.onNetworkFailed()
                    }
                }
            }
        }
    }
}
