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
import com.chriscartland.garage.domain.model.DoorUpdateStrategyOverride
import com.chriscartland.garage.domain.model.GoogleIdToken
import com.chriscartland.garage.domain.model.NavigationRailItemPosition
import com.chriscartland.garage.domain.model.NavigationRailLayout
import com.chriscartland.garage.domain.model.SnoozeAction
import com.chriscartland.garage.domain.model.SnoozeDurationUIOption
import com.chriscartland.garage.domain.model.SnoozeState
import com.chriscartland.garage.domain.model.VoiceIntentClassification
import com.chriscartland.garage.domain.model.WatchAppStatus
import com.chriscartland.garage.domain.model.WatchInstallAction
import com.chriscartland.garage.domain.model.WatchInstallResult
import com.chriscartland.garage.domain.model.toServer
import com.chriscartland.garage.usecase.AppSettingsUseCase
import com.chriscartland.garage.usecase.ClassifyVoiceIntentUseCase
import com.chriscartland.garage.usecase.ComputeEffectiveSnoozeStateUseCase
import com.chriscartland.garage.usecase.FetchSnoozeStatusUseCase
import com.chriscartland.garage.usecase.LogAppEventUseCase
import com.chriscartland.garage.usecase.ObserveAuthStateUseCase
import com.chriscartland.garage.usecase.ObserveDoorEventsUseCase
import com.chriscartland.garage.usecase.ObserveFeatureAccessUseCase
import com.chriscartland.garage.usecase.ObserveWatchAppStatusUseCase
import com.chriscartland.garage.usecase.RequestWatchAppInstallUseCase
import com.chriscartland.garage.usecase.RevalidateSnoozeStatusUseCase
import com.chriscartland.garage.usecase.SignInWithGoogleUseCase
import com.chriscartland.garage.usecase.SignOutUseCase
import com.chriscartland.garage.usecase.SimulatedVoiceCommandEnvironment
import com.chriscartland.garage.usecase.SnoozeNotificationsUseCase
import com.chriscartland.garage.usecase.VoiceCommandController
import com.chriscartland.garage.usecase.VoiceCommandIgnoreReason
import com.chriscartland.garage.usecase.VoiceCommandState
import com.chriscartland.garage.usecase.VoiceDoorState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val SNOOZE_ACTION_RESET_DELAY_MS = 10_000L
private const val WATCH_INSTALL_ACTION_RESET_DELAY_MS = 10_000L

/**
 * What the classifier made of one capture on the simulated voice
 * surface, latched so it can be read and copied at leisure.
 *
 * Exists to feed the eval corpus: real device transcripts are the best
 * source of new cases (see `MobileGarage/docs/VOICE_COMMANDS.md`), and
 * the interesting ones are exactly the surprises — a refusal you
 * expected to act, or an action you expected to refuse. Display-free by
 * design; the UI maps the enums to labels.
 */
data class VoiceVerdict(
    val transcript: String,
    val classification: VoiceIntentClassification,
    val engineName: String,
    /** Why it was refused, or null when the command armed. */
    val ignoreReason: VoiceCommandIgnoreReason?,
) {
    /**
     * Paste-friendly structured summary. Raw enum names
     * (locale-independent, stable across app versions) and the input
     * quoted so leading/trailing whitespace is visible.
     */
    fun clipboardSummary(): String =
        """
        input: "$transcript"
        intent: ${classification.intent.name}
        confidence: ${classification.confidence.name}
        engine: $engineName
        outcome: ${ignoreReason?.name ?: "ARMED"}
        """.trimIndent()
}

/**
 * Drives the Settings screen — one VM per screen (ADR-026). Aggregates the
 * UseCases that the legacy `AuthViewModel` + `RemoteButtonViewModel` +
 * `AppSettingsViewModel` trio exposed to `ProfileContent.kt`. Phase 43 —
 * depends on UseCases only.
 *
 * The user-facing tab label is "Settings"; the function name `ProfileContent`
 * (and `Screen.Profile` route) is preserved for backwards compatibility with
 * the bottom-nav definition.
 */
interface ProfileViewModel {
    val authState: StateFlow<AuthState>

    val snoozeState: StateFlow<SnoozeState>

    val snoozeAction: StateFlow<SnoozeAction>

    /**
     * Per-user access for the Function List feature, used to gate the
     * Settings-screen Function-list entry. Tri-state: `null` (loading or
     * denied), `false` (server says denied), `true` (allowed). Full
     * convention in `docs/FEATURE_FLAGS.md`.
     */
    val functionListAccess: StateFlow<Boolean?>

    /**
     * Per-user access for the Developer section, used to gate the whole
     * Settings → Developer block. Tri-state, same convention as
     * [functionListAccess]. Replaces the temporary `functionListAccess`-as-
     * Developer-gate shortcut from `android/196` (2.10.2).
     */
    val developerAccess: StateFlow<Boolean?>

    /**
     * Developer-only: when `true`, the chrome (TopAppBar / NavigationBar /
     * NavigationRail / body) paints with debug colors so layout boundaries
     * are visible. Persisted in DataStore so the toggle survives process
     * death. UI gate: Settings → Developer → "Layout debug colors".
     * Default `false`.
     */
    val layoutDebugEnabled: StateFlow<Boolean>

    /**
     * Developer-only: where Wide-mode NavigationRail items sit
     * vertically. Persisted in DataStore. UI gate: Settings →
     * Developer → "Nav rail items". Default
     * [NavigationRailItemPosition.CenteredVertically].
     */
    val navigationRailItemPosition: StateFlow<NavigationRailItemPosition>

    /**
     * Developer-only: extra dp inserted above the Wide-mode rail's
     * tab items. Persisted in DataStore. UI gate: Settings →
     * Developer → "Nav rail top padding". Default 0.
     */
    val navigationRailTopPaddingDp: StateFlow<Int>

    /**
     * Developer-only: which door-update strategy the app runs, as the
     * stored OVERRIDE rather than the resolved id — the picker's job is to
     * show what was chosen, including "whatever this platform defaults to".
     * Persisted in DataStore; `DoorUpdateManager` swaps strategies live
     * when it changes. UI gate: Settings → Developer → "Door updates".
     */
    val doorUpdateStrategy: StateFlow<DoorUpdateStrategyOverride>

    /**
     * Whether the paired watch has the Wear OS app. Drives the Settings
     * "Watch" section: hidden until a connected watch is detected, then a
     * green check (installed) or an install call-to-action.
     */
    val watchAppStatus: StateFlow<WatchAppStatus>

    /**
     * Transient state of the install-on-watch action ([installOnWatch]).
     * `Idle -> Sending -> (OpenedOnWatch | Failed) -> Idle` with auto-reset,
     * mirroring [snoozeAction]. The UI surfaces OpenedOnWatch/Failed once
     * (snackbar; Failed also falls back to the phone Play Store).
     */
    val watchInstallAction: StateFlow<WatchInstallAction>

    /**
     * Settings → Developer → Simulated voice: the Home tab's voice
     * control, rehearsed safely. The same mic → classify → gate →
     * cancel-window → commit loop and the same 3s cancel window as
     * Home, but the commit presses a pretend button on a pretend door
     * ([SimulatedVoiceCommandEnvironment]). The real door cannot be
     * reached from here — this ViewModel has no
     * `PushRemoteButtonUseCase`. State machine:
     * [VoiceCommandController] in `:usecase`.
     */
    val voiceCommandState: StateFlow<VoiceCommandState>

    /**
     * The pretend door the gate checks against. Displayed read-only:
     * refusals ("already open", "the door is moving") are unreadable
     * without it, and it reacts to committed commands the way the real
     * one does.
     */
    val voiceCommandDoorState: StateFlow<VoiceDoorState>

    /**
     * The last classified capture, or null before the first one. Latched
     * rather than derived from [voiceCommandState] so it survives the
     * ~4s refusal flash and stays copyable. See [VoiceVerdict].
     */
    val lastVoiceVerdict: StateFlow<VoiceVerdict?>

    /** Mic tap: always starts over; cancels a pending command first. */
    fun voiceCommandMicTap()

    /** Recognizer outcome for the command loop (null = no speech). */
    fun voiceCommandTranscript(text: String?)

    /** The recognizer launch failed: no recognizer on this device. */
    fun voiceCommandCaptureUnavailable()

    /**
     * The surface left the screen: cancels a pending (Armed) command
     * so nothing commits off-screen.
     */
    fun voiceCommandBackgrounded()

    fun signInWithGoogle(idToken: GoogleIdToken)

    /** Open the app's Play Store listing on the connected watch. */
    fun installOnWatch()

    fun signOut()

    fun fetchSnoozeStatus()

    /**
     * TTL-gated screen-entry revalidate (STATUS_CACHE_PLAN.md D3):
     * cached state renders instantly; this fetches only when the last
     * server round-trip is stale. Call once per screen entry — the
     * per-minute poll it replaces is gone.
     */
    fun revalidateSnoozeIfStale()

    fun snoozeOpenDoorsNotifications(snoozeDuration: SnoozeDurationUIOption)

    fun setLayoutDebugEnabled(enabled: Boolean)

    fun setNavigationRailItemPosition(position: NavigationRailItemPosition)

    fun resetNavigationRailItemPosition()

    fun setNavigationRailTopPaddingDp(value: Int)

    fun resetNavigationRailTopPaddingDp()

    fun setDoorUpdateStrategy(value: DoorUpdateStrategyOverride)
}

class DefaultProfileViewModel(
    observeAuthState: ObserveAuthStateUseCase,
    computeEffectiveSnoozeState: ComputeEffectiveSnoozeStateUseCase,
    private val observeDoorEvents: ObserveDoorEventsUseCase,
    private val observeFeatureAccessUseCase: ObserveFeatureAccessUseCase,
    observeWatchAppStatusUseCase: ObserveWatchAppStatusUseCase,
    private val requestWatchAppInstallUseCase: RequestWatchAppInstallUseCase,
    private val classifyVoiceIntentUseCase: ClassifyVoiceIntentUseCase,
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val fetchSnoozeStatusUseCase: FetchSnoozeStatusUseCase,
    private val revalidateSnoozeStatusUseCase: RevalidateSnoozeStatusUseCase,
    private val snoozeNotificationsUseCase: SnoozeNotificationsUseCase,
    private val logAppEvent: LogAppEventUseCase,
    private val appSettings: AppSettingsUseCase,
    private val dispatchers: DispatcherProvider,
) : ViewModel(),
    ProfileViewModel {
    // ADR-022: pass through the repository's StateFlow by reference — no mirror.
    override val authState: StateFlow<AuthState> = observeAuthState()

    // The user-facing snooze state: the singleton expiry derivation's
    // StateFlow, passed through by reference (ADR-022) — "Snoozing until
    // 3 PM" flips to NotSnoozing at 3 PM on both platforms, no polling.
    override val snoozeState: StateFlow<SnoozeState> = computeEffectiveSnoozeState()

    private val _snoozeAction = MutableStateFlow<SnoozeAction>(SnoozeAction.Idle)
    override val snoozeAction: StateFlow<SnoozeAction> = _snoozeAction

    private val _functionListAccess = MutableStateFlow<Boolean?>(null)
    override val functionListAccess: StateFlow<Boolean?> = _functionListAccess

    private val _developerAccess = MutableStateFlow<Boolean?>(null)
    override val developerAccess: StateFlow<Boolean?> = _developerAccess

    private val _layoutDebugEnabled = MutableStateFlow<Boolean>(false)
    override val layoutDebugEnabled: StateFlow<Boolean> = _layoutDebugEnabled

    private val _navigationRailItemPosition =
        MutableStateFlow<NavigationRailItemPosition>(NavigationRailItemPosition.TopAligned)
    override val navigationRailItemPosition: StateFlow<NavigationRailItemPosition> =
        _navigationRailItemPosition

    private val _navigationRailTopPaddingDp =
        MutableStateFlow<Int>(NavigationRailLayout.DEFAULT_TOP_PADDING_DP)
    override val navigationRailTopPaddingDp: StateFlow<Int> = _navigationRailTopPaddingDp

    // A `stateIn` pass-through, NOT a MutableStateFlow mirror: G3 bans a
    // VM-owned MutableStateFlow whose type argument is a :domain type, and
    // `ViewModelDomainMirrorKonsistTest` enforces it. The nav-rail settings
    // above are still mirrors only because they predate the rule and sit on
    // its burn-down list — this is the shape that list is burning down TO,
    // so a new one must not be added to it.
    //
    // Seeding with PLATFORM_DEFAULT is honest rather than a placeholder: it
    // is the persisted default, so the one frame before DataStore's first
    // read shows the value that is actually in force unless an override
    // exists, and an override corrects it immediately.
    override val doorUpdateStrategy: StateFlow<DoorUpdateStrategyOverride> =
        appSettings
            .observeDoorUpdateStrategy()
            .flowOn(dispatchers.io)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = DoorUpdateStrategyOverride.PLATFORM_DEFAULT,
            )

    // ADR-022: pass the UseCase's cached StateFlow through by reference.
    // A VM-local mirror would re-seed to Unknown on every fresh
    // NavBackStackEntry and replay the Settings "Watch" section's
    // expand-in animation on every entry to the tab.
    override val watchAppStatus: StateFlow<WatchAppStatus> = observeWatchAppStatusUseCase()

    private val _watchInstallAction = MutableStateFlow<WatchInstallAction>(WatchInstallAction.Idle)
    override val watchInstallAction: StateFlow<WatchInstallAction> = _watchInstallAction

    // Simulated voice: the real state machine wired to a fake world
    // (pretend door + pretend button). viewModelScope-owned so the
    // rehearsal's state evaporates with the screen VM. The cancel window
    // is left at the default, which is the same 3s Home commits on — the
    // point of this surface is to behave exactly like the live one.
    private val voiceCommandEnvironment = SimulatedVoiceCommandEnvironment(scope = viewModelScope)
    private val voiceCommandController = VoiceCommandController(
        classify = classifyVoiceIntentUseCase,
        environment = voiceCommandEnvironment,
        scope = viewModelScope,
    )
    override val voiceCommandState: StateFlow<VoiceCommandState> = voiceCommandController.state
    override val voiceCommandDoorState: StateFlow<VoiceDoorState> = voiceCommandEnvironment.doorState

    private val _lastVoiceVerdict = MutableStateFlow<VoiceVerdict?>(null)
    override val lastVoiceVerdict: StateFlow<VoiceVerdict?> = _lastVoiceVerdict

    // ADR-022 pass-through: the snooze action reads `.value` to attach
    // the latest door change time. This was a VM-local
    // MutableStateFlow<DoorEvent?>(null) mirror + collector — an
    // unseeded copy of repository-owned state (G3,
    // docs/DATA_GRAPH_PLAN.md); the upstream IS a StateFlow, so pass
    // the reference through instead.
    private val currentDoorEvent: StateFlow<DoorEvent?> = observeDoorEvents.current()

    init {
        viewModelScope.launch(dispatchers.io) {
            observeFeatureAccessUseCase.functionList().collect { _functionListAccess.value = it }
        }
        viewModelScope.launch(dispatchers.io) {
            observeFeatureAccessUseCase.developer().collect { _developerAccess.value = it }
        }
        viewModelScope.launch(dispatchers.io) {
            appSettings.observeLayoutDebugEnabled().collect { _layoutDebugEnabled.value = it }
        }
        viewModelScope.launch(dispatchers.io) {
            appSettings.observeNavigationRailItemPosition().collect {
                _navigationRailItemPosition.value = it
            }
        }
        viewModelScope.launch(dispatchers.io) {
            appSettings.observeNavigationRailTopPaddingDp().collect {
                _navigationRailTopPaddingDp.value = it
            }
        }
        // Latch every classified capture so the verdict outlives the ~4s
        // refusal flash. Copying a transcript is a deliberate, two-handed
        // act (read it, decide it is interesting, tap); tying it to a
        // state that auto-dismisses would make the corpus tool a race.
        // Reclassifying here rather than reading Armed/Ignored's own
        // fields is what makes the two states yield the SAME shape:
        // Armed carries no confidence, and Ignored's classification is
        // null for a no-speech capture. The classifier is pure, so this
        // cannot disagree with the verdict the gate actually acted on.
        viewModelScope.launch(dispatchers.default) {
            voiceCommandController.state.collect { state ->
                val transcript = when (state) {
                    is VoiceCommandState.Armed -> state.transcript
                    is VoiceCommandState.Ignored -> state.transcript
                    else -> null
                } ?: return@collect
                _lastVoiceVerdict.value = VoiceVerdict(
                    transcript = transcript,
                    classification = classifyVoiceIntentUseCase(transcript),
                    engineName = classifyVoiceIntentUseCase.engineName,
                    ignoreReason = (state as? VoiceCommandState.Ignored)?.reason,
                )
            }
        }
    }

    override fun installOnWatch() {
        // Guard double-taps: the row stays tappable while Sending renders
        // the in-flight ring, so a second tap must not queue another launch.
        if (_watchInstallAction.value is WatchInstallAction.Sending) return
        _watchInstallAction.value = WatchInstallAction.Sending
        viewModelScope.launch(dispatchers.io) {
            _watchInstallAction.value = when (requestWatchAppInstallUseCase()) {
                WatchInstallResult.OpenedOnWatch -> WatchInstallAction.OpenedOnWatch
                WatchInstallResult.NoWatchReachable -> WatchInstallAction.Failed
                WatchInstallResult.Failed -> WatchInstallAction.Failed
            }
            scheduleWatchInstallActionReset()
        }
    }

    override fun voiceCommandMicTap() = voiceCommandController.onMicTap()

    override fun voiceCommandTranscript(text: String?) = voiceCommandController.onTranscript(text)

    override fun voiceCommandCaptureUnavailable() = voiceCommandController.onCaptureUnavailable()

    override fun voiceCommandBackgrounded() = voiceCommandController.onBackgrounded()

    override fun signInWithGoogle(idToken: GoogleIdToken) {
        viewModelScope.launch(dispatchers.io) {
            logAppEvent(AppLoggerKeys.BEGIN_GOOGLE_SIGN_IN)
            Logger.d { "signInWithGoogle" }
            signInWithGoogleUseCase(idToken)
        }
    }

    override fun signOut() {
        viewModelScope.launch(dispatchers.io) {
            signOutUseCase()
        }
    }

    override fun fetchSnoozeStatus() {
        viewModelScope.launch(dispatchers.io) {
            fetchSnoozeStatusUseCase()
        }
    }

    override fun revalidateSnoozeIfStale() {
        viewModelScope.launch(dispatchers.io) {
            revalidateSnoozeStatusUseCase()
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
                    val newState = result.data
                    _snoozeAction.value = when (newState) {
                        is SnoozeState.Snoozing -> SnoozeAction.Succeeded.Set(newState.untilEpochSeconds)
                        SnoozeState.NotSnoozing -> SnoozeAction.Succeeded.Cleared
                        SnoozeState.Loading -> SnoozeAction.Succeeded.Cleared
                    }
                    scheduleActionReset()
                }
                is AppResult.Error -> {
                    _snoozeAction.value = when (result.error) {
                        ActionError.NotAuthenticated -> SnoozeAction.Failed.NotAuthenticated
                        ActionError.MissingData -> SnoozeAction.Failed.MissingData
                        ActionError.NetworkFailed -> SnoozeAction.Failed.NetworkError
                        ActionError.SnoozeEventChanged -> SnoozeAction.Failed.EventChanged
                    }
                    scheduleActionReset()
                }
            }
        }
    }

    override fun setLayoutDebugEnabled(enabled: Boolean) {
        viewModelScope.launch(dispatchers.io) {
            appSettings.setLayoutDebugEnabled(enabled)
        }
    }

    override fun setDoorUpdateStrategy(value: DoorUpdateStrategyOverride) {
        viewModelScope.launch(dispatchers.io) {
            appSettings.setDoorUpdateStrategy(value)
        }
    }

    override fun setNavigationRailItemPosition(position: NavigationRailItemPosition) {
        viewModelScope.launch(dispatchers.io) {
            appSettings.setNavigationRailItemPosition(position)
        }
    }

    override fun resetNavigationRailItemPosition() {
        viewModelScope.launch(dispatchers.io) {
            appSettings.restoreNavigationRailItemPositionDefault()
        }
    }

    override fun setNavigationRailTopPaddingDp(value: Int) {
        viewModelScope.launch(dispatchers.io) {
            appSettings.setNavigationRailTopPaddingDp(value)
        }
    }

    override fun resetNavigationRailTopPaddingDp() {
        viewModelScope.launch(dispatchers.io) {
            appSettings.restoreNavigationRailTopPaddingDpDefault()
        }
    }

    private fun scheduleActionReset() {
        viewModelScope.launch {
            delay(SNOOZE_ACTION_RESET_DELAY_MS)
            _snoozeAction.value = SnoozeAction.Idle
        }
    }

    private fun scheduleWatchInstallActionReset() {
        viewModelScope.launch {
            delay(WATCH_INSTALL_ACTION_RESET_DELAY_MS)
            _watchInstallAction.value = WatchInstallAction.Idle
        }
    }
}
