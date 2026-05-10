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

package com.chriscartland.garage.ui

import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.touchlab.kermit.Logger
import com.chriscartland.garage.auth.rememberGoogleSignIn
import com.chriscartland.garage.di.rememberAppComponent
import com.chriscartland.garage.domain.model.AppLoggerKeys
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.LoadingResult
import com.chriscartland.garage.permissions.rememberNotificationPermissionState
import com.chriscartland.garage.ui.home.DeviceCheckIn
import com.chriscartland.garage.ui.home.HomeAlert
import com.chriscartland.garage.ui.home.HomeMapper
import com.chriscartland.garage.usecase.ButtonHealthDisplay
import com.chriscartland.garage.usecase.HomeViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import java.time.Instant
import java.time.ZoneId
import com.chriscartland.garage.ui.home.HomeContent as HomeContentInternal

/**
 * Stateful Home tab — thin bridge between Main.kt and the stateless
 * [HomeContentInternal] in [com.chriscartland.garage.ui.home].
 *
 * Resolves the screen-scoped [HomeViewModel] (ADR-026 — one VM per screen),
 * collects flows, runs [HomeMapper], renders. All mapping logic is in
 * [HomeMapper] (unit-tested); all layout is in [HomeContentInternal]
 * (screenshot-tested).
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeContent(
    modifier: Modifier = Modifier,
    homeViewModel: HomeViewModel? = null,
) {
    val component = rememberAppComponent()
    val resolved = homeViewModel ?: viewModel { component.homeViewModel }

    val googleSignIn = rememberGoogleSignIn(
        onTokenReceived = { token -> resolved.signInWithGoogle(token) },
    )
    val currentDoorEvent by resolved.currentDoorEvent.collectAsState()
    val buttonState by resolved.buttonState.collectAsState()
    val authState by resolved.authState.collectAsState()
    val isCheckInStale by resolved.isCheckInStale.collectAsState()
    // No `initialValue` — `buttonHealthDisplay` is a StateFlow whose
    // upstream is stateIn'd at app scope (Eagerly), so the cached current
    // value is read synchronously on first composition. Avoids the
    // brief "Checking…" flash on every fresh screen entry.
    val buttonHealthDisplay: ButtonHealthDisplay by resolved.buttonHealthDisplay
        .collectAsStateWithLifecycle()

    val notificationPermissionState = rememberNotificationPermissionState()
    // Survives rotation + process death so the alert flicker on rotation
    // doesn't re-prompt for permission spuriously.
    var permissionRequestCount by rememberSaveable { mutableIntStateOf(0) }

    // `now` is driven by the VM's LiveClock-backed StateFlow (10s tick) —
    // `rememberLiveNow()` no longer exists; the ticker is owned by the
    // UseCase layer and lives across the app, not per-Composable.
    val nowEpochSeconds by resolved.nowEpochSeconds.collectAsState()
    val now = remember(nowEpochSeconds) { Instant.ofEpochSecond(nowEpochSeconds) }
    val zone = remember { ZoneId.systemDefault() }

    val status = HomeMapper.toHomeStatusDisplay(currentDoorEvent, now, zone, isCheckInStale)
    val alerts = HomeMapper.toHomeAlerts(
        currentDoorEvent = currentDoorEvent,
        isCheckInStale = isCheckInStale,
        notificationPermissionGranted = notificationPermissionState.status.isGranted,
        notificationRequestCount = permissionRequestCount,
    )
    val homeAuthState = HomeMapper.toHomeAuthState(authState)
    val deviceCheckIn = DeviceCheckIn.format(
        lastCheckInSeconds = currentDoorEvent.data?.lastCheckInTimeSeconds,
        nowSeconds = nowEpochSeconds,
    )

    HomeContentInternal(
        status = status,
        authState = homeAuthState,
        modifier = modifier,
        remoteButtonState = buttonState,
        alerts = alerts,
        deviceCheckIn = deviceCheckIn,
        buttonHealthDisplay = buttonHealthDisplay,
        isRefreshing = currentDoorEvent is LoadingResult.Loading,
        onRefresh = {
            resolved.log(AppLoggerKeys.USER_FETCH_CURRENT_DOOR)
            resolved.fetchCurrentDoorEvent()
            // Pull-to-refresh on Home is the user's "give me a fresh view of
            // everything visible on this screen" gesture — refresh button
            // health alongside the door event so the remote-button pill
            // reflects the same point-in-time as the door status.
            resolved.refreshButtonHealth()
        },
        onAlertAction = { alert ->
            when (alert) {
                is HomeAlert.Stale -> {
                    Logger.e { "Trying to fix outdated info. Resetting FCM, and fetching data." }
                    resolved.deregisterFcm()
                    resolved.fetchCurrentDoorEvent()
                }
                is HomeAlert.PermissionMissing -> {
                    permissionRequestCount++
                    notificationPermissionState.launchPermissionRequest()
                    resolved.log(AppLoggerKeys.USER_REQUESTED_NOTIFICATION_PERMISSION)
                }
                is HomeAlert.FetchError -> {
                    resolved.fetchCurrentDoorEvent()
                }
            }
        },
        onRemoteButtonTap = {
            when (authState) {
                is AuthState.Authenticated -> {
                    Logger.d { "Remote button tapped. authState $authState" }
                    resolved.onButtonTap()
                }
                AuthState.Unauthenticated, AuthState.Unknown -> {
                    googleSignIn.launchSignIn()
                }
            }
        },
        onSignIn = { googleSignIn.launchSignIn() },
    )
    ReportDrawnWhen { currentDoorEvent is LoadingResult.Complete }
}
