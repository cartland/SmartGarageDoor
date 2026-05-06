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
import androidx.activity.compose.ReportDrawn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chriscartland.garage.auth.rememberGoogleSignIn
import com.chriscartland.garage.di.rememberAppComponent
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.SnoozeDurationUIOption
import com.chriscartland.garage.domain.model.SnoozeState
import com.chriscartland.garage.permissions.rememberNotificationPermissionState
import com.chriscartland.garage.ui.settings.AccountBottomSheet
import com.chriscartland.garage.ui.settings.AccountRowState
import com.chriscartland.garage.ui.settings.SettingsContent
import com.chriscartland.garage.ui.settings.SnoozeBottomSheet
import com.chriscartland.garage.ui.settings.SnoozeRowState
import com.chriscartland.garage.ui.settings.VersionDialog
import com.chriscartland.garage.usecase.AppSettingsViewModel
import com.chriscartland.garage.usecase.AuthViewModel
import com.chriscartland.garage.usecase.RemoteButtonViewModel
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
 * shipped in PR after the legacy expandable-cards layout. Aggregates
 * AuthViewModel + RemoteButtonViewModel + AppSettingsViewModel
 * (legacy multi-VM exemption per ADR-026; the screen-level VM split
 * is deferred — see screen-viewmodel-exemptions.txt).
 *
 * The function name is preserved (`ProfileContent`) to avoid touching
 * `Screen.Profile` and the bottom-nav definition; the user-facing tab
 * label is "Settings".
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ProfileContent(
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel? = null,
    onNavigateToDiagnostics: () -> Unit = {},
    onNavigateToFunctionList: () -> Unit = {},
) {
    val component = rememberAppComponent()
    val resolvedAuthViewModel = authViewModel ?: viewModel { component.authViewModel }
    val buttonViewModel: RemoteButtonViewModel = viewModel { component.remoteButtonViewModel }
    val settingsViewModel: AppSettingsViewModel = viewModel { component.appSettingsViewModel }
    val notificationPermissionState = rememberNotificationPermissionState()
    val googleSignIn = rememberGoogleSignIn(
        onTokenReceived = { token -> resolvedAuthViewModel.signInWithGoogle(token) },
    )

    val authState by resolvedAuthViewModel.authState.collectAsState()
    val snoozeState by buttonViewModel.snoozeState.collectAsState()
    val functionListAccess by settingsViewModel.functionListAccess.collectAsState()
    val appConfig = component.appConfig
    val context = LocalContext.current
    val appVersion = context.AppVersion()

    // Surface-level state: which sheet/dialog is currently open. Local to
    // the screen — not persisted across process death (recreating the
    // sheet/dialog after a crash would surprise the user).
    var snoozeSheetOpen by remember { mutableStateOf(false) }
    var accountSheetOpen by remember { mutableStateOf(false) }
    var versionDialogOpen by remember { mutableStateOf(false) }

    // Refresh snooze status every minute while this screen is mounted.
    // Mirrors the legacy ProfileContent behavior; the polling cadence
    // covers both the row-secondary-text and the sheet's pre-selection.
    LaunchedEffect(Unit) {
        while (true) {
            buttonViewModel.fetchSnoozeStatus()
            delay(Duration.ofMinutes(1).toMillis())
        }
    }

    val accountState = when (val s = authState) {
        is AuthState.Authenticated -> AccountRowState.SignedIn(
            displayName = s.user.name
                .asString()
                .ifBlank { "(unknown)" },
            email = s.user.email.asString(),
        )
        AuthState.Unauthenticated, AuthState.Unknown -> AccountRowState.SignedOut
    }
    val snoozeRowState = ProfileContentHelpers.snoozeRowStateOf(snoozeState)

    SettingsContent(
        accountState = accountState,
        snoozeState = snoozeRowState,
        showSnoozeRow = appConfig.snoozeNotificationsOption &&
            notificationPermissionState.status.isGranted,
        showDeveloperSection = functionListAccess == true,
        versionName = appVersion.versionName,
        versionCode = appVersion.versionCode.toString(),
        modifier = modifier,
        onAccountTap = { accountSheetOpen = true },
        onSignInTap = { googleSignIn.launchSignIn() },
        onSnoozeTap = { snoozeSheetOpen = true },
        onFunctionListTap = onNavigateToFunctionList,
        onVersionTap = { versionDialogOpen = true },
        onPlayStoreTap = {
            val url = "https://play.google.com/store/apps/details?id=${appVersion.packageName}"
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        },
        onPrivacyPolicyTap = {
            context.startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_POLICY_URL.toUri()))
        },
        onDiagnosticsTap = onNavigateToDiagnostics,
    )

    if (snoozeSheetOpen) {
        SnoozeBottomSheet(
            initialSelection = if (snoozeState is SnoozeState.Snoozing) {
                SnoozeDurationUIOption.OneHour
            } else {
                SnoozeDurationUIOption.None
            },
            onSave = { duration ->
                buttonViewModel.snoozeOpenDoorsNotifications(duration)
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
                onSignOut = { resolvedAuthViewModel.signOut() },
                onDismiss = { accountSheetOpen = false },
            )
        }
    }

    if (versionDialogOpen) {
        VersionDialog(
            versionName = appVersion.versionName,
            versionCode = appVersion.versionCode.toString(),
            buildTimestamp = appVersion.buildTimestamp,
            packageName = appVersion.packageName,
            onDismiss = { versionDialogOpen = false },
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
