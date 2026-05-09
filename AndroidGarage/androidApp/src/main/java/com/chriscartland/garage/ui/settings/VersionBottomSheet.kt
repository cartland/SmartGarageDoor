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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Production wrapper. Shows extended version metadata inside a
 * [ModalBottomSheet]. Replaces the old `VersionDialog` (Material 3
 * `AlertDialog`) — the bottom-sheet shape matches the
 * [AccountBottomSheet] structure and gives each metadata field its own
 * tappable container that copies the value to the clipboard.
 *
 * The `*Content` Composable below is the previewable surface — see the
 * matching note on [SnoozeBottomSheet].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionBottomSheet(
    versionName: String,
    versionCode: String,
    buildTimestamp: String,
    packageName: String,
    onCopy: (label: String, value: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        VersionSheetContent(
            versionName = versionName,
            versionCode = versionCode,
            buildTimestamp = buildTimestamp,
            packageName = packageName,
            onCopy = onCopy,
        )
    }
}

/**
 * Sheet content extracted as a separate Composable so previews and
 * screenshot tests can render it directly. The interaction (tap-to-copy
 * + post-copy feedback) is the consumer's concern; this Composable
 * only emits the [onCopy] callback when a row is tapped.
 */
@Composable
fun VersionSheetContent(
    versionName: String,
    versionCode: String,
    buildTimestamp: String,
    packageName: String,
    onCopy: (label: String, value: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "App version",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(16.dp))

        VersionFieldRow(label = "Version", value = versionName, onCopy = onCopy)
        VersionFieldRow(label = "Build", value = versionCode, onCopy = onCopy)
        VersionFieldRow(label = "Package", value = packageName, onCopy = onCopy)
        VersionFieldRow(label = "Built", value = buildTimestamp, onCopy = onCopy)
    }
}

@Composable
private fun VersionFieldRow(
    label: String,
    value: String,
    onCopy: (label: String, value: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = { onCopy(label, value) },
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCopy(label, value) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = "Copy $label",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview
@Composable
fun VersionSheetContentPreview() {
    Surface {
        VersionSheetContent(
            versionName = "2.15.2",
            versionCode = "212",
            buildTimestamp = "2026-05-09 16:00:00 UTC",
            packageName = "com.chriscartland.garage",
            onCopy = { _, _ -> },
        )
    }
}
