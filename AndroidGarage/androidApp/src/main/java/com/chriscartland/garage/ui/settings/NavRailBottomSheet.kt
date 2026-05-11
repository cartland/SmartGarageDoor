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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chriscartland.garage.R
import com.chriscartland.garage.domain.model.NavigationRailItemPosition

/**
 * Production wrapper. Hosts both Wide-mode NavigationRail developer
 * settings (item position + extra top padding) inside a single
 * [ModalBottomSheet] so the parent Settings → Developer list keeps
 * exactly one row for "Nav rail" instead of growing inline.
 *
 * The `*Content` Composable below is the previewable surface (the
 * sheet's show animation runs in a `LaunchedEffect` that doesn't fire
 * under `@Preview` / screenshot tests, so the content is split out).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavRailBottomSheet(
    itemPosition: NavigationRailItemPosition,
    topPaddingDp: Int,
    onItemPositionChange: (NavigationRailItemPosition) -> Unit,
    onItemPositionReset: () -> Unit,
    onTopPaddingDpChange: (Int) -> Unit,
    onTopPaddingDpReset: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        NavRailSheetContent(
            itemPosition = itemPosition,
            topPaddingDp = topPaddingDp,
            onItemPositionChange = onItemPositionChange,
            onItemPositionReset = onItemPositionReset,
            onTopPaddingDpChange = onTopPaddingDpChange,
            onTopPaddingDpReset = onTopPaddingDpReset,
        )
    }
}

/**
 * Sheet content extracted as a separate Composable so previews and
 * screenshot tests can render it directly. Two sections, each with a
 * header (title + trailing "Set default" text button) and an inline
 * control. Both sections share the same shape so reset and
 * value-change feel consistent.
 *
 * Per-section reset is a text button rather than a glyph (the prior
 * iteration used an `Icons.Outlined.RestartAlt` `↺`) — the action's
 * meaning is "set to default" which is more legible as a label than as
 * an icon, especially in a developer-facing sheet where there's only
 * one reset target per section.
 *
 * Position rows are listed with the **default value first**
 * (TopAligned), then the alternative (CenteredVertically). This is a
 * display-order decision only; the underlying enum order in
 * `NavigationRailItemPosition` is unchanged so persisted values are
 * stable.
 */
@Composable
fun NavRailSheetContent(
    itemPosition: NavigationRailItemPosition,
    topPaddingDp: Int,
    onItemPositionChange: (NavigationRailItemPosition) -> Unit,
    onItemPositionReset: () -> Unit,
    onTopPaddingDpChange: (Int) -> Unit,
    onTopPaddingDpReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_developer_nav_rail_sheet_title),
            style = MaterialTheme.typography.titleLarge,
        )

        SectionHeader(
            title = stringResource(R.string.settings_developer_nav_rail_section_position),
            onReset = onItemPositionReset,
        )
        // Default first (TopAligned), then alternative. Enum order is
        // unchanged — this is a display-only reorder.
        POSITION_DISPLAY_ORDER.forEach { position ->
            PositionChoiceRow(
                position = position,
                selected = position == itemPosition,
                onClick = { onItemPositionChange(position) },
            )
        }

        HorizontalDivider()

        SectionHeader(
            title = stringResource(R.string.settings_developer_nav_rail_section_padding),
            onReset = onTopPaddingDpReset,
        )
        TopPaddingStepper(
            valueDp = topPaddingDp,
            onChange = onTopPaddingDpChange,
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    onReset: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onReset) {
            Text(stringResource(R.string.settings_developer_nav_rail_reset_button))
        }
    }
}

@Composable
private fun PositionChoiceRow(
    position: NavigationRailItemPosition,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val label = when (position) {
        NavigationRailItemPosition.CenteredVertically ->
            stringResource(R.string.settings_developer_nav_rail_items_centered)
        NavigationRailItemPosition.TopAligned ->
            stringResource(R.string.settings_developer_nav_rail_items_top_aligned)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.padding(horizontal = 4.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun TopPaddingStepper(
    valueDp: Int,
    onChange: (Int) -> Unit,
) {
    val coerced = valueDp.coerceIn(NAV_RAIL_PADDING_MIN_DP, NAV_RAIL_PADDING_MAX_DP)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconButton(
            onClick = { onChange((coerced - 1).coerceAtLeast(NAV_RAIL_PADDING_MIN_DP)) },
            enabled = coerced > NAV_RAIL_PADDING_MIN_DP,
        ) {
            Icon(
                imageVector = Icons.Outlined.Remove,
                contentDescription = stringResource(
                    R.string.settings_developer_nav_rail_top_padding_decrease,
                ),
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = stringResource(
                    R.string.settings_developer_nav_rail_top_padding_value,
                    coerced,
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        IconButton(
            onClick = { onChange((coerced + 1).coerceAtMost(NAV_RAIL_PADDING_MAX_DP)) },
            enabled = coerced < NAV_RAIL_PADDING_MAX_DP,
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = stringResource(
                    R.string.settings_developer_nav_rail_top_padding_increase,
                ),
            )
        }
    }
}

internal const val NAV_RAIL_PADDING_MIN_DP = 0
internal const val NAV_RAIL_PADDING_MAX_DP = 64

// Default value first so the user sees it at the top of the list.
// Order here is independent of the enum's `entries` order — the enum
// stays as authored (CenteredVertically, TopAligned) so persisted
// ordinal values are unchanged.
private val POSITION_DISPLAY_ORDER = listOf(
    NavigationRailItemPosition.TopAligned,
    NavigationRailItemPosition.CenteredVertically,
)

// `private` so `checkPreviewCoverage` exempts it. The bottom sheet's
// behavior is observable on a real device behind the developer
// allowlist; this is an Android Studio reference only.
@Preview
@Composable
private fun NavRailSheetContentDefaultPreview() {
    Surface {
        NavRailSheetContent(
            itemPosition = NavigationRailItemPosition.TopAligned,
            topPaddingDp = 8,
            onItemPositionChange = {},
            onItemPositionReset = {},
            onTopPaddingDpChange = {},
            onTopPaddingDpReset = {},
        )
    }
}

@Preview
@Composable
private fun NavRailSheetContentCenteredPreview() {
    Surface {
        NavRailSheetContent(
            itemPosition = NavigationRailItemPosition.CenteredVertically,
            topPaddingDp = 16,
            onItemPositionChange = {},
            onItemPositionReset = {},
            onTopPaddingDpChange = {},
            onTopPaddingDpReset = {},
        )
    }
}
