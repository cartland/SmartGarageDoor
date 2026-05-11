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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.NotificationsPaused
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.VerticalAlignCenter
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chriscartland.garage.R
import com.chriscartland.garage.domain.model.NavigationRailItemPosition
import com.chriscartland.garage.ui.theme.AppAnimatedVisibility
import com.chriscartland.garage.ui.theme.DividerInset
import com.chriscartland.garage.ui.theme.PreviewScreenSurface
import com.chriscartland.garage.ui.theme.Spacing
import com.chriscartland.garage.ui.theme.safeListContentPadding

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

    /** Notifications permission has not been granted — row prompts to enable. */
    data object PermissionDenied : SnoozeRowState

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
    showDeveloperSection: Boolean,
    showFunctionListRow: Boolean,
    versionName: String,
    versionCode: String,
    layoutDebugEnabled: Boolean,
    navigationRailItemPosition: NavigationRailItemPosition,
    navigationRailTopPaddingDp: Int,
    modifier: Modifier = Modifier,
    snoozeInFlight: Boolean = false,
    onAccountTap: () -> Unit = {},
    onSignInTap: () -> Unit = {},
    onSnoozeTap: () -> Unit = {},
    onFunctionListTap: () -> Unit = {},
    onVersionTap: () -> Unit = {},
    onPlayStoreTap: () -> Unit = {},
    onPrivacyPolicyTap: () -> Unit = {},
    onDiagnosticsTap: () -> Unit = {},
    onLayoutDebugChange: (Boolean) -> Unit = {},
    onNavRailTap: () -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = safeListContentPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.BetweenItems),
    ) {
        item {
            SettingsSection(label = stringResource(R.string.settings_section_account)) {
                when (accountState) {
                    AccountRowState.SignedOut -> SettingsRow(
                        icon = Icons.AutoMirrored.Outlined.Login,
                        title = stringResource(R.string.settings_account_sign_in),
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
                SettingsSection(label = stringResource(R.string.settings_section_notifications)) {
                    val (icon, subtitle) = when (snoozeState) {
                        SnoozeRowState.Loading ->
                            Icons.Outlined.Notifications to
                                stringResource(R.string.settings_notifications_subtitle_loading)
                        SnoozeRowState.PermissionDenied ->
                            Icons.Outlined.NotificationsOff to
                                stringResource(R.string.settings_notifications_subtitle_permission_denied)
                        SnoozeRowState.Off ->
                            Icons.Outlined.Notifications to
                                stringResource(R.string.settings_notifications_subtitle_off)
                        is SnoozeRowState.SnoozingUntil ->
                            Icons.Outlined.NotificationsPaused to
                                stringResource(
                                    R.string.settings_notifications_subtitle_snoozing_until,
                                    snoozeState.displayTime,
                                )
                    }
                    SettingsRow(
                        icon = icon,
                        title = stringResource(R.string.settings_notifications_row_title),
                        subtitle = subtitle,
                        showChevron = true,
                        onClick = onSnoozeTap,
                        inFlight = snoozeInFlight,
                    )
                }
            }
        }

        item {
            SettingsSection(label = stringResource(R.string.settings_section_about)) {
                SettingsRow(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.settings_about_version_title),
                    subtitle = stringResource(
                        R.string.settings_about_version_subtitle,
                        versionName,
                        versionCode,
                    ),
                    showChevron = true,
                    onClick = onVersionTap,
                )
                HorizontalDivider(modifier = Modifier.padding(start = DividerInset.ListItem))
                SettingsRow(
                    icon = Icons.Outlined.Storefront,
                    title = stringResource(R.string.settings_about_play_store_title),
                    subtitle = null,
                    trailingIcon = Icons.AutoMirrored.Outlined.OpenInNew,
                    showChevron = false,
                    onClick = onPlayStoreTap,
                )
                HorizontalDivider(modifier = Modifier.padding(start = DividerInset.ListItem))
                SettingsRow(
                    icon = Icons.Outlined.Description,
                    title = stringResource(R.string.settings_about_privacy_policy_title),
                    subtitle = null,
                    trailingIcon = Icons.AutoMirrored.Outlined.OpenInNew,
                    showChevron = false,
                    onClick = onPrivacyPolicyTap,
                )
            }
        }

        item {
            AppAnimatedVisibility(
                visible = showDeveloperSection,
                label = "Developer section",
            ) {
                SettingsSection(label = stringResource(R.string.settings_section_developer)) {
                    SettingsRow(
                        icon = Icons.Outlined.Analytics,
                        title = stringResource(R.string.settings_developer_diagnostics_title),
                        subtitle = null,
                        showChevron = true,
                        onClick = onDiagnosticsTap,
                    )
                    if (showFunctionListRow) {
                        HorizontalDivider(modifier = Modifier.padding(start = DividerInset.ListItem))
                        SettingsRow(
                            icon = Icons.AutoMirrored.Outlined.List,
                            title = stringResource(R.string.settings_developer_function_list_title),
                            subtitle = null,
                            showChevron = true,
                            onClick = onFunctionListTap,
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = DividerInset.ListItem))
                    SettingsSwitchRow(
                        icon = Icons.Outlined.Palette,
                        title = stringResource(R.string.settings_developer_layout_debug_title),
                        subtitle = stringResource(R.string.settings_developer_layout_debug_subtitle),
                        checked = layoutDebugEnabled,
                        onCheckedChange = onLayoutDebugChange,
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = DividerInset.ListItem))
                    val railPositionLabel = when (navigationRailItemPosition) {
                        NavigationRailItemPosition.CenteredVertically ->
                            stringResource(R.string.settings_developer_nav_rail_items_centered)
                        NavigationRailItemPosition.TopAligned ->
                            stringResource(R.string.settings_developer_nav_rail_items_top_aligned)
                    }
                    SettingsRow(
                        icon = Icons.Outlined.VerticalAlignCenter,
                        title = stringResource(R.string.settings_developer_nav_rail_row_title),
                        subtitle = stringResource(
                            R.string.settings_developer_nav_rail_row_subtitle,
                            railPositionLabel,
                            navigationRailTopPaddingDp,
                        ),
                        showChevron = true,
                        onClick = onNavRailTap,
                    )
                }
            }
        }
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
            modifier = Modifier.padding(
                start = Spacing.SectionHeaderStart,
                top = Spacing.SectionHeaderTop,
                bottom = Spacing.SectionHeaderBottom,
            ),
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
    inFlight: Boolean = false,
) {
    ListItem(
        leadingContent = {
            // When the row's action is in flight, ring a `CircularProgressIndicator`
            // around the leading icon. The icon stays at full alpha so the user
            // reads "current state, in flux" — not "loading from scratch."
            // Box is sized to fit the indicator (ring is slightly larger than
            // the 24dp icon so it doesn't overlap the glyph).
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (inFlight) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
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

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    // Same shape as SettingsRow but with a Switch trailing instead of a
    // chevron / icon. Tap anywhere on the row toggles, matching M3
    // settings convention. The Switch's own onCheckedChange is wired to
    // the same handler so dragging the thumb works too.
    ListItem(
        leadingContent = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
        modifier = Modifier.clickable { onCheckedChange(!checked) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    )
}

// Wrapper-Composable preview helpers used by the screenshot test file.

@Preview
@Composable
fun SettingsContentSignedOutPreview() {
    PreviewScreenSurface {
        SettingsContent(
            accountState = AccountRowState.SignedOut,
            snoozeState = SnoozeRowState.Off,
            showSnoozeRow = true,
            showDeveloperSection = false,
            showFunctionListRow = false,
            versionName = "2.6.1",
            versionCode = "182",
            layoutDebugEnabled = false,
            navigationRailItemPosition = NavigationRailItemPosition.CenteredVertically,
            navigationRailTopPaddingDp = 8,
        )
    }
}

@Preview
@Composable
fun SettingsContentSignedInBasicPreview() {
    PreviewScreenSurface {
        SettingsContent(
            accountState = AccountRowState.SignedIn(
                displayName = "Chris Cartland",
                email = "chris@example.com",
            ),
            snoozeState = SnoozeRowState.Off,
            showSnoozeRow = true,
            showDeveloperSection = false,
            showFunctionListRow = false,
            versionName = "2.6.1",
            versionCode = "182",
            layoutDebugEnabled = false,
            navigationRailItemPosition = NavigationRailItemPosition.CenteredVertically,
            navigationRailTopPaddingDp = 8,
        )
    }
}

@Preview
@Composable
fun SettingsContentSignedInAllowlistedPreview() {
    PreviewScreenSurface {
        SettingsContent(
            accountState = AccountRowState.SignedIn(
                displayName = "Chris Cartland",
                email = "chris@example.com",
            ),
            snoozeState = SnoozeRowState.SnoozingUntil("5:30 PM"),
            showSnoozeRow = true,
            showDeveloperSection = true,
            showFunctionListRow = true,
            versionName = "2.6.1",
            versionCode = "182",
            layoutDebugEnabled = false,
            navigationRailItemPosition = NavigationRailItemPosition.TopAligned,
            navigationRailTopPaddingDp = 8,
        )
    }
}

@Preview
@Composable
fun SettingsContentPermissionDeniedPreview() {
    PreviewScreenSurface {
        SettingsContent(
            accountState = AccountRowState.SignedIn(
                displayName = "Chris Cartland",
                email = "chris@example.com",
            ),
            snoozeState = SnoozeRowState.PermissionDenied,
            showSnoozeRow = true,
            showDeveloperSection = false,
            showFunctionListRow = false,
            versionName = "2.6.1",
            versionCode = "182",
            layoutDebugEnabled = false,
            navigationRailItemPosition = NavigationRailItemPosition.CenteredVertically,
            navigationRailTopPaddingDp = 8,
        )
    }
}

@Preview
@Composable
fun SettingsContentSnoozeInFlightPreview() {
    // Shows the snooze row with a CircularProgressIndicator ringing the
    // notifications icon while the snooze action is in flight. The icon
    // stays at full alpha so the row reads "current state, in flux".
    PreviewScreenSurface {
        SettingsContent(
            accountState = AccountRowState.SignedIn(
                displayName = "Chris Cartland",
                email = "chris@example.com",
            ),
            snoozeState = SnoozeRowState.Off,
            showSnoozeRow = true,
            showDeveloperSection = false,
            showFunctionListRow = false,
            versionName = "2.6.1",
            versionCode = "182",
            layoutDebugEnabled = false,
            navigationRailItemPosition = NavigationRailItemPosition.CenteredVertically,
            navigationRailTopPaddingDp = 8,
            snoozeInFlight = true,
        )
    }
}
