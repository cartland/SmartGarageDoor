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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.chriscartland.garage.usecase.AppLoggerViewModel
import com.chriscartland.garage.usecase.AuthViewModel
import com.chriscartland.garage.usecase.DoorViewModel
import com.chriscartland.garage.usecase.RemoteButtonViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import java.time.Instant
import java.time.ZoneId
import com.chriscartland.garage.ui.home.HomeContent as HomeContentInternal

/**
 * Stateful Home tab — thin bridge between Main.kt and the stateless
 * [HomeContentInternal] in [com.chriscartland.garage.ui.home].
 *
 * Resolves ViewModels, collects flows, runs [HomeMapper], renders. All
 * mapping logic is in [HomeMapper] (unit-tested); all layout is in
 * [HomeContentInternal] (screenshot-tested).
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeContent(
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel? = null,
    doorViewModel: DoorViewModel? = null,
    appLoggerViewModel: AppLoggerViewModel? = null,
) {
    val component = rememberAppComponent()
    val resolvedAuthViewModel = authViewModel ?: viewModel { component.authViewModel }
    val resolvedDoorViewModel = doorViewModel ?: viewModel { component.doorViewModel }
    val buttonViewModel: RemoteButtonViewModel = viewModel { component.remoteButtonViewModel }
    val resolvedAppLoggerViewModel = appLoggerViewModel ?: viewModel { component.appLoggerViewModel }

    val googleSignIn = rememberGoogleSignIn(
        onTokenReceived = { token -> resolvedAuthViewModel.signInWithGoogle(token) },
    )
    val currentDoorEvent by resolvedDoorViewModel.currentDoorEvent.collectAsState()
    val buttonState by buttonViewModel.buttonState.collectAsState()
    val authState by resolvedAuthViewModel.authState.collectAsState()
    val isCheckInStale by resolvedDoorViewModel.isCheckInStale.collectAsState()

    val notificationPermissionState = rememberNotificationPermissionState()
    var permissionRequestCount by remember { mutableIntStateOf(0) }

    // `now` is driven by the VM's LiveClock-backed StateFlow (10s tick) —
    // `rememberLiveNow()` no longer exists; the ticker is owned by the
    // UseCase layer and lives across the app, not per-Composable.
    val nowEpochSeconds by resolvedDoorViewModel.nowEpochSeconds.collectAsState()
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
        isRefreshing = currentDoorEvent is LoadingResult.Loading,
        onRefresh = {
            resolvedAppLoggerViewModel.log(AppLoggerKeys.USER_FETCH_CURRENT_DOOR)
            resolvedDoorViewModel.fetchCurrentDoorEvent()
        },
        onAlertAction = { alert ->
            when (alert) {
                is HomeAlert.Stale -> {
                    Logger.e { "Trying to fix outdated info. Resetting FCM, and fetching data." }
                    resolvedDoorViewModel.deregisterFcm()
                    resolvedDoorViewModel.fetchCurrentDoorEvent()
                }
                is HomeAlert.PermissionMissing -> {
                    permissionRequestCount++
                    notificationPermissionState.launchPermissionRequest()
                    resolvedAppLoggerViewModel.log(AppLoggerKeys.USER_REQUESTED_NOTIFICATION_PERMISSION)
                }
                is HomeAlert.FetchError -> {
                    resolvedDoorViewModel.fetchCurrentDoorEvent()
                }
            }
        },
        onRemoteButtonTap = {
            when (authState) {
                is AuthState.Authenticated -> {
                    Logger.d { "Remote button tapped. AuthViewModel authState $authState" }
                    buttonViewModel.onButtonTap()
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
