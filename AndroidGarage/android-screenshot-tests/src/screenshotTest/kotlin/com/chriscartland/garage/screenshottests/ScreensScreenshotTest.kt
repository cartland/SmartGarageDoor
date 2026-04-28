package com.chriscartland.garage.screenshottests

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.chriscartland.garage.ui.DoorHistoryContentPreview
import com.chriscartland.garage.ui.FunctionListContentDeniedPreview
import com.chriscartland.garage.ui.FunctionListContentPreview
import com.chriscartland.garage.ui.FunctionListScreenDeniedPreview
import com.chriscartland.garage.ui.FunctionListScreenPreview
import com.chriscartland.garage.ui.HistoryTabPreview
import com.chriscartland.garage.ui.HomeContentPreview
import com.chriscartland.garage.ui.HomeTabPreview
import com.chriscartland.garage.ui.theme.AppTheme

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
fun HomeTabPreviewTest() {
    AppTheme {
        HomeTabPreview()
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
fun HistoryTabPreviewTest() {
    AppTheme {
        HistoryTabPreview()
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
fun FunctionListScreenPreviewTest() {
    AppTheme {
        FunctionListScreenPreview()
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
fun FunctionListContentPreviewTest() {
    AppTheme {
        FunctionListContentPreview()
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
fun FunctionListContentDeniedPreviewTest() {
    AppTheme {
        FunctionListContentDeniedPreview()
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
fun FunctionListScreenDeniedPreviewTest() {
    AppTheme {
        FunctionListScreenDeniedPreview()
    }
}
