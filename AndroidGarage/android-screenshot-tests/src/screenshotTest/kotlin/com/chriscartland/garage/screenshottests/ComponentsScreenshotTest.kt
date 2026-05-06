package com.chriscartland.garage.screenshottests

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.chriscartland.garage.ui.DoorStatusCardPreview
import com.chriscartland.garage.ui.ErrorCardLongButtonWordPreview
import com.chriscartland.garage.ui.ErrorCardManyButtonWordsPreview
import com.chriscartland.garage.ui.ErrorCardPreview
import com.chriscartland.garage.ui.GarageDoorButtonAwaitingConfirmationPreview
import com.chriscartland.garage.ui.GarageDoorButtonCancelledPreview
import com.chriscartland.garage.ui.GarageDoorButtonDoorFailedPreview
import com.chriscartland.garage.ui.GarageDoorButtonPreparingPreview
import com.chriscartland.garage.ui.GarageDoorButtonReadyPreview
import com.chriscartland.garage.ui.GarageDoorButtonSendingToDoorPreview
import com.chriscartland.garage.ui.GarageDoorButtonSendingToServerPreview
import com.chriscartland.garage.ui.GarageDoorButtonServerFailedPreview
import com.chriscartland.garage.ui.GarageDoorButtonSucceededPreview
import com.chriscartland.garage.ui.NetworkDiagramDoorFailedPreview
import com.chriscartland.garage.ui.NetworkDiagramIdlePreview
import com.chriscartland.garage.ui.NetworkDiagramSendingToDoorPreview
import com.chriscartland.garage.ui.NetworkDiagramSendingToServerPreview
import com.chriscartland.garage.ui.NetworkDiagramServerFailedPreview
import com.chriscartland.garage.ui.NetworkDiagramSucceededPreview
import com.chriscartland.garage.ui.RemoteButtonContentAwaitingConfirmationPreview
import com.chriscartland.garage.ui.RemoteButtonContentCancelledPreview
import com.chriscartland.garage.ui.RemoteButtonContentDoorFailedPreview
import com.chriscartland.garage.ui.RemoteButtonContentPreparingPreview
import com.chriscartland.garage.ui.RemoteButtonContentPreview
import com.chriscartland.garage.ui.RemoteButtonContentSendingToDoorPreview
import com.chriscartland.garage.ui.RemoteButtonContentSendingToServerPreview
import com.chriscartland.garage.ui.RemoteButtonContentServerFailedPreview
import com.chriscartland.garage.ui.RemoteButtonContentSucceededPreview
import com.chriscartland.garage.ui.RemoteButtonHealthPillLoadingPreview
import com.chriscartland.garage.ui.RemoteButtonHealthPillOfflinePreview
import com.chriscartland.garage.ui.RemoteButtonHealthPillOnlinePreview
import com.chriscartland.garage.ui.RemoteButtonHealthPillUnauthorizedPreview
import com.chriscartland.garage.ui.RemoteButtonHealthPillUnknownPreview
import com.chriscartland.garage.ui.RemoteOfflinePillAgingPreview
import com.chriscartland.garage.ui.RemoteOfflinePillFreshPreview
import com.chriscartland.garage.ui.RemoteOfflinePillStalePreview
import com.chriscartland.garage.ui.RemoteOfflinePillVeryStalePreview
import com.chriscartland.garage.ui.TitleBarCheckInPillAgingPreview
import com.chriscartland.garage.ui.TitleBarCheckInPillFreshPreview
import com.chriscartland.garage.ui.TitleBarCheckInPillNoDataPreview
import com.chriscartland.garage.ui.TitleBarCheckInPillStalePreview
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
fun NetworkDiagramIdlePreviewTest() {
    AppTheme { NetworkDiagramIdlePreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun NetworkDiagramSendingToServerPreviewTest() {
    AppTheme { NetworkDiagramSendingToServerPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun NetworkDiagramSendingToDoorPreviewTest() {
    AppTheme { NetworkDiagramSendingToDoorPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun NetworkDiagramSucceededPreviewTest() {
    AppTheme { NetworkDiagramSucceededPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun NetworkDiagramServerFailedPreviewTest() {
    AppTheme { NetworkDiagramServerFailedPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun NetworkDiagramDoorFailedPreviewTest() {
    AppTheme { NetworkDiagramDoorFailedPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun GarageDoorButtonReadyPreviewTest() {
    AppTheme { GarageDoorButtonReadyPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun GarageDoorButtonPreparingPreviewTest() {
    AppTheme { GarageDoorButtonPreparingPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun GarageDoorButtonAwaitingConfirmationPreviewTest() {
    AppTheme { GarageDoorButtonAwaitingConfirmationPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun GarageDoorButtonCancelledPreviewTest() {
    AppTheme { GarageDoorButtonCancelledPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun GarageDoorButtonSendingToServerPreviewTest() {
    AppTheme { GarageDoorButtonSendingToServerPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun GarageDoorButtonSendingToDoorPreviewTest() {
    AppTheme { GarageDoorButtonSendingToDoorPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun GarageDoorButtonSucceededPreviewTest() {
    AppTheme { GarageDoorButtonSucceededPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun GarageDoorButtonServerFailedPreviewTest() {
    AppTheme { GarageDoorButtonServerFailedPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun GarageDoorButtonDoorFailedPreviewTest() {
    AppTheme { GarageDoorButtonDoorFailedPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun ErrorCardLongButtonWordPreviewTest() {
    AppTheme { ErrorCardLongButtonWordPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun ErrorCardManyButtonWordsPreviewTest() {
    AppTheme { ErrorCardManyButtonWordsPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun TitleBarCheckInPillFreshPreviewTest() {
    AppTheme { TitleBarCheckInPillFreshPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun TitleBarCheckInPillAgingPreviewTest() {
    AppTheme { TitleBarCheckInPillAgingPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun TitleBarCheckInPillStalePreviewTest() {
    AppTheme { TitleBarCheckInPillStalePreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun TitleBarCheckInPillNoDataPreviewTest() {
    AppTheme { TitleBarCheckInPillNoDataPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun RemoteOfflinePillFreshPreviewTest() {
    AppTheme { RemoteOfflinePillFreshPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun RemoteOfflinePillAgingPreviewTest() {
    AppTheme { RemoteOfflinePillAgingPreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun RemoteOfflinePillStalePreviewTest() {
    AppTheme { RemoteOfflinePillStalePreview() }
}

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun RemoteOfflinePillVeryStalePreviewTest() {
    AppTheme { RemoteOfflinePillVeryStalePreview() }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 320, heightDp = 64, name = "Light")
@Preview(
    showBackground = true,
    widthDp = 320,
    heightDp = 64,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun RemoteButtonHealthPillUnauthorizedPreviewTest() {
    AppTheme { RemoteButtonHealthPillUnauthorizedPreview() }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 320, heightDp = 64, name = "Light")
@Preview(
    showBackground = true,
    widthDp = 320,
    heightDp = 64,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun RemoteButtonHealthPillLoadingPreviewTest() {
    AppTheme { RemoteButtonHealthPillLoadingPreview() }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 320, heightDp = 64, name = "Light")
@Preview(
    showBackground = true,
    widthDp = 320,
    heightDp = 64,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun RemoteButtonHealthPillUnknownPreviewTest() {
    AppTheme { RemoteButtonHealthPillUnknownPreview() }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 320, heightDp = 64, name = "Light")
@Preview(
    showBackground = true,
    widthDp = 320,
    heightDp = 64,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun RemoteButtonHealthPillOnlinePreviewTest() {
    AppTheme { RemoteButtonHealthPillOnlinePreview() }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 320, heightDp = 64, name = "Light")
@Preview(
    showBackground = true,
    widthDp = 320,
    heightDp = 64,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun RemoteButtonHealthPillOfflinePreviewTest() {
    AppTheme { RemoteButtonHealthPillOfflinePreview() }
}
