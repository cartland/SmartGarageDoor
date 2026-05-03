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

package com.chriscartland.garage.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsPaused
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Account section row state. Determines whether the row shows a sign-in
 * CTA or the signed-in user identity.
 */
sealed interface AccountRowState {
    data object SignedOut : AccountRowState

    data class SignedIn(
        val displayName: String,
        val email: String,
    ) : AccountRowState
}

/**
 * Snooze row secondary-text state.
 */
sealed interface SnoozeRowState {
    data object Loading : SnoozeRowState

    data object Off : SnoozeRowState

    data class SnoozingUntil(
        val displayTime: String,
    ) : SnoozeRowState
}

/**
 * Sectioned-list Settings screen — Direction A from the redesign.
 *
 * Stateless: every piece of data is parameterized; every action is a
 * lambda. Caller is responsible for wiring to ViewModels and showing
 * the bottom sheets / dialogs / sub-screens reached from each row.
 */
@Composable
fun SettingsContent(
    accountState: AccountRowState,
    snoozeState: SnoozeRowState,
    showSnoozeRow: Boolean,
    showToolsSection: Boolean,
    showDiagnosticsRow: Boolean,
    versionName: String,
    versionCode: String,
    modifier: Modifier = Modifier,
    onAccountTap: () -> Unit = {},
    onSignInTap: () -> Unit = {},
    onSnoozeTap: () -> Unit = {},
    onFunctionListTap: () -> Unit = {},
    onVersionTap: () -> Unit = {},
    onPlayStoreTap: () -> Unit = {},
    onPrivacyPolicyTap: () -> Unit = {},
    onDiagnosticsTap: () -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SettingsSection(label = "Account") {
                when (accountState) {
                    AccountRowState.SignedOut -> SettingsRow(
                        icon = Icons.AutoMirrored.Outlined.Login,
                        title = "Sign in with Google",
                        subtitle = null,
                        showChevron = false,
                        onClick = onSignInTap,
                    )
                    is AccountRowState.SignedIn -> SettingsRow(
                        icon = Icons.Filled.AccountCircle,
                        title = accountState.displayName,
                        subtitle = accountState.email,
                        showChevron = true,
                        onClick = onAccountTap,
                    )
                }
            }
        }

        if (showSnoozeRow) {
            item {
                SettingsSection(label = "Notifications") {
                    val (icon, subtitle) = when (snoozeState) {
                        SnoozeRowState.Loading -> Icons.Outlined.Notifications to "—"
                        SnoozeRowState.Off -> Icons.Outlined.Notifications to "Off"
                        is SnoozeRowState.SnoozingUntil ->
                            Icons.Outlined.NotificationsPaused to "Snoozing until ${snoozeState.displayTime}"
                    }
                    SettingsRow(
                        icon = icon,
                        title = "Snooze",
                        subtitle = subtitle,
                        showChevron = true,
                        onClick = onSnoozeTap,
                    )
                }
            }
        }

        item {
            SettingsSection(label = "About") {
                SettingsRow(
                    icon = Icons.Outlined.Info,
                    title = "Version",
                    subtitle = "$versionName (build $versionCode)",
                    showChevron = true,
                    onClick = onVersionTap,
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                SettingsRow(
                    icon = Icons.Outlined.Storefront,
                    title = "Play Store",
                    subtitle = null,
                    trailingIcon = Icons.AutoMirrored.Outlined.OpenInNew,
                    showChevron = false,
                    onClick = onPlayStoreTap,
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                SettingsRow(
                    icon = Icons.Outlined.Description,
                    title = "Privacy Policy",
                    subtitle = null,
                    trailingIcon = Icons.AutoMirrored.Outlined.OpenInNew,
                    showChevron = false,
                    onClick = onPrivacyPolicyTap,
                )
                if (showDiagnosticsRow) {
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                    SettingsRow(
                        icon = Icons.Outlined.Analytics,
                        title = "Diagnostics",
                        subtitle = null,
                        showChevron = true,
                        onClick = onDiagnosticsTap,
                    )
                }
            }
        }

        item {
            AnimatedVisibility(visible = showToolsSection) {
                SettingsSection(label = "Tools") {
                    SettingsRow(
                        icon = Icons.AutoMirrored.Outlined.List,
                        title = "Function list",
                        subtitle = null,
                        showChevron = true,
                        onClick = onFunctionListTap,
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun SettingsSection(
    label: String,
    content: @Composable () -> Unit,
) {
    // No horizontal padding here — the parent screen already provides
    // 16dp via Main.kt's nav-entry wrapper, which is the Material 3 phone
    // default and matches the TopAppBar title's left edge. The section
    // label inset (start = 16dp below) keeps it visually aligned with
    // the ListItem content text inside the Surface — Material settings
    // convention.
    Column {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large),
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    showChevron: Boolean,
    onClick: () -> Unit,
    trailingIcon: ImageVector? = null,
) {
    ListItem(
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = when {
            trailingIcon != null -> {
                {
                    Icon(
                        imageVector = trailingIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            showChevron -> {
                {
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> null
        },
        modifier = Modifier.clickable { onClick() },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    )
}

// Wrapper-Composable preview helpers used by the screenshot test file.

@Preview
@Composable
fun SettingsContentSignedOutPreview() {
    SettingsContent(
        accountState = AccountRowState.SignedOut,
        snoozeState = SnoozeRowState.Off,
        showSnoozeRow = true,
        showToolsSection = false,
        showDiagnosticsRow = true,
        versionName = "2.6.1",
        versionCode = "182",
    )
}

@Preview
@Composable
fun SettingsContentSignedInBasicPreview() {
    SettingsContent(
        accountState = AccountRowState.SignedIn(
            displayName = "Chris Cartland",
            email = "chris@example.com",
        ),
        snoozeState = SnoozeRowState.Off,
        showSnoozeRow = true,
        showToolsSection = false,
        showDiagnosticsRow = true,
        versionName = "2.6.1",
        versionCode = "182",
    )
}

@Preview
@Composable
fun SettingsContentSignedInAllowlistedPreview() {
    SettingsContent(
        accountState = AccountRowState.SignedIn(
            displayName = "Chris Cartland",
            email = "chris@example.com",
        ),
        snoozeState = SnoozeRowState.SnoozingUntil("5:30 PM"),
        showSnoozeRow = true,
        showToolsSection = true,
        showDiagnosticsRow = true,
        versionName = "2.6.1",
        versionCode = "182",
    )
}
