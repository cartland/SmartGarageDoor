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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chriscartland.garage.R
import com.chriscartland.garage.auth.rememberGoogleSignIn
import com.chriscartland.garage.di.rememberAppComponent
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.SnoozeAction
import com.chriscartland.garage.domain.model.SnoozeDurationUIOption
import com.chriscartland.garage.domain.model.SnoozeState
import com.chriscartland.garage.permissions.rememberNotificationPermissionState
import com.chriscartland.garage.ui.settings.AccountBottomSheet
import com.chriscartland.garage.ui.settings.AccountRowState
import com.chriscartland.garage.ui.settings.SettingsContent
import com.chriscartland.garage.ui.settings.SnoozeBottomSheet
import com.chriscartland.garage.ui.settings.SnoozeRowState
import com.chriscartland.garage.ui.settings.VersionBottomSheet
import com.chriscartland.garage.usecase.ProfileViewModel
import com.chriscartland.garage.version.AppVersion
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import kotlinx.coroutines.delay
import java.time.Duration
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
    val appConfig = component.appConfig
    val context = LocalContext.current
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

    // Refresh snooze status every minute while this screen is mounted.
    // Mirrors the legacy ProfileContent behavior; the polling cadence
    // covers both the row-secondary-text and the sheet's pre-selection.
    LaunchedEffect(Unit) {
        while (true) {
            resolved.fetchSnoozeStatus()
            delay(Duration.ofMinutes(1).toMillis())
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
        AuthState.Unauthenticated, AuthState.Unknown -> AccountRowState.SignedOut
    }
    val notificationsGranted = notificationPermissionState.status.isGranted
    val snoozeRowState = if (notificationsGranted) {
        ProfileContentHelpers.snoozeRowStateOf(snoozeState)
    } else {
        SnoozeRowState.PermissionDenied
    }

    SettingsContent(
        accountState = accountState,
        snoozeState = snoozeRowState,
        showSnoozeRow = appConfig.snoozeNotificationsOption,
        showDeveloperSection = developerAccess == true,
        showFunctionListRow = functionListAccess == true,
        versionName = appVersion.versionName,
        versionCode = appVersion.versionCode.toString(),
        layoutDebugEnabled = layoutDebugEnabled,
        navigationRailItemPosition = navigationRailItemPosition,
        modifier = modifier,
        snoozeInFlight = snoozeAction is SnoozeAction.Sending,
        onAccountTap = { accountSheetOpen = true },
        onSignInTap = { googleSignIn.launchSignIn() },
        onSnoozeTap = {
            if (notificationsGranted) {
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
            context.startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_POLICY_URL.toUri()))
        },
        onDiagnosticsTap = onNavigateToDiagnostics,
        onLayoutDebugChange = resolved::setLayoutDebugEnabled,
        onNavigationRailItemPositionChange = resolved::setNavigationRailItemPosition,
    )

    if (snoozeSheetOpen) {
        SnoozeBottomSheet(
            initialSelection = SnoozeDurationUIOption.None,
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
                            context.getString(R.string.profile_version_toast_copied, label),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            },
            onDismiss = { versionSheetOpen = false },
        )
    }

    ReportDrawn()
}

private const val PRIVACY_POLICY_URL = "https://chriscart.land/garage-privacy-policy"

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
