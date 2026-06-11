package com.chriscartland.garage.screenshottests

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.chriscartland.garage.ui.CLOSED_POSITION
import com.chriscartland.garage.ui.ClosedPreview
import com.chriscartland.garage.ui.ClosingPreview
import com.chriscartland.garage.ui.ClosingTooLongPreview
import com.chriscartland.garage.ui.ErrorSensorConflictPreview
import com.chriscartland.garage.ui.GarageDoorCanvas
import com.chriscartland.garage.ui.GarageIconPreview
import com.chriscartland.garage.ui.MidwayPreview
import com.chriscartland.garage.ui.OpenMisalignedPreview
import com.chriscartland.garage.ui.OpenPreview
import com.chriscartland.garage.ui.OpeningPreview
import com.chriscartland.garage.ui.OpeningTooLongPreview
import com.chriscartland.garage.ui.theme.AppTheme

@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun GarageDoorClosedPreviewTest() {
    AppTheme {
        ClosedPreview()
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
fun GarageDoorOpenPreviewTest() {
    AppTheme {
        OpenPreview()
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
fun GarageDoorOpeningPreviewTest() {
    AppTheme {
        OpeningPreview()
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
fun GarageDoorClosingPreviewTest() {
    AppTheme {
        ClosingPreview()
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
fun GarageDoorMidwayPreviewTest() {
    AppTheme {
        MidwayPreview()
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
fun GarageIconPreviewTest() {
    AppTheme {
        GarageIconPreview()
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
fun GarageDoorOpeningTooLongPreviewTest() {
    AppTheme {
        OpeningTooLongPreview()
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
fun GarageDoorClosingTooLongPreviewTest() {
    AppTheme {
        ClosingTooLongPreview()
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
fun GarageDoorOpenMisalignedPreviewTest() {
    AppTheme {
        OpenMisalignedPreview()
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
fun GarageDoorErrorSensorConflictPreviewTest() {
    AppTheme {
        ErrorSensorConflictPreview()
    }
}

/**
 * Fidelity fixture for the app launcher / Play Store icon. Renders the production
 * [GarageDoorCanvas] closed door (doorOffset = [CLOSED_POSITION]) on the light-green
 * icon ground, framed like the 108dp adaptive foreground. The launcher icon is a
 * hand-ported vector (ic_launcher_foreground.xml) and the store icon a hand-ported
 * SVG (distribution/playstore/src/icon.svg); this screenshot is the real Compose
 * render to diff them against, so drift in GarageDoorCanvas shows up here.
 *
 * Colors are literals (the icon is theme-independent): #226B43 is closedFreshLight,
 * #D7E8CE is the icon ground. This is the screenshot-test source set, which is not
 * scanned by checkHardcodedColors.
 */
@PreviewTest
@Preview(name = "App icon - closed door")
@Composable
fun AppIconClosedDoorPreviewTest() {
    Box(
        modifier = Modifier
            .size(108.dp)
            .background(Color(0xFFD7E8CE)),
        contentAlignment = Alignment.Center,
    ) {
        GarageDoorCanvas(
            doorOffset = CLOSED_POSITION,
            modifier = Modifier.size(72.dp),
            color = Color(0xFF226B43),
        )
    }
}
