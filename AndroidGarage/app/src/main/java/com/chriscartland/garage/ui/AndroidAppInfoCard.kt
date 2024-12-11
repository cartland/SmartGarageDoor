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

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chriscartland.garage.settings.AppSettingsViewModel
import com.chriscartland.garage.settings.AppSettingsViewModelImpl
import com.chriscartland.garage.version.AppVersion

const val PRIVACY_POLICY_URL: String = "https://chriscart.land/garage-privacy-policy"

@Composable
fun AndroidAppInfoCard(
    modifier: Modifier = Modifier,
    settingsViewModel: AppSettingsViewModel = hiltViewModel<AppSettingsViewModelImpl>(),
) {
    val startAndroidAppInfoCardExpanded by settingsViewModel.profileAppCardExpanded.collectAsState()
    AndroidAppInfoCard(
        appVersion = LocalContext.current.AppVersion(),
        modifier = modifier,
        startExpanded = startAndroidAppInfoCardExpanded,
        onExpandedChange = {
            settingsViewModel.setProfileAppCardExpanded(it)
        }
    )
}

@Composable
fun AndroidAppInfoCard(
    appVersion: AppVersion,
    modifier: Modifier = Modifier,
    startExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit = {},
) {
    ExpandableColumnCard(
        title = "App",
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        startExpanded = startExpanded,
        onExpandedChange = onExpandedChange,
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
        Text(
            "Privacy Policy: $PRIVACY_POLICY_URL",
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        val context = LocalContext.current
        Row {
            Button(
                onClick = {
                    val url = "https://play.google.com/store/apps/details?id=${appVersion.packageName}"
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse(url)
                    }
                    context.startActivity(intent)
                },
            ) {
                Text("Play Store")
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val url = PRIVACY_POLICY_URL
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse(url)
                    }
                    context.startActivity(intent)
                },
            ) {
                Text("Privacy Policy")
            }
        }
    }
}

@Preview
@Composable
fun AndroidAppInfoCardPreview() {
    AndroidAppInfoCard(
        appVersion = AppVersion(
            packageName = "com.example",
            versionCode = 1L,
            versionName = "1.0.0 20241028.095244"
        ),
        startExpanded = true,
    )
}
