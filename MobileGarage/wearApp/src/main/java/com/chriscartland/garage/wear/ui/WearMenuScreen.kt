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

package com.chriscartland.garage.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import com.chriscartland.garage.wear.R

/**
 * The watch's menu: which build is running, and a way to the Play Store.
 *
 * Exists because the watch is updated far more often than it is configured, and
 * until now it could answer neither "what am I running?" nor "is there anything
 * newer?" without going through the phone. Wear OS does update apps on its own,
 * but on its own schedule, which is no use when the build you want to try was
 * cut minutes ago.
 *
 * Deliberately shows the running build ALONGSIDE the store link rather than just
 * linking out. The store can say what the newest build is; only the watch can
 * say what it actually installed, and after tapping Update that second question
 * is the one you come back to ask. Without it the menu could only ever tell you
 * half of what you wanted to know.
 *
 * Stateless apart from the one thing the caller cannot know in advance: whether
 * the launch found a Play Store. [onOpenStore] reports that back so the screen
 * can say so instead of leaving a button that looks broken — see [WearStoreLink]
 * for why this is discovered by trying rather than by asking first.
 */
@Composable
fun WearMenuScreen(
    versionName: String,
    tagNumber: Int,
    onOpenStore: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    var storeUnavailable by remember { mutableStateOf(false) }

    ScreenScaffold(scrollState = scrollState, modifier = modifier) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(contentPadding)
                .padding(horizontal = HORIZONTAL_PADDING_DP.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(
                ITEM_SPACING_DP.dp,
                Alignment.CenterVertically,
            ),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = versionName,
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
            )
            Text(
                // A released build names itself the way the tags do (wear/15);
                // a local one says so rather than pretending to be wear/0.
                text = WearStoreLink.releaseTag(tagNumber)
                    ?: stringResource(R.string.menu_local_build),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = { storeUnavailable = !onOpenStore() }) {
                Text(text = stringResource(R.string.menu_open_store))
            }
            // Reserved caption slot: an empty line keeps the column from
            // reflowing when the message appears (same pattern as the hero
            // screen's sign-in failure caption).
            Text(
                text = if (storeUnavailable) stringResource(R.string.menu_store_unavailable) else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                minLines = 1,
            )
        }
    }
}

private const val HORIZONTAL_PADDING_DP = 16
private const val ITEM_SPACING_DP = 4

/** A released build: names itself with the tag it was cut from. */
@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
private fun WearMenuScreenReleasePreview() {
    MaterialTheme {
        WearMenuScreen(versionName = "0.3.6", tagNumber = 15, onOpenStore = { true })
    }
}

/** A build that never came from a release tag. */
@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
private fun WearMenuScreenLocalBuildPreview() {
    MaterialTheme {
        WearMenuScreen(versionName = "0.3.6", tagNumber = 0, onOpenStore = { true })
    }
}
