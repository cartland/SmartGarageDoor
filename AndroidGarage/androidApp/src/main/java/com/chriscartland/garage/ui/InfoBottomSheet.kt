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

package com.chriscartland.garage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Bottom sheets that explain what the Status and Remote control
 * indicators on the Home tab mean. Opened on tap of the corresponding
 * indicator. The text is intentionally short — strings reviewed with the
 * user before landing.
 *
 * Two production wrappers ([DoorStatusInfoBottomSheet],
 * [RemoteControlInfoBottomSheet]) plus a private layout helper that owns
 * the visual structure (info icon + title + paragraphs in a scrollable
 * Column). Pattern matches [com.chriscartland.garage.ui.settings.VersionBottomSheet]
 * — outer `*BottomSheet` is the production wrapper hosting the
 * `ModalBottomSheet` shell; `*SheetContent` is the previewable surface
 * for screenshot tests.
 *
 * `verticalScroll(rememberScrollState())` per the CLAUDE.md rule —
 * `ModalBottomSheet` doesn't provide its own scroll, so tall content +
 * a short viewport (landscape, large font scale) would silently clip
 * without it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoorStatusInfoBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        DoorStatusInfoSheetContent()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteControlInfoBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        RemoteControlInfoSheetContent()
    }
}

@Composable
fun DoorStatusInfoSheetContent(modifier: Modifier = Modifier) {
    InfoSheetLayout(
        title = "Door status",
        paragraphs = listOf(
            "The door sensor checks in every 10 minutes, or whenever the door moves.",
            "If we don't hear from it on schedule, this shows \"no signal\" so you know the sensor may be offline.",
        ),
        modifier = modifier,
    )
}

@Composable
fun RemoteControlInfoSheetContent(modifier: Modifier = Modifier) {
    InfoSheetLayout(
        title = "Remote control",
        paragraphs = listOf(
            "The remote button checks in frequently. \"Available\" means it just told us it's ready to open or close the door.",
            "If contact stops, this shows when we last heard from it. Tapping the button may not work until it reconnects.",
        ),
        modifier = modifier,
    )
}

@Composable
private fun InfoSheetLayout(
    title: String,
    paragraphs: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        paragraphs.forEach { paragraph ->
            Text(
                text = paragraph,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview
@Composable
fun DoorStatusInfoSheetContentPreview() {
    Surface { DoorStatusInfoSheetContent() }
}

@Preview
@Composable
fun RemoteControlInfoSheetContentPreview() {
    Surface { RemoteControlInfoSheetContent() }
}
