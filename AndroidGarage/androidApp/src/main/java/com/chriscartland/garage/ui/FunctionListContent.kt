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

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chriscartland.garage.R
import com.chriscartland.garage.auth.rememberGoogleSignIn
import com.chriscartland.garage.di.rememberAppComponent
import com.chriscartland.garage.ui.auth.rememberAuthTokenCopier
import com.chriscartland.garage.ui.theme.PreviewScreenSurface
import com.chriscartland.garage.ui.theme.Spacing
import com.chriscartland.garage.ui.theme.safeListContentPadding
import com.chriscartland.garage.viewmodel.FunctionListViewModel

@Composable
fun FunctionListContent(
    modifier: Modifier = Modifier,
    viewModel: FunctionListViewModel? = null,
) {
    val component = rememberAppComponent()
    val resolved = viewModel ?: viewModel { component.functionListViewModel }
    val accessGranted by resolved.accessGranted.collectAsState()
    val googleSignIn = rememberGoogleSignIn(
        onTokenReceived = { token -> resolved.signInWithGoogle(token) },
    )
    val copyAuthToken = rememberAuthTokenCopier()

    FunctionListContent(
        modifier = modifier,
        accessGranted = accessGranted,
        onOpenOrCloseDoor = resolved::openOrCloseDoor,
        onRefreshDoorStatus = resolved::refreshDoorStatus,
        onRefreshDoorHistory = resolved::refreshDoorHistory,
        onRefreshSnoozeStatus = resolved::refreshSnoozeStatus,
        onRefreshButtonHealth = resolved::refreshButtonHealth,
        onSnoozeOneHour = resolved::snoozeNotificationsForOneHour,
        onSignIn = { googleSignIn.launchSignIn() },
        onSignOut = resolved::signOut,
        onClearDiagnostics = resolved::clearDiagnostics,
        onPruneDiagnosticsLog = resolved::pruneDiagnosticsLog,
        onRegisterFcm = resolved::registerFcm,
        onDeregisterFcm = resolved::deregisterFcm,
        onCopyAuthToken = copyAuthToken,
    )
}

@Composable
fun FunctionListContent(
    modifier: Modifier = Modifier,
    accessGranted: Boolean? = null,
    onOpenOrCloseDoor: () -> Unit = {},
    onRefreshDoorStatus: () -> Unit = {},
    onRefreshDoorHistory: () -> Unit = {},
    onRefreshSnoozeStatus: () -> Unit = {},
    onRefreshButtonHealth: () -> Unit = {},
    onSnoozeOneHour: () -> Unit = {},
    onSignIn: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onClearDiagnostics: () -> Unit = {},
    onPruneDiagnosticsLog: () -> Unit = {},
    onRegisterFcm: () -> Unit = {},
    onDeregisterFcm: () -> Unit = {},
    onCopyAuthToken: () -> Unit = {},
) {
    if (accessGranted == true) {
        LazyColumn(
            modifier = modifier,
            contentPadding = safeListContentPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.BetweenItems),
        ) {
            item { FunctionListWarning() }
            item { FunctionButton(stringResource(R.string.function_list_door_action), onOpenOrCloseDoor) }
            item { FunctionButton(stringResource(R.string.function_list_refresh_door_status), onRefreshDoorStatus) }
            item { FunctionButton(stringResource(R.string.function_list_refresh_door_history), onRefreshDoorHistory) }
            item { FunctionButton(stringResource(R.string.function_list_refresh_snooze_status), onRefreshSnoozeStatus) }
            item { FunctionButton(stringResource(R.string.function_list_refresh_button_health), onRefreshButtonHealth) }
            item { FunctionButton(stringResource(R.string.function_list_snooze_one_hour), onSnoozeOneHour) }
            item { FunctionButton(stringResource(R.string.function_list_sign_in_google), onSignIn) }
            item { FunctionButton(stringResource(R.string.function_list_sign_out), onSignOut) }
            item { FunctionButton(stringResource(R.string.function_list_clear_diagnostics), onClearDiagnostics) }
            item { FunctionButton(stringResource(R.string.function_list_prune_diagnostics), onPruneDiagnosticsLog) }
            item { FunctionButton(stringResource(R.string.function_list_register_fcm), onRegisterFcm) }
            item { FunctionButton(stringResource(R.string.function_list_deregister_fcm), onDeregisterFcm) }
            // API-gated to Android 13+ — same redaction-flag rationale as the
            // Diagnostics surface (see AuthTokenCopier kdoc). Both screens
            // share rememberAuthTokenCopier() so a regression in the
            // clipboard / token-fetch path breaks both at once, which is
            // the verification property the user asked for.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item { FunctionButton(stringResource(R.string.function_list_copy_auth_token), onCopyAuthToken) }
            }
        }
    } else {
        // null = unknown / fetch in flight; false = server denied. Both
        // gate-closed; UI text doesn't distinguish — the user reads the
        // same message either way and the resolved state lands within a
        // few hundred ms of sign-in.
        FunctionListAccessDeniedContent(modifier = modifier)
    }
}

@Composable
private fun FunctionListWarning() {
    Text(
        text = stringResource(R.string.function_list_warning),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    )
}

@Composable
private fun FunctionListAccessDeniedContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.function_list_access_denied),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FunctionButton(
    label: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = label)
    }
}

@Preview
@Composable
fun FunctionListContentPreview() {
    // Pass explicit lambdas so Kotlin picks the stateless inner overload —
    // the DI wrapper above this calls rememberAppComponent(), which crashes
    // Layoutlib in screenshot tests.
    PreviewScreenSurface {
        FunctionListContent(
            accessGranted = true,
            onOpenOrCloseDoor = {},
            onRefreshDoorStatus = {},
            onRefreshDoorHistory = {},
            onRefreshSnoozeStatus = {},
            onRefreshButtonHealth = {},
            onSnoozeOneHour = {},
            onSignIn = {},
            onSignOut = {},
            onClearDiagnostics = {},
            onPruneDiagnosticsLog = {},
            onRegisterFcm = {},
            onDeregisterFcm = {},
            onCopyAuthToken = {},
        )
    }
}

@Preview
@Composable
fun FunctionListContentDeniedPreview() {
    PreviewScreenSurface {
        FunctionListContent(
            accessGranted = false,
            onOpenOrCloseDoor = {},
            onRefreshDoorStatus = {},
            onRefreshDoorHistory = {},
            onRefreshSnoozeStatus = {},
            onRefreshButtonHealth = {},
            onSnoozeOneHour = {},
            onSignIn = {},
            onSignOut = {},
            onClearDiagnostics = {},
            onPruneDiagnosticsLog = {},
            onRegisterFcm = {},
            onDeregisterFcm = {},
            onCopyAuthToken = {},
        )
    }
}
