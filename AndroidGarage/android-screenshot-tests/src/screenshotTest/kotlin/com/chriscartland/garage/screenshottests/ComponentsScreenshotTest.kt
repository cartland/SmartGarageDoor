package com.chriscartland.garage.screenshottests

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.chriscartland.garage.ui.DoorStatusCardPreview
import com.chriscartland.garage.ui.ErrorCardPreview
import com.chriscartland.garage.ui.LogSummaryCardPreview
import com.chriscartland.garage.ui.RecentDoorEventListItemPreview
import com.chriscartland.garage.ui.RemoteButtonContentAwaitingConfirmationPreview
import com.chriscartland.garage.ui.RemoteButtonContentCancelledPreview
import com.chriscartland.garage.ui.RemoteButtonContentDoorFailedPreview
import com.chriscartland.garage.ui.RemoteButtonContentPreparingPreview
import com.chriscartland.garage.ui.RemoteButtonContentPreview
import com.chriscartland.garage.ui.RemoteButtonContentSendingToDoorPreview
import com.chriscartland.garage.ui.RemoteButtonContentSendingToServerPreview
import com.chriscartland.garage.ui.RemoteButtonContentServerFailedPreview
import com.chriscartland.garage.ui.RemoteButtonContentSucceededPreview
import com.chriscartland.garage.ui.SnoozeNotificationCardClearedPreview
import com.chriscartland.garage.ui.SnoozeNotificationCardErrorPreview
import com.chriscartland.garage.ui.SnoozeNotificationCardLoadingPreview
import com.chriscartland.garage.ui.SnoozeNotificationCardPreview
import com.chriscartland.garage.ui.SnoozeNotificationCardSendingPreview
import com.chriscartland.garage.ui.SnoozeNotificationCardSucceededPreview
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
fun RemoteButtonContentPreparingPreviewTest() {
    AppTheme { RemoteButtonContentPreparingPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun RemoteButtonContentAwaitingConfirmationPreviewTest() {
    AppTheme { RemoteButtonContentAwaitingConfirmationPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun RemoteButtonContentCancelledPreviewTest() {
    AppTheme { RemoteButtonContentCancelledPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun RemoteButtonContentSendingToServerPreviewTest() {
    AppTheme { RemoteButtonContentSendingToServerPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun RemoteButtonContentSendingToDoorPreviewTest() {
    AppTheme { RemoteButtonContentSendingToDoorPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun RemoteButtonContentSucceededPreviewTest() {
    AppTheme { RemoteButtonContentSucceededPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun RemoteButtonContentServerFailedPreviewTest() {
    AppTheme { RemoteButtonContentServerFailedPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun RemoteButtonContentDoorFailedPreviewTest() {
    AppTheme { RemoteButtonContentDoorFailedPreview() }
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

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun SnoozeNotificationCardLoadingPreviewTest() {
    AppTheme {
        SnoozeNotificationCardLoadingPreview()
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
fun SnoozeNotificationCardSendingPreviewTest() {
    AppTheme {
        SnoozeNotificationCardSendingPreview()
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
fun SnoozeNotificationCardSucceededPreviewTest() {
    AppTheme {
        SnoozeNotificationCardSucceededPreview()
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
fun SnoozeNotificationCardClearedPreviewTest() {
    AppTheme {
        SnoozeNotificationCardClearedPreview()
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
fun SnoozeNotificationCardErrorPreviewTest() {
    AppTheme {
        SnoozeNotificationCardErrorPreview()
    }
}
