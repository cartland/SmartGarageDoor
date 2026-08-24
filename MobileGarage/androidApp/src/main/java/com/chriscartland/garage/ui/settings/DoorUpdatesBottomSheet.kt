/*
 * Copyright 2026 Chris Cartland. All rights reserved.
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chriscartland.garage.R
import com.chriscartland.garage.domain.model.DoorUpdateStrategyOverride

/**
 * Developer-gated picker for how the app keeps door state fresh.
 *
 * Swapping is live — `DoorUpdateManager` cancels the running strategy the
 * moment the setting changes — so this sheet is the surface for comparing
 * push against polling on one device without rebuilding. On Android the
 * default (push) is the one that has always shipped; the alternatives are
 * here so iOS's behavior can be exercised on hardware that is easier to
 * instrument.
 *
 * Wording is Android's, deliberately: shared code decides WHICH strategy
 * runs, each platform says what that means in its own words (ADR-035), so
 * "push" here names FCM without the shared enum having to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoorUpdatesBottomSheet(
    selected: DoorUpdateStrategyOverride,
    onSelect: (DoorUpdateStrategyOverride) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        DoorUpdatesSheetContent(
            selected = selected,
            onSelect = onSelect,
        )
    }
}

/**
 * Sheet content as its own Composable so previews and screenshot tests
 * can render it without the sheet's show animation (same split as
 * `NavRailSheetContent`).
 */
@Composable
fun DoorUpdatesSheetContent(
    selected: DoorUpdateStrategyOverride,
    onSelect: (DoorUpdateStrategyOverride) -> Unit,
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
            text = stringResource(R.string.settings_developer_door_updates_sheet_title),
            style = MaterialTheme.typography.titleLarge,
        )
        DoorUpdateStrategyOverride.entries.forEach { option ->
            StrategyChoiceRow(
                option = option,
                selected = option == selected,
                onClick = { onSelect(option) },
            )
        }
    }
}

@Composable
private fun StrategyChoiceRow(
    option: DoorUpdateStrategyOverride,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.padding(horizontal = 4.dp))
        Column {
            Text(
                text = stringResource(DoorUpdateStrategyLabels.title(option)),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(DoorUpdateStrategyLabels.subtitle(option)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Android's word for each strategy (ADR-035: shared decides, platform
 * words it). An `object` rather than bare top-level functions, per the
 * `checkNoBareTopLevelFunctions` rule — Composables are exempt from it,
 * these string lookups are not.
 */
internal object DoorUpdateStrategyLabels {
    fun title(option: DoorUpdateStrategyOverride): Int =
        when (option) {
            DoorUpdateStrategyOverride.PLATFORM_DEFAULT ->
                R.string.settings_developer_door_updates_default_title
            DoorUpdateStrategyOverride.PUSH ->
                R.string.settings_developer_door_updates_push_title
            DoorUpdateStrategyOverride.POLL ->
                R.string.settings_developer_door_updates_poll_title
            DoorUpdateStrategyOverride.PUSH_WITH_FOREGROUND_REFRESH ->
                R.string.settings_developer_door_updates_push_refresh_title
        }

    fun subtitle(option: DoorUpdateStrategyOverride): Int =
        when (option) {
            DoorUpdateStrategyOverride.PLATFORM_DEFAULT ->
                R.string.settings_developer_door_updates_default_subtitle
            DoorUpdateStrategyOverride.PUSH ->
                R.string.settings_developer_door_updates_push_subtitle
            DoorUpdateStrategyOverride.POLL ->
                R.string.settings_developer_door_updates_poll_subtitle
            DoorUpdateStrategyOverride.PUSH_WITH_FOREGROUND_REFRESH ->
                R.string.settings_developer_door_updates_push_refresh_subtitle
        }
}

// `private` so `checkPreviewCoverage` exempts it, matching
// `NavRailBottomSheet`'s previews. The sheet's behavior is observable on a
// device behind the developer allowlist; these are an Android Studio
// reference only.
@Preview
@Composable
private fun DoorUpdatesSheetContentDefaultPreview() {
    Surface {
        DoorUpdatesSheetContent(
            selected = DoorUpdateStrategyOverride.PLATFORM_DEFAULT,
            onSelect = {},
        )
    }
}

@Preview
@Composable
private fun DoorUpdatesSheetContentPollPreview() {
    Surface {
        DoorUpdatesSheetContent(
            selected = DoorUpdateStrategyOverride.POLL,
            onSelect = {},
        )
    }
}
