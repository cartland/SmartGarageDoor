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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.autoSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chriscartland.garage.R
import com.chriscartland.garage.domain.model.SnoozeDurationUIOption

/**
 * Production wrapper. Shows the sheet content inside a [ModalBottomSheet].
 * Tracks the user's pending selection locally; only commits via [onSave].
 *
 * The `*Content` Composable below is the previewable surface (the
 * sheet's show animation runs in a `LaunchedEffect` that doesn't fire
 * under `@Preview` / screenshot tests, so the content is split out).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnoozeBottomSheet(
    initialSelection: SnoozeDurationUIOption,
    onSave: (SnoozeDurationUIOption) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Saveable so the user's pending duration choice survives rotation /
    // window resize while the sheet is open. `autoSaver` handles the enum.
    var selected by rememberSaveable(stateSaver = autoSaver()) { mutableStateOf(initialSelection) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        SnoozeSheetContent(
            selectedOption = selected,
            onOptionSelected = { selected = it },
            onCancel = onDismiss,
            onSave = {
                onSave(selected)
                onDismiss()
            },
        )
    }
}

/**
 * Sheet content extracted as a separate Composable so previews and
 * screenshot tests can render it directly.
 */
@Composable
fun SnoozeSheetContent(
    selectedOption: SnoozeDurationUIOption,
    onOptionSelected: (SnoozeDurationUIOption) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_snooze_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        SnoozeOptionRow(
            label = stringResource(R.string.settings_snooze_option_none),
            selected = selectedOption == SnoozeDurationUIOption.None,
            onClick = { onOptionSelected(SnoozeDurationUIOption.None) },
        )
        HorizontalDivider()
        SnoozeOptionRow(
            label = stringResource(R.string.settings_snooze_option_one_hour),
            selected = selectedOption == SnoozeDurationUIOption.OneHour,
            onClick = { onOptionSelected(SnoozeDurationUIOption.OneHour) },
        )
        HorizontalDivider()
        SnoozeOptionRow(
            label = stringResource(R.string.settings_snooze_option_four_hours),
            selected = selectedOption == SnoozeDurationUIOption.FourHours,
            onClick = { onOptionSelected(SnoozeDurationUIOption.FourHours) },
        )
        HorizontalDivider()
        SnoozeOptionRow(
            label = stringResource(R.string.settings_snooze_option_eight_hours),
            selected = selectedOption == SnoozeDurationUIOption.EightHours,
            onClick = { onOptionSelected(SnoozeDurationUIOption.EightHours) },
        )
        HorizontalDivider()
        SnoozeOptionRow(
            label = stringResource(R.string.settings_snooze_option_twelve_hours),
            selected = selectedOption == SnoozeDurationUIOption.TwelveHours,
            onClick = { onOptionSelected(SnoozeDurationUIOption.TwelveHours) },
        )

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.settings_snooze_cancel)) }
            Spacer(Modifier.padding(horizontal = 4.dp))
            Button(onClick = onSave) { Text(stringResource(R.string.settings_snooze_save)) }
        }
    }
}

@Composable
private fun SnoozeOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.padding(horizontal = 4.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

// Preview helpers — wrapped in a Surface for theming context. These are
// what the screenshot test will exercise.

@Preview
@Composable
fun SnoozeSheetContentOffPreview() {
    Surface {
        SnoozeSheetContent(
            selectedOption = SnoozeDurationUIOption.OneHour,
            onOptionSelected = {},
            onCancel = {},
            onSave = {},
        )
    }
}

@Preview
@Composable
fun SnoozeSheetContentActivePreview() {
    Surface {
        SnoozeSheetContent(
            selectedOption = SnoozeDurationUIOption.None,
            onOptionSelected = {},
            onCancel = {},
            onSave = {},
        )
    }
}
