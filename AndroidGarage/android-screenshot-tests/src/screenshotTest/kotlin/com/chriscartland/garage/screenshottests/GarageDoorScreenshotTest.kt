package com.chriscartland.garage.screenshottests

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.chriscartland.garage.ui.ClosedPreview
import com.chriscartland.garage.ui.ClosingPreview
import com.chriscartland.garage.ui.ClosingTooLongPreview
import com.chriscartland.garage.ui.ErrorSensorConflictPreview
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
