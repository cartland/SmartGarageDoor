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

package com.chriscartland.garage.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ExpandableColumnCard(
    title: String,
    modifier: Modifier = Modifier.Companion,
    startExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(startExpanded) }
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.Companion
                .fillMaxWidth(),
            horizontalAlignment = Alignment.Companion.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.Companion.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Companion.CenterVertically
            ) {
                Spacer(modifier = Modifier.Companion.width(32.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.Companion.weight(1f),
                    textAlign = TextAlign.Companion.Center,
                )
                IconButton(
                    onClick = { expanded = !expanded },
                ) {
                    Icon(
                        modifier = Modifier.Companion.width(32.dp),
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(durationMillis = 300)),
                exit = shrinkVertically(animationSpec = tween(durationMillis = 300))
            ) {
                Column(
                    modifier = Modifier.Companion
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.Companion.CenterHorizontally,
                ) {
                    content()
                }
            }
        }
    }
}