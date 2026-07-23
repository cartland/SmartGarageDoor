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
import com.chriscartland.garage.domain.model.GoogleIdToken
import com.chriscartland.garage.domain.model.NavigationRailItemPosition
import com.chriscartland.garage.domain.model.NavigationRailLayout
import com.chriscartland.garage.domain.model.SnoozeAction
import com.chriscartland.garage.domain.model.SnoozeDurationUIOption
import com.chriscartland.garage.domain.model.SnoozeState
import com.chriscartland.garage.domain.model.WatchAppStatus
import com.chriscartland.garage.domain.model.WatchInstallAction
import com.chriscartland.garage.domain.model.WatchInstallResult
import com.chriscartland.garage.domain.model.toServer
import com.chriscartland.garage.usecase.AppSettingsUseCase
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
import com.chriscartland.garage.usecase.SnoozeNotificationsUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val SNOOZE_ACTION_RESET_DELAY_MS = 10_000L
private const val WATCH_INSTALL_ACTION_RESET_DELAY_MS = 10_000L

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
}

class DefaultProfileViewModel(
    observeAuthState: ObserveAuthStateUseCase,
    computeEffectiveSnoozeState: ComputeEffectiveSnoozeStateUseCase,
    private val observeDoorEvents: ObserveDoorEventsUseCase,
    private val observeFeatureAccessUseCase: ObserveFeatureAccessUseCase,
    private val observeWatchAppStatusUseCase: ObserveWatchAppStatusUseCase,
    private val requestWatchAppInstallUseCase: RequestWatchAppInstallUseCase,
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

    private val _layoutDebugEnabled = MutableStateFlow(false)
    override val layoutDebugEnabled: StateFlow<Boolean> = _layoutDebugEnabled

    private val _navigationRailItemPosition =
        MutableStateFlow(NavigationRailItemPosition.TopAligned)
    override val navigationRailItemPosition: StateFlow<NavigationRailItemPosition> =
        _navigationRailItemPosition

    private val _navigationRailTopPaddingDp =
        MutableStateFlow(NavigationRailLayout.DEFAULT_TOP_PADDING_DP)
    override val navigationRailTopPaddingDp: StateFlow<Int> = _navigationRailTopPaddingDp

    private val _watchAppStatus = MutableStateFlow<WatchAppStatus>(WatchAppStatus.Unknown)
    override val watchAppStatus: StateFlow<WatchAppStatus> = _watchAppStatus

    private val _watchInstallAction = MutableStateFlow<WatchInstallAction>(WatchInstallAction.Idle)
    override val watchInstallAction: StateFlow<WatchInstallAction> = _watchInstallAction

    // Cached so the snooze action can attach the latest door change time
    // without the UI having to thread it through.
    private val currentDoorEvent = MutableStateFlow<DoorEvent?>(null)

    init {
        viewModelScope.launch(dispatchers.io) {
            observeDoorEvents.current().collect { currentDoorEvent.value = it }
        }
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
        viewModelScope.launch(dispatchers.io) {
            observeWatchAppStatusUseCase().collect { _watchAppStatus.value = it }
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
