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
import com.chriscartland.garage.ui.home.HomeContentAwaitingConfirmationPreview
import com.chriscartland.garage.ui.home.HomeContentClosedSignedInPreview
import com.chriscartland.garage.ui.home.HomeContentOnTabletPreview
import com.chriscartland.garage.ui.home.HomeContentOpenSignedInPreview
import com.chriscartland.garage.ui.home.HomeContentOpeningTooLongPreview
import com.chriscartland.garage.ui.home.HomeContentPermissionMissingPreview
import com.chriscartland.garage.ui.home.HomeContentRemotePillLoadingPreview
import com.chriscartland.garage.ui.home.HomeContentRemotePillOfflinePreview
import com.chriscartland.garage.ui.home.HomeContentRemotePillOnlinePreview
import com.chriscartland.garage.ui.home.HomeContentRemotePillUnauthorizedPreview
import com.chriscartland.garage.ui.home.HomeContentRemotePillUnknownPreview
import com.chriscartland.garage.ui.home.HomeContentSendingToDoorPreview
import com.chriscartland.garage.ui.home.HomeContentSignedOutPreview
import com.chriscartland.garage.ui.home.HomeContentStaleBannerPreview
import com.chriscartland.garage.ui.theme.AppTheme

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun HomeContentOpenSignedInPreviewTest() {
    AppTheme { HomeContentOpenSignedInPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun HomeContentClosedSignedInPreviewTest() {
    AppTheme { HomeContentClosedSignedInPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun HomeContentAwaitingConfirmationPreviewTest() {
    AppTheme { HomeContentAwaitingConfirmationPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun HomeContentSendingToDoorPreviewTest() {
    AppTheme { HomeContentSendingToDoorPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun HomeContentOpeningTooLongPreviewTest() {
    AppTheme { HomeContentOpeningTooLongPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun HomeContentStaleBannerPreviewTest() {
    AppTheme { HomeContentStaleBannerPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun HomeContentPermissionMissingPreviewTest() {
    AppTheme { HomeContentPermissionMissingPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun HomeContentSignedOutPreviewTest() {
    AppTheme { HomeContentSignedOutPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun HomeContentRemotePillUnauthorizedPreviewTest() {
    AppTheme { HomeContentRemotePillUnauthorizedPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun HomeContentRemotePillLoadingPreviewTest() {
    AppTheme { HomeContentRemotePillLoadingPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun HomeContentRemotePillUnknownPreviewTest() {
    AppTheme { HomeContentRemotePillUnknownPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun HomeContentRemotePillOnlinePreviewTest() {
    AppTheme { HomeContentRemotePillOnlinePreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun HomeContentRemotePillOfflinePreviewTest() {
    AppTheme { HomeContentRemotePillOfflinePreview() }
}

@PreviewTest
@Preview(name = "Tablet Light", widthDp = 900, heightDp = 1100)
@Preview(
    name = "Tablet Dark",
    widthDp = 900,
    heightDp = 1100,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun HomeContentOnTabletPreviewTest() {
    AppTheme { HomeContentOnTabletPreview() }
}
