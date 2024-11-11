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

package com.chriscartland.garage.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.chriscartland.garage.version.AppVersion

@Composable
fun AndroidAppInfoCard() {
    AndroidAppInfoCard(LocalContext.current.AppVersion())
}

@Composable
fun AndroidAppInfoCard(
    appVersion: AppVersion,
    modifier: Modifier = Modifier,
) {
    ExpandableColumnCard(
        title = "Application",
        modifier = modifier,
        startExpanded = true,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Package name: ${appVersion.packageName}",
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            "Version name: ${appVersion.versionName}",
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            "Version code: ${appVersion.versionCode}",
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview
@Composable
fun AndroidAppInfoCardPreview() {
    AndroidAppInfoCard(
        AppVersion(
            packageName = "com.example",
            versionCode = 1L,
            versionName = "1.0.0 20241028.095244"
        )
    )
}
