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

package com.chriscartland.garage.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Visual reference for the spacing tokens defined in [Spacing.kt].
 * Renders one labeled region per token in its intended context, so a
 * screenshot diff catches any value drift.
 *
 * Captured by [SpacingTokensScreenshotTest] in light + dark.
 */
@Preview(showBackground = true, name = "Light", widthDp = 360, heightDp = 1500)
@Composable
fun SpacingTokensReferencePreview() {
    PreviewScreenSurface {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Screen)
                .padding(vertical = Spacing.ListVertical),
            verticalArrangement = Arrangement.spacedBy(Spacing.BetweenItems),
        ) {
            // Section header — exercises Spacing.SectionHeader{Start, Top, Bottom}
            SectionHeaderRef("SECTION HEADER (Spacing.SectionHeader*)")

            // CardPadding.Standard
            CardSwatchRef("CardPadding.Standard", CardPadding.Standard)

            // CardPadding.Tall
            CardSwatchRef("CardPadding.Tall", CardPadding.Tall)

            // CardPadding.Compact
            CardSwatchRef("CardPadding.Compact", CardPadding.Compact)

            // DividerInset family — three rows with three different inset values
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    DividerRow("Row above ListItem (56dp inset)")
                    HorizontalDivider(modifier = Modifier.padding(start = DividerInset.ListItem))
                    DividerRow("Row above LargeLeading (72dp inset)")
                    HorizontalDivider(modifier = Modifier.padding(start = DividerInset.LargeLeading))
                    DividerRow("Row above FullWidth (16dp inset)")
                    HorizontalDivider(modifier = Modifier.padding(horizontal = DividerInset.FullWidth))
                    DividerRow("Row below all dividers")
                }
            }

            // ButtonSpacing.Stacked — buttons with vertical gap
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(CardPadding.Standard)) {
                    Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                        Text("First button")
                    }
                    Spacer(modifier = Modifier.height(ButtonSpacing.Stacked))
                    Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                        Text("Second button (ButtonSpacing.Stacked above)")
                    }
                }
            }

            // ButtonSpacing.Inline — text + button in a row
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(CardPadding.Compact),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Inline message", modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(ButtonSpacing.Inline))
                    Button(onClick = {}) { Text("Action") }
                }
            }

            // ParagraphSpacing — title to body to icon-to-text
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(CardPadding.Standard)) {
                    Text(
                        "Title (ParagraphSpacing.TitleToBody below)",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(ParagraphSpacing.TitleToBody))
                    Text(
                        "Body text follows the title with a 4dp gap.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(ParagraphSpacing.IconToText))
                    Text(
                        "(ParagraphSpacing.IconToText above; demonstrating gap)",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            // Spacing.Tight — supporting text gap
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(CardPadding.Standard)) {
                    Text("Headline", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(Spacing.Tight))
                    Text(
                        "Supporting text (Spacing.Tight above)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeaderRef(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = Spacing.SectionHeaderStart,
                top = Spacing.SectionHeaderTop,
                bottom = Spacing.SectionHeaderBottom,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun CardSwatchRef(
    label: String,
    padding: PaddingValues,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DividerRow(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
