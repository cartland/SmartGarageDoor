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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chriscartland.garage.auth.rememberGoogleSignIn
import com.chriscartland.garage.di.rememberAppComponent
import com.chriscartland.garage.ui.theme.PreviewScreenSurface
import com.chriscartland.garage.ui.theme.Spacing
import com.chriscartland.garage.usecase.FunctionListViewModel

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
) {
    if (accessGranted == true) {
        LazyColumn(
            modifier = modifier,
            contentPadding = Spacing.ListContentPadding,
            verticalArrangement = Arrangement.spacedBy(Spacing.BetweenItems),
        ) {
            item { FunctionListWarning() }
            item { FunctionButton("Open or close garage door", onOpenOrCloseDoor) }
            item { FunctionButton("Refresh door status", onRefreshDoorStatus) }
            item { FunctionButton("Refresh door history", onRefreshDoorHistory) }
            item { FunctionButton("Refresh snooze status", onRefreshSnoozeStatus) }
            item { FunctionButton("Refresh button health", onRefreshButtonHealth) }
            item { FunctionButton("Snooze notifications for 1 hour", onSnoozeOneHour) }
            item { FunctionButton("Sign in with Google", onSignIn) }
            item { FunctionButton("Sign out", onSignOut) }
            item { FunctionButton("Clear all diagnostics", onClearDiagnostics) }
            item { FunctionButton("Prune diagnostics log", onPruneDiagnosticsLog) }
            item { FunctionButton("Re-register FCM", onRegisterFcm) }
            item { FunctionButton("Deregister FCM", onDeregisterFcm) }
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
        text = "Each button below performs a real action immediately — no " +
            "confirmation prompts. Tapping triggers calls to the server, " +
            "modifies app state, or wipes local data. Double-check the label " +
            "before tapping.",
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
            text = "Access not enabled for your account.",
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
        )
    }
}
