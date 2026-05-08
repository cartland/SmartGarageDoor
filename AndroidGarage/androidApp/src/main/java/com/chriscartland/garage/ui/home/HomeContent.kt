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

package com.chriscartland.garage.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.SignalWifiOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.RemoteButtonState
import com.chriscartland.garage.permissions.NotificationPermissionCopy
import com.chriscartland.garage.ui.GarageIcon
import com.chriscartland.garage.ui.RemoteButtonContent
import com.chriscartland.garage.ui.RemoteButtonHealthPill
import com.chriscartland.garage.ui.TitleBarCheckInPill
import com.chriscartland.garage.ui.theme.ButtonSpacing
import com.chriscartland.garage.ui.theme.CardPadding
import com.chriscartland.garage.ui.theme.DoorColorState
import com.chriscartland.garage.ui.theme.LocalDoorStatusColorScheme
import com.chriscartland.garage.ui.theme.ParagraphSpacing
import com.chriscartland.garage.ui.theme.PreviewScreenSurface
import com.chriscartland.garage.ui.theme.Spacing
import com.chriscartland.garage.ui.theme.doorColorSet
import com.chriscartland.garage.ui.theme.doorColorState
import com.chriscartland.garage.usecase.ButtonHealthDisplay

/**
 * Display data for the Home tab's status card.
 *
 * Stateless: every string is pre-formatted by the caller so screenshot tests
 * remain deterministic and the Composable carries no time math.
 *
 * @param doorPosition drives the [GarageIcon] visual + door coloring.
 * @param stateLabel headline text — e.g. "Open", "Closed", "Opening".
 * @param sinceLine combined status line — e.g. "Since 9:47 AM · 2 hr 14 min".
 *   Replaces the two separate icon-text rows from the legacy
 *   `DoorStatusCard`.
 * @param warning optional warning supporting text, surfaced for
 *   `OPENING_TOO_LONG`, `OPEN_MISALIGNED`, `CLOSING_TOO_LONG`,
 *   `ERROR_SENSOR_CONFLICT`, etc.
 */
data class HomeStatusDisplay(
    val doorPosition: DoorPosition,
    val stateLabel: String,
    val sinceLine: String,
    val warning: String? = null,
    /** Drives the muted "stale" door color and disables animation when true. */
    val isStale: Boolean = false,
)

/** Auth-gated bottom card variant. */
sealed interface HomeAuthState {
    /** Auth listener still resolving. */
    data object Unknown : HomeAuthState

    /** Show the sign-in card (matches Settings' SignedOut row aesthetic). */
    data object SignedOut : HomeAuthState

    /** Show the [RemoteButtonContent] inside the action card. */
    data object SignedIn : HomeAuthState
}

/**
 * Banner stack that appears above the Status card.
 *
 * All variants render with the same rounded-large surface so the visual
 * weight is consistent. Color carries the severity:
 * `errorContainer` for things the user can fix, `tertiaryContainer` for
 * informational alerts.
 */
sealed interface HomeAlert {
    /** Door telemetry hasn't arrived recently — server may be down. */
    data class Stale(
        val message: String = "Not receiving updates from server",
        val actionLabel: String = "Retry",
    ) : HomeAlert

    /** Notification permission denied/never asked. */
    data class PermissionMissing(
        val message: String,
        val actionLabel: String = "Allow",
    ) : HomeAlert

    /** A door-event fetch failed. */
    data class FetchError(
        val message: String,
        val actionLabel: String = "Retry",
    ) : HomeAlert
}

/**
 * Sectioned-list Home tab — applies the Settings/History M3 visual language
 * (uppercase section headers, `surfaceContainer` rounded surfaces, ListItem
 * conventions, pull-to-refresh) while keeping the hero/action two-zone shape:
 * the door art and the remote button each need to remain visually
 * prominent — they are not list rows.
 *
 * Stateless: pass display data + lambdas. Caller is responsible for wiring
 * to ViewModels.
 *
 * @param isRefreshing drives the M3 pull-to-refresh spinner. Production
 *   callers tie this to the `LoadingResult.Loading` flag of the underlying
 *   door event.
 * @param onRefresh fires when the user completes a downward pull.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    status: HomeStatusDisplay,
    authState: HomeAuthState,
    deviceCheckIn: DeviceCheckInDisplay,
    buttonHealthDisplay: ButtonHealthDisplay,
    modifier: Modifier = Modifier,
    remoteButtonState: RemoteButtonState = RemoteButtonState.Ready,
    alerts: List<HomeAlert> = emptyList(),
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onAlertAction: (HomeAlert) -> Unit = {},
    onRemoteButtonTap: () -> Unit = {},
    onSignIn: () -> Unit = {},
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = Spacing.ListVertical),
            verticalArrangement = Arrangement.spacedBy(Spacing.BetweenItems),
        ) {
            if (alerts.isNotEmpty()) {
                item(key = "alerts") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.BetweenItems)) {
                        alerts.forEach { alert ->
                            HomeAlertCard(
                                alert = alert,
                                onAction = { onAlertAction(alert) },
                            )
                        }
                    }
                }
            }

            item(key = "status") {
                HomeSection(
                    label = "Status",
                    trailing = { TitleBarCheckInPill(display = deviceCheckIn) },
                ) {
                    HomeStatusCardBody(status = status)
                }
            }

            item(key = "action") {
                when (authState) {
                    HomeAuthState.Unknown -> {
                        HomeSection(label = "Remote control") {
                            HomeAuthLoadingBody()
                        }
                    }
                    HomeAuthState.SignedOut -> {
                        HomeSection(label = "Sign in") {
                            HomeSignInBody(onSignIn = onSignIn)
                        }
                    }
                    HomeAuthState.SignedIn -> {
                        HomeSection(
                            label = "Remote control",
                            trailing = {
                                // TEMPORARY (debug): always-on pill for every ButtonHealthDisplay arm.
                                // To revert to production-only "Remote offline" behavior, swap back
                                // to `RemoteOfflinePill` (Offline-only) and delete RemoteButtonHealthPill.
                                RemoteButtonHealthPill(display = buttonHealthDisplay)
                            },
                        ) {
                            HomeRemoteButtonBody(
                                state = remoteButtonState,
                                onTap = onRemoteButtonTap,
                            )
                        }
                    }
                }
            }

            item(key = "tail-spacer") { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun HomeSection(
    label: String,
    trailing: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = Spacing.SectionHeaderStart,
                    end = Spacing.SectionHeaderStart,
                    top = Spacing.SectionHeaderTop,
                    bottom = Spacing.SectionHeaderBottom,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            trailing()
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large),
        ) {
            content()
        }
    }
}

@Composable
private fun HomeStatusCardBody(status: HomeStatusDisplay) {
    val colorSet = LocalDoorStatusColorScheme.current.doorColorSet(isStale = status.isStale)
    val doorColor = when (DoorEvent(doorPosition = status.doorPosition).doorColorState()) {
        DoorColorState.OPEN -> colorSet.open
        DoorColorState.CLOSED -> colorSet.closed
        DoorColorState.UNKNOWN -> colorSet.unknown
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(CardPadding.Tall),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Live state — animate motion (OPENING/CLOSING tween, terminal/error
            // spring). History rows stay `static = true` because they show past
            // snapshots, not the current motion.
            GarageIcon(
                doorPosition = status.doorPosition,
                static = false,
                color = doorColor,
                modifier = Modifier.size(160.dp),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
        ) {
            Text(
                text = status.stateLabel,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = status.sinceLine,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (status.warning != null) {
                Spacer(modifier = Modifier.height(Spacing.Tight))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = status.warning,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeRemoteButtonBody(
    state: RemoteButtonState,
    onTap: () -> Unit,
) {
    Box(modifier = Modifier.padding(CardPadding.Standard)) {
        RemoteButtonContent(
            state = state,
            onTap = onTap,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun HomeSignInBody(onSignIn: () -> Unit) {
    ListItem(
        leadingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Login,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        headlineContent = { Text("Sign in with Google") },
        supportingContent = { Text("Required to use the remote button") },
        trailingContent = {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.clickable { onSignIn() },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

@Composable
private fun HomeAuthLoadingBody() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Checking sign-in…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HomeAlertCard(
    alert: HomeAlert,
    onAction: () -> Unit,
) {
    val (icon, message, actionLabel) = when (alert) {
        is HomeAlert.Stale ->
            Triple(Icons.Outlined.SignalWifiOff, alert.message, alert.actionLabel)
        is HomeAlert.PermissionMissing ->
            Triple(Icons.Outlined.NotificationsActive, alert.message, alert.actionLabel)
        is HomeAlert.FetchError ->
            Triple(Icons.Outlined.WarningAmber, alert.message, alert.actionLabel)
    }
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large),
    ) {
        Row(
            modifier = Modifier.padding(CardPadding.Compact),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(ParagraphSpacing.IconToText))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(ButtonSpacing.Inline))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

// region Preview helpers

private object HomePreviewData {
    val openStatus = HomeStatusDisplay(
        doorPosition = DoorPosition.OPEN,
        stateLabel = "Open",
        sinceLine = "Since 9:47 AM · 2 hr 14 min",
    )
    val closedStatus = HomeStatusDisplay(
        doorPosition = DoorPosition.CLOSED,
        stateLabel = "Closed",
        sinceLine = "Since 11:22 AM · 38 min",
    )
    val openingTooLongStatus = HomeStatusDisplay(
        doorPosition = DoorPosition.OPENING_TOO_LONG,
        stateLabel = "Opening",
        sinceLine = "Since 12:01 PM · 4 min",
        warning = "Taking longer than expected — check the door for obstructions",
    )
    val staleAlert = HomeAlert.Stale()
    val permissionAlert = HomeAlert.PermissionMissing(
        message = NotificationPermissionCopy.justificationText(0),
    )

    // Heartbeat cadence is ~10 min, so a representative typical pill reads
    // ~5 min — comfortably under the 11-min staleness threshold.
    val freshCheckIn = DeviceCheckInDisplay(
        durationLabel = "5 min ago",
        isStale = false,
    )
    val staleCheckIn = DeviceCheckInDisplay(
        durationLabel = "23 min ago",
        isStale = true,
    )
}

@Preview(heightDp = 900)
@Composable
fun HomeContentOpenSignedInPreview() =
    PreviewScreenSurface {
        HomeContent(
            status = HomePreviewData.openStatus,
            authState = HomeAuthState.SignedIn,
            remoteButtonState = RemoteButtonState.Ready,
            deviceCheckIn = HomePreviewData.freshCheckIn,
            buttonHealthDisplay = ButtonHealthDisplay.Loading,
            modifier = Modifier.padding(horizontal = Spacing.Screen),
        )
    }

@Preview(heightDp = 900)
@Composable
fun HomeContentClosedSignedInPreview() =
    PreviewScreenSurface {
        HomeContent(
            status = HomePreviewData.closedStatus,
            authState = HomeAuthState.SignedIn,
            remoteButtonState = RemoteButtonState.Ready,
            deviceCheckIn = HomePreviewData.freshCheckIn,
            buttonHealthDisplay = ButtonHealthDisplay.Loading,
            modifier = Modifier.padding(horizontal = Spacing.Screen),
        )
    }

@Preview(heightDp = 900)
@Composable
fun HomeContentAwaitingConfirmationPreview() =
    PreviewScreenSurface {
        HomeContent(
            status = HomePreviewData.closedStatus,
            authState = HomeAuthState.SignedIn,
            remoteButtonState = RemoteButtonState.AwaitingConfirmation,
            deviceCheckIn = HomePreviewData.freshCheckIn,
            buttonHealthDisplay = ButtonHealthDisplay.Loading,
            modifier = Modifier.padding(horizontal = Spacing.Screen),
        )
    }

@Preview(heightDp = 900)
@Composable
fun HomeContentSendingToDoorPreview() =
    PreviewScreenSurface {
        HomeContent(
            status = HomePreviewData.closedStatus,
            authState = HomeAuthState.SignedIn,
            remoteButtonState = RemoteButtonState.SendingToDoor,
            deviceCheckIn = HomePreviewData.freshCheckIn,
            buttonHealthDisplay = ButtonHealthDisplay.Loading,
            modifier = Modifier.padding(horizontal = Spacing.Screen),
        )
    }

@Preview(heightDp = 900)
@Composable
fun HomeContentOpeningTooLongPreview() =
    PreviewScreenSurface {
        HomeContent(
            status = HomePreviewData.openingTooLongStatus,
            authState = HomeAuthState.SignedIn,
            remoteButtonState = RemoteButtonState.Ready,
            deviceCheckIn = HomePreviewData.freshCheckIn,
            buttonHealthDisplay = ButtonHealthDisplay.Loading,
            modifier = Modifier.padding(horizontal = Spacing.Screen),
        )
    }

@Preview(heightDp = 900)
@Composable
fun HomeContentStaleBannerPreview() =
    PreviewScreenSurface {
        HomeContent(
            status = HomePreviewData.openStatus,
            authState = HomeAuthState.SignedIn,
            remoteButtonState = RemoteButtonState.Ready,
            alerts = listOf(HomePreviewData.staleAlert),
            deviceCheckIn = HomePreviewData.staleCheckIn,
            buttonHealthDisplay = ButtonHealthDisplay.Loading,
            modifier = Modifier.padding(horizontal = Spacing.Screen),
        )
    }

@Preview(heightDp = 900)
@Composable
fun HomeContentPermissionMissingPreview() =
    PreviewScreenSurface {
        HomeContent(
            status = HomePreviewData.openStatus,
            authState = HomeAuthState.SignedIn,
            remoteButtonState = RemoteButtonState.Ready,
            alerts = listOf(HomePreviewData.permissionAlert),
            deviceCheckIn = HomePreviewData.freshCheckIn,
            buttonHealthDisplay = ButtonHealthDisplay.Loading,
            modifier = Modifier.padding(horizontal = Spacing.Screen),
        )
    }

@Preview(heightDp = 900)
@Composable
fun HomeContentSignedOutPreview() =
    PreviewScreenSurface {
        HomeContent(
            status = HomePreviewData.openStatus,
            authState = HomeAuthState.SignedOut,
            deviceCheckIn = HomePreviewData.freshCheckIn,
            buttonHealthDisplay = ButtonHealthDisplay.Loading,
            modifier = Modifier.padding(horizontal = Spacing.Screen),
        )
    }

// Full-screen previews showing the always-on RemoteButtonHealthPill for
// each ButtonHealthDisplay arm in its real position (Remote control
// HomeSection trailing slot). One per arm so you can review pill-in-context
// at a glance.

@Preview(heightDp = 900)
@Composable
fun HomeContentRemotePillUnauthorizedPreview() =
    PreviewScreenSurface {
        HomeContent(
            status = HomePreviewData.closedStatus,
            authState = HomeAuthState.SignedIn,
            remoteButtonState = RemoteButtonState.Ready,
            deviceCheckIn = HomePreviewData.freshCheckIn,
            buttonHealthDisplay = ButtonHealthDisplay.Unauthorized,
            modifier = Modifier.padding(horizontal = Spacing.Screen),
        )
    }

@Preview(heightDp = 900)
@Composable
fun HomeContentRemotePillLoadingPreview() =
    PreviewScreenSurface {
        HomeContent(
            status = HomePreviewData.closedStatus,
            authState = HomeAuthState.SignedIn,
            remoteButtonState = RemoteButtonState.Ready,
            deviceCheckIn = HomePreviewData.freshCheckIn,
            buttonHealthDisplay = ButtonHealthDisplay.Loading,
            modifier = Modifier.padding(horizontal = Spacing.Screen),
        )
    }

@Preview(heightDp = 900)
@Composable
fun HomeContentRemotePillUnknownPreview() =
    PreviewScreenSurface {
        HomeContent(
            status = HomePreviewData.closedStatus,
            authState = HomeAuthState.SignedIn,
            remoteButtonState = RemoteButtonState.Ready,
            deviceCheckIn = HomePreviewData.freshCheckIn,
            buttonHealthDisplay = ButtonHealthDisplay.Unknown,
            modifier = Modifier.padding(horizontal = Spacing.Screen),
        )
    }

@Preview(heightDp = 900)
@Composable
fun HomeContentRemotePillOnlinePreview() =
    PreviewScreenSurface {
        HomeContent(
            status = HomePreviewData.closedStatus,
            authState = HomeAuthState.SignedIn,
            remoteButtonState = RemoteButtonState.Ready,
            deviceCheckIn = HomePreviewData.freshCheckIn,
            buttonHealthDisplay = ButtonHealthDisplay.Online,
            modifier = Modifier.padding(horizontal = Spacing.Screen),
        )
    }

@Preview(heightDp = 900)
@Composable
fun HomeContentRemotePillOfflinePreview() =
    PreviewScreenSurface {
        HomeContent(
            status = HomePreviewData.closedStatus,
            authState = HomeAuthState.SignedIn,
            remoteButtonState = RemoteButtonState.Ready,
            deviceCheckIn = HomePreviewData.freshCheckIn,
            buttonHealthDisplay = ButtonHealthDisplay.Offline(durationLabel = "11 min ago"),
            modifier = Modifier.padding(horizontal = Spacing.Screen),
        )
    }

// endregion
