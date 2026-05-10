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
import com.chriscartland.garage.ui.HomeDashboardPreview1024dp
import com.chriscartland.garage.ui.HomeDashboardPreview1280dp
import com.chriscartland.garage.ui.HomeDashboardPreview600dp
import com.chriscartland.garage.ui.HomeDashboardPreview840dp
import com.chriscartland.garage.ui.HomeDashboardRailPreview700dp
import com.chriscartland.garage.ui.HomeDashboardRailPreview916dp
import com.chriscartland.garage.ui.HomeRailPreview700dp
import com.chriscartland.garage.ui.HomeTabPreview
import com.chriscartland.garage.ui.HomeTabStalePillPreview
import com.chriscartland.garage.ui.SettingsTabPreview
import com.chriscartland.garage.ui.ThreePaneDashboardLargeTabletPreview
import com.chriscartland.garage.ui.ThreePaneDashboardPhoneLandscapePreview
import com.chriscartland.garage.ui.ThreePaneDashboardTabletNarrowPreview
import com.chriscartland.garage.ui.ThreePaneDashboardWithDiagnosticsOverlayPreview
import com.chriscartland.garage.ui.home.HomeContentOpenSignedInPreview
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
        HomeContentOpenSignedInPreview()
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
fun HomeTabStalePillPreviewTest() {
    AppTheme {
        HomeTabStalePillPreview()
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
fun SettingsTabPreviewTest() {
    AppTheme {
        SettingsTabPreview()
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

// Wide-screen Home dashboard previews — exploratory, screenshots-only
// for pre-implementation review. Each fixture renders Home + History
// side-by-side in a wide-screen Scaffold (TopAppBar "Garage" on top,
// 2-tab NavigationBar Home + Settings on the bottom). Four widths
// chosen to span the activation range (600dp Medium boundary →
// 1280dp full Expanded). Light + dark per fixture for surface checks.
@PreviewTest
@Preview(showBackground = true, name = "Light", widthDp = 600, heightDp = 800)
@Preview(
    showBackground = true,
    name = "Dark",
    widthDp = 600,
    heightDp = 800,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun HomeDashboardPreview600dpTest() {
    AppTheme {
        HomeDashboardPreview600dp()
    }
}

// Home tab rendered with the new NavigationRailLeft chrome. 700dp width
// is unambiguously inside AppLayoutMode.Wide (≥600dp Medium, <1200dp
// Expanded). Verifies rail visual + start-side inset coordination.
@PreviewTest
@Preview(showBackground = true, name = "Light", widthDp = 700, heightDp = 800)
@Preview(
    showBackground = true,
    name = "Dark",
    widthDp = 700,
    heightDp = 800,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun HomeRailPreview700dpTest() {
    AppTheme {
        HomeRailPreview700dp()
    }
}

// Rail + 2-pane Home dashboard at 700dp (Wide low-bound) — verifies
// rail co-existing with the dashboard layout at the tightest Wide
// width.
@PreviewTest
@Preview(showBackground = true, name = "Light", widthDp = 700, heightDp = 800)
@Preview(
    showBackground = true,
    name = "Dark",
    widthDp = 700,
    heightDp = 800,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun HomeDashboardRailPreview700dpTest() {
    AppTheme {
        HomeDashboardRailPreview700dp()
    }
}

// Rail + 2-pane Home dashboard at 916dp x 411dp — Pixel 9 Pro in
// landscape. Widest landscape phone we expect to hit Wide; confirms
// the rail looks right at the upper end of Wide before Expanded
// (≥1200dp) kicks in.
@PreviewTest
@Preview(showBackground = true, name = "Light", widthDp = 916, heightDp = 411)
@Preview(
    showBackground = true,
    name = "Dark",
    widthDp = 916,
    heightDp = 411,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun HomeDashboardRailPreview916dpTest() {
    AppTheme {
        HomeDashboardRailPreview916dp()
    }
}

@PreviewTest
@Preview(showBackground = true, name = "Light", widthDp = 840, heightDp = 800)
@Preview(
    showBackground = true,
    name = "Dark",
    widthDp = 840,
    heightDp = 800,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun HomeDashboardPreview840dpTest() {
    AppTheme {
        HomeDashboardPreview840dp()
    }
}

@PreviewTest
@Preview(showBackground = true, name = "Light", widthDp = 1024, heightDp = 800)
@Preview(
    showBackground = true,
    name = "Dark",
    widthDp = 1024,
    heightDp = 800,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun HomeDashboardPreview1024dpTest() {
    AppTheme {
        HomeDashboardPreview1024dp()
    }
}

@PreviewTest
@Preview(showBackground = true, name = "Light", widthDp = 1280, heightDp = 800)
@Preview(
    showBackground = true,
    name = "Dark",
    widthDp = 1280,
    heightDp = 800,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun HomeDashboardPreview1280dpTest() {
    AppTheme {
        HomeDashboardPreview1280dp()
    }
}

@PreviewTest
@Preview(showBackground = true, name = "Light", widthDp = 916, heightDp = 411)
@Preview(
    showBackground = true,
    name = "Dark",
    widthDp = 916,
    heightDp = 411,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun ThreePaneDashboardPhoneLandscapePreviewTest() {
    AppTheme {
        ThreePaneDashboardPhoneLandscapePreview()
    }
}

@PreviewTest
@Preview(showBackground = true, name = "Light", widthDp = 1024, heightDp = 768)
@Preview(
    showBackground = true,
    name = "Dark",
    widthDp = 1024,
    heightDp = 768,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun ThreePaneDashboardTabletNarrowPreviewTest() {
    AppTheme {
        ThreePaneDashboardTabletNarrowPreview()
    }
}

@PreviewTest
@Preview(showBackground = true, name = "Light", widthDp = 1280, heightDp = 800)
@Preview(
    showBackground = true,
    name = "Dark",
    widthDp = 1280,
    heightDp = 800,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun ThreePaneDashboardLargeTabletPreviewTest() {
    AppTheme {
        ThreePaneDashboardLargeTabletPreview()
    }
}

@PreviewTest
@Preview(showBackground = true, name = "Light", widthDp = 1280, heightDp = 800)
@Preview(
    showBackground = true,
    name = "Dark",
    widthDp = 1280,
    heightDp = 800,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun ThreePaneDashboardWithDiagnosticsOverlayPreviewTest() {
    AppTheme {
        ThreePaneDashboardWithDiagnosticsOverlayPreview()
    }
}
