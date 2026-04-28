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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * AlertDialog showing extended version metadata. Material 3 [AlertDialog]
 * is a pure Compose surface (no platform Window) so it renders under
 * `@Preview` and Compose Preview Screenshot Testing without workarounds.
 */
@Composable
fun VersionDialog(
    versionName: String,
    versionCode: String,
    buildTimestamp: String,
    packageName: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
            )
        },
        title = { Text("App version") },
        text = {
            Column {
                LabeledLine("Version", versionName)
                Spacer(Modifier.height(8.dp))
                LabeledLine("Build", versionCode)
                Spacer(Modifier.height(8.dp))
                LabeledLine("Package", packageName)
                Spacer(Modifier.height(8.dp))
                LabeledLine("Built", buildTimestamp)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun LabeledLine(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Preview
@Composable
fun VersionDialogPreview() {
    VersionDialog(
        versionName = "2.6.1",
        versionCode = "182",
        buildTimestamp = "2026-04-28 04:55:47 UTC",
        packageName = "com.chriscartland.garage",
        onDismiss = {},
    )
}
