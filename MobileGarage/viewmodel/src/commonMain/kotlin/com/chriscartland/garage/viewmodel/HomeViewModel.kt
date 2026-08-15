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

package com.chriscartland.garage.viewmodel

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
import com.chriscartland.garage.usecase.ButtonAckToken
import com.chriscartland.garage.usecase.ButtonHealthDisplay
import com.chriscartland.garage.usecase.ButtonStateMachine
import com.chriscartland.garage.usecase.CheckDoorCommandUseCase
import com.chriscartland.garage.usecase.CheckInStalenessManager
import com.chriscartland.garage.usecase.ClassifyVoiceIntentUseCase
import com.chriscartland.garage.usecase.DeregisterFcmUseCase
import com.chriscartland.garage.usecase.FetchButtonHealthUseCase
import com.chriscartland.garage.usecase.FetchCurrentDoorEventUseCase
import com.chriscartland.garage.usecase.LiveClock
import com.chriscartland.garage.usecase.LogAppEventUseCase
import com.chriscartland.garage.usecase.ObserveAuthStateUseCase
import com.chriscartland.garage.usecase.ObserveDoorEventsUseCase
import com.chriscartland.garage.usecase.ObserveFeatureAccessUseCase
import com.chriscartland.garage.usecase.PushRemoteButtonUseCase
import com.chriscartland.garage.usecase.RemoteButtonVoiceCommandEnvironment
import com.chriscartland.garage.usecase.SignInWithGoogleUseCase
import com.chriscartland.garage.usecase.VoiceCommandController
import com.chriscartland.garage.usecase.VoiceCommandState
import com.chriscartland.garage.usecase.VoiceDoorState
import com.chriscartland.garage.usecase.VoiceDoorStateMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

    /**
     * The whole Home door-status surface as ONE derived node
     * (docs/DATA_GRAPH_PLAN.md rule G7): the typed warning, the
     * "Since … · duration" line data, the check-in-stale flag, and the
     * voice gate's door projection — all fields of the same value,
     * computed by [HomeDoorStateMapper] from the same
     * `(event, stale, now)` snapshot. Replaces the former independent
     * `warning` / `sinceStatus` / `isCheckInStale` StateFlows, which
     * could render one frame apart and whose agreement with the voice
     * gate was promised only by a comment. Seeded synchronously so a
     * fresh screen entry renders the correct state on the first frame.
     */
    val doorState: StateFlow<HomeDoorState>

    /**
     * Wall-clock time as epoch seconds, ticking on the [LiveClock] cadence
     * (10s by default). Pass-through of [LiveClock.nowEpochSeconds] (ADR-022).
     */
    val nowEpochSeconds: StateFlow<Long>

    val buttonState: StateFlow<RemoteButtonState>

    /**
     * Display state for the remote-button device's online/offline pill.
     * Per ADR-022, exposed as [StateFlow] (the upstream
     * `ComputeButtonHealthDisplayUseCase` is `stateIn`'d at app scope so
     * the cached value is available synchronously). The Composable
     * collects via the no-initial-value `collectAsStateWithLifecycle()`
     * overload — eliminates the brief `Loading` flash on every fresh
     * screen entry.
     */
    val buttonHealthDisplay: StateFlow<ButtonHealthDisplay>

    /**
     * Per-user access for the Developer features (same server-maintained
     * flag that gates Settings → Developer). Gates the Home voice-control
     * section. Tri-state: `null` (loading or denied), `false`, `true`.
     */
    val developerAccess: StateFlow<Boolean?>

    /**
     * Home voice-control surface (developer-flag-gated), LIVE: the gate
     * reads the real observed door state (projected via
     * [VoiceDoorStateMapper] — anomalies and stale check-ins refuse),
     * and a committed command presses the REAL remote garage button
     * through the same auth-gated push path as the manual button.
     * Fixed 3s cancel window. State machine: [VoiceCommandController]
     * in `:usecase`.
     */
    val voiceCommandState: StateFlow<VoiceCommandState>

    /** Mic tap: always starts over; cancels a pending command first. */
    fun voiceCommandMicTap()

    /** Recognizer outcome for the command loop (null = no speech). */
    fun voiceCommandTranscript(text: String?)

    /** The recognizer launch failed: no recognizer on this device. */
    fun voiceCommandCaptureUnavailable()

    /**
     * The Home screen left the foreground: cancels a pending (Armed)
     * command so nothing commits off-screen.
     */
    fun voiceCommandBackgrounded()

    fun signInWithGoogle(idToken: GoogleIdToken)

    fun fetchCurrentDoorEvent()

    /**
     * One-shot fetch of the remote-button device's health. Called from
     * pull-to-refresh alongside [fetchCurrentDoorEvent] so the user can
     * recover both pills with a single gesture. Fire-and-forget; the
     * resulting state lands in the StateFlow that backs [buttonHealthDisplay].
     */
    fun refreshButtonHealth()

    fun deregisterFcm()

    fun onButtonTap()

    fun log(key: String)
}

class DefaultHomeViewModel(
    observeDoorEvents: ObserveDoorEventsUseCase,
    observeAuthState: ObserveAuthStateUseCase,
    private val observeFeatureAccessUseCase: ObserveFeatureAccessUseCase,
    private val classifyVoiceIntentUseCase: ClassifyVoiceIntentUseCase,
    private val logAppEvent: LogAppEventUseCase,
    private val dispatchers: DispatcherProvider,
    private val fetchCurrentDoorEventUseCase: FetchCurrentDoorEventUseCase,
    private val fetchButtonHealthUseCase: FetchButtonHealthUseCase,
    private val deregisterFcmUseCase: DeregisterFcmUseCase,
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    private val pushRemoteButtonUseCase: PushRemoteButtonUseCase,
    private val checkDoorCommandUseCase: CheckDoorCommandUseCase,
    private val checkInStalenessManager: CheckInStalenessManager,
    private val liveClock: LiveClock,
    override val buttonHealthDisplay: StateFlow<ButtonHealthDisplay>,
    private val appVersion: String,
    // Default false — cold-start fetch lives in `InitialDoorFetchManager`
    // (singleton, idempotent, fires once per process from `AppStartup`).
    // Per-VM init fetch fired on every fresh `NavBackStackEntry`, causing
    // a redundant round-trip on every tab tap even though FCM already
    // covers live updates while the app is open.
    private val fetchOnInit: Boolean = false,
) : ViewModel(),
    HomeViewModel {
    // ADR-022: pass through the repository's StateFlow by reference — no mirror.
    override val authState: StateFlow<AuthState> = observeAuthState()

    // ADR-022: pass through LiveClock's StateFlow — no mirror.
    override val nowEpochSeconds: StateFlow<Long> = liveClock.nowEpochSeconds

    // Seed from the singleton repo's StateFlow `.value` (pass-through via
    // `observeDoorEvents.current()` per ADR-022) so we never expose
    // `Loading(null)` on first composition. Without this seed, the
    // Composable would briefly read `Loading(null)` → map to UNKNOWN/MIDWAY,
    // then the collect lambda below would update to `Complete(actualEvent)` →
    // map to OPEN/CLOSED — and `LaunchedEffect(doorPosition)` in `GarageIcon`
    // would visibly animate MIDWAY→actual on every fresh screen entry.
    private val _currentDoorEvent =
        MutableStateFlow<LoadingResult<DoorEvent?>>(
            LoadingResult.Complete(observeDoorEvents.current().value),
        )
    override val currentDoorEvent: StateFlow<LoadingResult<DoorEvent?>> = _currentDoorEvent

    private val checkInStale = MutableStateFlow(false)

    // The whole door-status surface as ONE derived node (G7,
    // docs/DATA_GRAPH_PLAN.md): a single combine + a single pure
    // transform replaces the former three independent stateIns
    // (`warning`, `sinceStatus`, and the voice gate's projection) over
    // the same root, which could render one frame apart and whose
    // agreement was promised only by a comment. Seeded synchronously
    // (Eagerly, ComputeButtonHealthDisplayUseCase pattern) so a fresh
    // screen entry reads the correct state on first composition.
    override val doorState: StateFlow<HomeDoorState> =
        combine(_currentDoorEvent, checkInStale, nowEpochSeconds) { event, stale, now ->
            HomeDoorStateMapper.compute(event.data, stale, now)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = HomeDoorStateMapper.compute(
                _currentDoorEvent.value.data,
                checkInStale.value,
                nowEpochSeconds.value,
            ),
        )

    private val _developerAccess = MutableStateFlow<Boolean?>(null)
    override val developerAccess: StateFlow<Boolean?> = _developerAccess

    // The voice gate's door view is a projection OF [doorState] — the
    // controller samples `.value` at gate time, so what it gates on is
    // the same computed snapshot the status card renders (it can lag by
    // at most one dispatch, but can never be a DIFFERENT projection of
    // the same instant, which the pre-G7 independent combine allowed).
    private val voiceDoorState: StateFlow<VoiceDoorState> =
        doorState
            .map { it.voice }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = doorState.value.voice,
            )

    private val voiceCommandController = VoiceCommandController(
        classify = classifyVoiceIntentUseCase,
        environment = RemoteButtonVoiceCommandEnvironment(
            doorState = voiceDoorState,
            pushRemoteButton = pushRemoteButtonUseCase,
            checkDoorCommand = checkDoorCommandUseCase,
            createButtonAckToken = {
                // The `-voice` suffix rides in the appVersion slot so server
                // logs can tell voice presses from manual ones; the server
                // treats the token as opaque (ack-equality only).
                ButtonAckToken.create(
                    currentTimeMillis = kotlinx.datetime.Clock.System
                        .now()
                        .toEpochMilliseconds(),
                    appVersion = "$appVersion-voice",
                )
            },
        ),
        scope = viewModelScope,
        // Fixed 3s window on Home (the playground keeps the stepper).
    )
    override val voiceCommandState: StateFlow<VoiceCommandState> = voiceCommandController.state

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
                checkInStale.value = it
            }
        }
        viewModelScope.launch(dispatchers.io) {
            observeDoorEvents.current().collect {
                Logger.d { "currentDoorEvent collect: $it" }
                _currentDoorEvent.value = LoadingResult.Complete(it)
            }
        }
        viewModelScope.launch(dispatchers.io) {
            observeFeatureAccessUseCase.developer().collect { _developerAccess.value = it }
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

    override fun refreshButtonHealth() {
        Logger.d { "refreshButtonHealth" }
        viewModelScope.launch(dispatchers.io) {
            fetchButtonHealthUseCase()
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

    override fun voiceCommandMicTap() = voiceCommandController.onMicTap()

    override fun voiceCommandTranscript(text: String?) = voiceCommandController.onTranscript(text)

    override fun voiceCommandCaptureUnavailable() = voiceCommandController.onCaptureUnavailable()

    override fun voiceCommandBackgrounded() = voiceCommandController.onBackgrounded()

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
                        currentTimeMillis = kotlinx.datetime.Clock.System
                            .now()
                            .toEpochMilliseconds(),
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
                    // SnoozeEventChanged is snooze-specific; the push
                    // repository never returns it. Defensive branch only —
                    // logs the unexpected case and resets the state machine
                    // so the UI doesn't latch on a half-completed press.
                    ActionError.SnoozeEventChanged -> {
                        Logger.e { "Push got unexpected SnoozeEventChanged (snooze-only error)" }
                        stateMachine.reset()
                    }
                }
            }
        }
    }
}
