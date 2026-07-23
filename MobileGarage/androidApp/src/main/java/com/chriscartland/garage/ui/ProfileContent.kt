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

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.ReportDrawn
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chriscartland.garage.R
import com.chriscartland.garage.auth.rememberGoogleSignIn
import com.chriscartland.garage.di.rememberAppComponent
import com.chriscartland.garage.domain.model.AppLinks
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.SnoozeAction
import com.chriscartland.garage.domain.model.SnoozeState
import com.chriscartland.garage.domain.model.WatchInstallAction
import com.chriscartland.garage.permissions.rememberNotificationPermissionState
import com.chriscartland.garage.ui.settings.AccountBottomSheet
import com.chriscartland.garage.ui.settings.AccountRowState
import com.chriscartland.garage.ui.settings.NavRailBottomSheet
import com.chriscartland.garage.ui.settings.SettingsContent
import com.chriscartland.garage.ui.settings.SnoozeBottomSheet
import com.chriscartland.garage.ui.settings.SnoozeRowState
import com.chriscartland.garage.ui.settings.VersionBottomSheet
import com.chriscartland.garage.version.AppVersion
import com.chriscartland.garage.viewmodel.ProfileViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Settings screen — bottom-nav destination. Sectioned-list redesign
 * shipped in PR after the legacy expandable-cards layout. Uses a single
 * screen-scoped [ProfileViewModel] (ADR-026 — one VM per screen).
 *
 * The function name is preserved (`ProfileContent`) to avoid touching
 * `Screen.Profile` and the bottom-nav definition; the user-facing tab
 * label is "Settings".
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ProfileContent(
    modifier: Modifier = Modifier,
    profileViewModel: ProfileViewModel? = null,
    onNavigateToDiagnostics: () -> Unit = {},
    onNavigateToFunctionList: () -> Unit = {},
) {
    val component = rememberAppComponent()
    val resolved = profileViewModel ?: viewModel { component.profileViewModel }
    val notificationPermissionState = rememberNotificationPermissionState()
    val googleSignIn = rememberGoogleSignIn(
        onTokenReceived = { token -> resolved.signInWithGoogle(token) },
    )

    val authState by resolved.authState.collectAsState()
    val snoozeState by resolved.snoozeState.collectAsState()
    val snoozeAction by resolved.snoozeAction.collectAsState()
    val functionListAccess by resolved.functionListAccess.collectAsState()
    val developerAccess by resolved.developerAccess.collectAsState()
    val layoutDebugEnabled by resolved.layoutDebugEnabled.collectAsState()
    val navigationRailItemPosition by resolved.navigationRailItemPosition.collectAsState()
    val navigationRailTopPaddingDp by resolved.navigationRailTopPaddingDp.collectAsState()
    val watchAppStatus by resolved.watchAppStatus.collectAsState()
    val watchInstallAction by resolved.watchInstallAction.collectAsState()
    val appConfig = component.appConfig
    val context = LocalContext.current
    // For click-time getString with runtime format args: LocalResources tracks
    // Configuration changes, unlike context.resources (Compose 1.9's
    // LocalContextGetResourceValueCall lint).
    val resources = LocalResources.current
    val clipboardManager = LocalClipboardManager.current
    val appVersion = context.AppVersion()

    // Surface-level state: which sheet/dialog is currently open. Saveable
    // so the user's mid-selection (snooze duration, account view) survives
    // rotation, window resize, and process death. Auto-reopening a sheet
    // after a process restart is mild and acceptable; closing one out from
    // under the user on rotation is not.
    var snoozeSheetOpen by rememberSaveable { mutableStateOf(false) }
    var accountSheetOpen by rememberSaveable { mutableStateOf(false) }
    var versionSheetOpen by rememberSaveable { mutableStateOf(false) }
    var navRailSheetOpen by rememberSaveable { mutableStateOf(false) }

    // TTL-gated revalidate, once per screen entry (STATUS_CACHE_PLAN.md
    // D3). The cached snooze state renders instantly (hydrated across
    // process restarts); the fetch fires only when the last server
    // round-trip is stale. The per-minute poll this replaces existed to
    // catch expiry (now a shared LiveClock derivation) and server-side
    // voiding (now the FCM door-event hook + this revalidate).
    LaunchedEffect(Unit) {
        resolved.revalidateSnoozeIfStale()
    }

    // Snackbar host for surfacing snooze save failures. Before this, the
    // `SnoozeAction.Failed.*` states were computed by [ProfileViewModel]
    // and auto-reset by `scheduleActionReset()` but were never rendered
    // anywhere — failures were silent (the spinner just stopped). The
    // hostState + LaunchedEffect below dispatch a per-variant message
    // on each transition into a Failed.* state. See docs/SNOOZE_BEHAVIOR.md.
    val snackbarHostState = remember { SnackbarHostState() }
    val snoozeFailedEventChangedMessage = stringResource(R.string.snooze_failed_event_changed)
    val snoozeFailedNetworkMessage = stringResource(R.string.snooze_failed_network)
    val snoozeFailedNotAuthenticatedMessage = stringResource(R.string.snooze_failed_not_authenticated)
    val snoozeFailedMissingDataMessage = stringResource(R.string.snooze_failed_missing_data)
    LaunchedEffect(snoozeAction) {
        val action = snoozeAction
        if (action is SnoozeAction.Failed) {
            val message = when (action) {
                SnoozeAction.Failed.EventChanged -> snoozeFailedEventChangedMessage
                SnoozeAction.Failed.NetworkError -> snoozeFailedNetworkMessage
                SnoozeAction.Failed.NotAuthenticated -> snoozeFailedNotAuthenticatedMessage
                SnoozeAction.Failed.MissingData -> snoozeFailedMissingDataMessage
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    // Install-on-watch outcome: confirm success, or fall back to the
    // phone's own Play Store listing (its device picker can target the
    // watch) when the remote launch fails.
    val watchInstallOpenedMessage = stringResource(R.string.watch_install_opened_snackbar)
    val watchInstallFailedMessage = stringResource(R.string.watch_install_failed_snackbar)
    LaunchedEffect(watchInstallAction) {
        when (watchInstallAction) {
            WatchInstallAction.OpenedOnWatch -> snackbarHostState.showSnackbar(watchInstallOpenedMessage)
            WatchInstallAction.Failed -> {
                val url = "https://play.google.com/store/apps/details?id=${appVersion.packageName}"
                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                snackbarHostState.showSnackbar(watchInstallFailedMessage)
            }
            WatchInstallAction.Idle, WatchInstallAction.Sending -> Unit
        }
    }

    val unknownNameFallback = stringResource(R.string.profile_account_name_unknown)
    val accountState = when (val s = authState) {
        is AuthState.Authenticated -> AccountRowState.SignedIn(
            displayName = s.user.name
                .asString()
                .ifBlank { unknownNameFallback },
            email = s.user.email.asString(),
        )
        AuthState.Unauthenticated -> AccountRowState.SignedOut
        // Cold start: don't flash the sign-in CTA before Firebase resolves.
        AuthState.Unknown -> AccountRowState.Checking
    }
    val notificationsGranted = notificationPermissionState.status.isGranted
    val snoozeRowState = if (notificationsGranted) {
        ProfileContentHelpers.snoozeRowStateOf(snoozeState)
    } else {
        SnoozeRowState.PermissionDenied
    }

    Box(modifier = modifier) {
        SettingsContent(
            accountState = accountState,
            snoozeState = snoozeRowState,
            showSnoozeRow = appConfig.snoozeNotificationsOption,
            showDeveloperSection = developerAccess == true,
            showFunctionListRow = functionListAccess == true,
            watchAppStatus = watchAppStatus,
            versionName = appVersion.versionName,
            versionCode = appVersion.versionCode.toString(),
            layoutDebugEnabled = layoutDebugEnabled,
            navigationRailItemPosition = navigationRailItemPosition,
            navigationRailTopPaddingDp = navigationRailTopPaddingDp,
            snoozeInFlight = snoozeAction is SnoozeAction.Sending,
            watchInstallInFlight = watchInstallAction is WatchInstallAction.Sending,
            onInstallOnWatchTap = resolved::installOnWatch,
            onAccountTap = { accountSheetOpen = true },
            onSignInTap = { googleSignIn.launchSignIn() },
            onSnoozeTap = {
                if (notificationsGranted) {
                    // Force-refresh (not TTL-gated): the sheet pre-selects
                    // from the current state, and opening it is the one
                    // user gesture that deserves an immediate uncached
                    // fetch — the Android manual-refresh path now that the
                    // poll is gone (iOS keeps pull-to-refresh).
                    resolved.fetchSnoozeStatus()
                    snoozeSheetOpen = true
                } else {
                    notificationPermissionState.launchPermissionRequest()
                }
            },
            onFunctionListTap = onNavigateToFunctionList,
            onVersionTap = { versionSheetOpen = true },
            onPlayStoreTap = {
                val url = "https://play.google.com/store/apps/details?id=${appVersion.packageName}"
                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            },
            onPrivacyPolicyTap = {
                context.startActivity(Intent(Intent.ACTION_VIEW, AppLinks.PRIVACY_POLICY_URL.toUri()))
            },
            onDiagnosticsTap = onNavigateToDiagnostics,
            onLayoutDebugChange = resolved::setLayoutDebugEnabled,
            onNavRailTap = { navRailSheetOpen = true },
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (snoozeSheetOpen) {
        SnoozeBottomSheet(
            onSave = { duration ->
                resolved.snoozeOpenDoorsNotifications(duration)
            },
            onDismiss = { snoozeSheetOpen = false },
        )
    }

    if (accountSheetOpen) {
        val signedIn = accountState as? AccountRowState.SignedIn
        if (signedIn != null) {
            AccountBottomSheet(
                displayName = signedIn.displayName,
                email = signedIn.email,
                onSignOut = { resolved.signOut() },
                onDismiss = { accountSheetOpen = false },
            )
        }
    }

    if (versionSheetOpen) {
        VersionBottomSheet(
            versionName = appVersion.versionName,
            versionCode = appVersion.versionCode.toString(),
            buildTimestamp = appVersion.buildTimestamp,
            packageName = appVersion.packageName,
            onCopy = { label, value ->
                clipboardManager.setText(AnnotatedString(value))
                // Android 13+ (API 33) shows its own clipboard preview
                // chip after a `setText`. Showing a Toast on top of that
                // is duplicate noise — gate to older API levels only.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast
                        .makeText(
                            context,
                            resources.getString(R.string.profile_version_toast_copied, label),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            },
            onDismiss = { versionSheetOpen = false },
        )
    }

    if (navRailSheetOpen) {
        NavRailBottomSheet(
            itemPosition = navigationRailItemPosition,
            topPaddingDp = navigationRailTopPaddingDp,
            onItemPositionChange = resolved::setNavigationRailItemPosition,
            onItemPositionReset = resolved::resetNavigationRailItemPosition,
            onTopPaddingDpChange = resolved::setNavigationRailTopPaddingDp,
            onTopPaddingDpReset = resolved::resetNavigationRailTopPaddingDp,
            onDismiss = { navRailSheetOpen = false },
        )
    }

    ReportDrawn()
}

private object ProfileContentHelpers {
    private val snoozeTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    fun snoozeRowStateOf(state: SnoozeState): SnoozeRowState =
        when (state) {
            SnoozeState.Loading -> SnoozeRowState.Loading
            SnoozeState.NotSnoozing -> SnoozeRowState.Off
            is SnoozeState.Snoozing -> SnoozeRowState.SnoozingUntil(formatSnoozeTime(state.untilEpochSeconds))
        }

    fun formatSnoozeTime(epochSeconds: Long): String =
        Instant
            .ofEpochSecond(epochSeconds)
            .atZone(ZoneId.systemDefault())
            .format(snoozeTimeFormatter)
}
