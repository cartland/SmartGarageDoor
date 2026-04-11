package com.chriscartland.garage.screenshottests

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.chriscartland.garage.ui.DoorHistoryContentPreview
import com.chriscartland.garage.ui.HistoryTabPreview
import com.chriscartland.garage.ui.HomeContentPreview
import com.chriscartland.garage.ui.HomeTabPreview
import com.chriscartland.garage.ui.ProfileContentPreview
import com.chriscartland.garage.ui.SettingsTabPreview
import com.chriscartland.garage.ui.theme.AppTheme

// region Content-only previews (no tab bar)

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun HomeScreenPreviewTest() {
    AppTheme {
        HomeContentPreview()
    }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun DoorHistoryScreenPreviewTest() {
    AppTheme {
        DoorHistoryContentPreview()
    }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun ProfileScreenPreviewTest() {
    AppTheme {
        ProfileContentPreview()
    }
}

// endregion

// region Full-screen tab previews (content + tab bar)

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun HomeTabPreviewTest() {
    HomeTabPreview()
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun HistoryTabPreviewTest() {
    HistoryTabPreview()
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun SettingsTabPreviewTest() {
    SettingsTabPreview()
}

// endregion
