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

package com.chriscartland.garage.screenshottests

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.chriscartland.garage.ui.settings.AccountSheetContentSignedInPreview
import com.chriscartland.garage.ui.settings.DiagnosticsContentPreview
import com.chriscartland.garage.ui.settings.SettingsContentSignedInAllowlistedPreview
import com.chriscartland.garage.ui.settings.SettingsContentSignedInBasicPreview
import com.chriscartland.garage.ui.settings.SettingsContentSignedOutPreview
import com.chriscartland.garage.ui.settings.SnoozeSheetContentActivePreview
import com.chriscartland.garage.ui.settings.SnoozeSheetContentOffPreview
import com.chriscartland.garage.ui.settings.VersionDialogPreview
import com.chriscartland.garage.ui.theme.AppTheme

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun SettingsContentSignedOutPreviewTest() {
    AppTheme { SettingsContentSignedOutPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun SettingsContentSignedInBasicPreviewTest() {
    AppTheme { SettingsContentSignedInBasicPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun SettingsContentSignedInAllowlistedPreviewTest() {
    AppTheme { SettingsContentSignedInAllowlistedPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun SnoozeSheetContentOffPreviewTest() {
    AppTheme { SnoozeSheetContentOffPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun SnoozeSheetContentActivePreviewTest() {
    AppTheme { SnoozeSheetContentActivePreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun AccountSheetContentSignedInPreviewTest() {
    AppTheme { AccountSheetContentSignedInPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun VersionDialogPreviewTest() {
    AppTheme { VersionDialogPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun DiagnosticsContentPreviewTest() {
    AppTheme { DiagnosticsContentPreview() }
}
