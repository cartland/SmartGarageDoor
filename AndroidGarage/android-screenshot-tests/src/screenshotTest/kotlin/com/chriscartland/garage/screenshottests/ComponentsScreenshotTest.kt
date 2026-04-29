package com.chriscartland.garage.screenshottests

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.chriscartland.garage.ui.DoorStatusCardPreview
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
