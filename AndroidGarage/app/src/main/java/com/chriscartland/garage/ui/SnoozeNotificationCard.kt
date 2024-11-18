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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chriscartland.garage.snoozenotifications.SnoozeDurationUIOption

@Composable
fun SnoozeNotificationCard(
    text: String,
    snoozeText: String,
    saveText: String,
    modifier: Modifier = Modifier,
    onSnooze: (snooze: SnoozeDurationUIOption) -> Unit = {},
) {
    var showOptions by remember { mutableStateOf(false) }
    val options: List<SnoozeDurationUIOption> = SnoozeDurationUIOption.entries
    var selectedOption = remember { options.first() }
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .height(IntrinsicSize.Min)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text,
                    modifier = Modifier
                        .weight(1f)
                        .align(Alignment.CenterVertically),
                )
                Spacer(Modifier.width(16.dp))
                Button(onClick = {
                    if (showOptions) { // Save
                        onSnooze(selectedOption)
                    }
                    showOptions = !showOptions
                }) {
                    Text(
                        if (showOptions) saveText else snoozeText,
                        maxLines = 2,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
            if (showOptions) {
                RadioGroup(
                    options = options,
                    selectedOption = selectedOption,
                    onOptionSelected = { option ->
                        selectedOption = option
                    },
                    optionFormatter = { option ->
                        option.duration.toComponents { hours, minutes, _, _ ->
                            val components = mutableListOf<String>()
                            when (hours) {
                                0L -> {}
                                1L -> components.add("1 hour")
                                else -> {
                                    components.add("$hours hours")
                                }
                            }
                            when (minutes) {
                                0 -> {}
                                1 -> components.add("1 minute")
                                else -> {
                                    components.add("$minutes minutes")
                                }
                            }
                            // When there are no components, add a disable option.
                            when (components.size) {
                                0 -> components.add("Do not snooze")
                            }
                            components.joinToString(" ")
                        }
                    }
                )
            }
        }
    }
}

@Preview
@Composable
fun SnoozeNotificationCardPreview() {
    SnoozeNotificationCard(
        text = "Snooze notifications",
        snoozeText = "Snooze",
        saveText = "Save",
    )
}

@Composable
fun <T> RadioGroup(
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    optionFormatter: (T) -> String = { it.toString() },
) {
    var selected by remember { mutableStateOf(selectedOption) }
    Column {
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected == option,
                    onClick = {
                        selected = option
                        onOptionSelected(option)
                    },
                )
                Text(
                    text = optionFormatter(option),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
