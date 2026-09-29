/*
 * Copyright 2024 Chris Cartland. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.chriscartland.garage.screenshottests

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.chriscartland.garage.ui.theme.AppTheme
import com.chriscartland.garage.ui.theme.SpacingTokensReferencePreview

/**
 * Pins the rendered values of every spacing token in [Spacing.kt],
 * [CardPadding], [DividerInset], [ButtonSpacing], [ParagraphSpacing],
 * and [ContentWidth].
 *
 * If a token value drifts (e.g., `Spacing.Tight` changes from 4dp to
 * 6dp), this screenshot diff catches it.
 */
@PreviewTest
@Preview(showBackground = true, name = "Light", widthDp = 360, heightDp = 1500)
@Preview(
    showBackground = true,
    name = "Dark",
    widthDp = 360,
    heightDp = 1500,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun SpacingTokensReferencePreviewTest() {
    AppTheme { SpacingTokensReferencePreview() }
}
