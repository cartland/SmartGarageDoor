package com.chriscartland.garage.screenshottests

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.chriscartland.garage.ui.DoorStatusCardPreview
import com.chriscartland.garage.ui.ErrorCardPreview
import com.chriscartland.garage.ui.LogSummaryCardPreview
import com.chriscartland.garage.ui.RecentDoorEventListItemPreview
import com.chriscartland.garage.ui.RemoteButtonContentArmedPreview
import com.chriscartland.garage.ui.RemoteButtonContentArmingPreview
import com.chriscartland.garage.ui.RemoteButtonContentNotConfirmedPreview
import com.chriscartland.garage.ui.RemoteButtonContentPreview
import com.chriscartland.garage.ui.RemoteButtonContentReceivedPreview
import com.chriscartland.garage.ui.RemoteButtonContentSendingPreview
import com.chriscartland.garage.ui.RemoteButtonContentSendingTimeoutPreview
import com.chriscartland.garage.ui.RemoteButtonContentSentPreview
import com.chriscartland.garage.ui.RemoteButtonContentSentTimeoutPreview
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
fun RemoteButtonContentArmingPreviewTest() {
    AppTheme { RemoteButtonContentArmingPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun RemoteButtonContentArmedPreviewTest() {
    AppTheme { RemoteButtonContentArmedPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun RemoteButtonContentNotConfirmedPreviewTest() {
    AppTheme { RemoteButtonContentNotConfirmedPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun RemoteButtonContentSendingPreviewTest() {
    AppTheme { RemoteButtonContentSendingPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun RemoteButtonContentSentPreviewTest() {
    AppTheme { RemoteButtonContentSentPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun RemoteButtonContentReceivedPreviewTest() {
    AppTheme { RemoteButtonContentReceivedPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun RemoteButtonContentSendingTimeoutPreviewTest() {
    AppTheme { RemoteButtonContentSendingTimeoutPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun RemoteButtonContentSentTimeoutPreviewTest() {
    AppTheme { RemoteButtonContentSentTimeoutPreview() }
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
