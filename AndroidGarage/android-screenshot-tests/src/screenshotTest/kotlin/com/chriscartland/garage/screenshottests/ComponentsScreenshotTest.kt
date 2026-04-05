package com.chriscartland.garage.screenshottests

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.chriscartland.garage.ui.DoorStatusCardPreview
import com.chriscartland.garage.ui.ErrorCardPreview
import com.chriscartland.garage.ui.LogSummaryCardPreview
import com.chriscartland.garage.ui.RecentDoorEventListItemPreview
import com.chriscartland.garage.ui.RemoteButtonContentPreview
import com.chriscartland.garage.ui.SnoozeNotificationCardPreview
import com.chriscartland.garage.ui.UserInfoCardPreview
import com.chriscartland.garage.ui.UserInfoNoUserPreview
import com.chriscartland.garage.ui.theme.AppTheme

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun DoorStatusCardPreviewTest() {
    AppTheme {
        DoorStatusCardPreview()
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
fun RecentDoorEventListItemPreviewTest() {
    AppTheme {
        RecentDoorEventListItemPreview()
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
fun RemoteButtonContentPreviewTest() {
    AppTheme {
        RemoteButtonContentPreview()
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
fun ErrorCardPreviewTest() {
    AppTheme {
        ErrorCardPreview()
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
fun UserInfoCardPreviewTest() {
    AppTheme {
        UserInfoCardPreview()
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
fun UserInfoNoUserPreviewTest() {
    AppTheme {
        UserInfoNoUserPreview()
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
fun LogSummaryCardPreviewTest() {
    AppTheme {
        LogSummaryCardPreview()
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
fun SnoozeNotificationCardPreviewTest() {
    AppTheme {
        SnoozeNotificationCardPreview()
    }
}
