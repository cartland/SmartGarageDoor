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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.chriscartland.garage.domain.model.SnoozeAction
import com.chriscartland.garage.domain.model.SnoozeDurationUIOption
import com.chriscartland.garage.domain.model.SnoozeState

@Composable
fun SnoozeNotificationCard(
    snoozeState: SnoozeState,
    snoozeAction: SnoozeAction,
    modifier: Modifier = Modifier,
    onSnooze: (snooze: SnoozeDurationUIOption) -> Unit = {},
    colors: CardColors = CardDefaults.cardColors(),
) {
    var showOptions by remember { mutableStateOf(false) }
    val options: List<SnoozeDurationUIOption> = SnoozeDurationUIOption.entries
    var selectedOption by remember { mutableStateOf<SnoozeDurationUIOption?>(null) }
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier,
        colors = colors,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .height(IntrinsicSize.Min)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .align(Alignment.CenterVertically),
                ) {
                    SnoozeStatusText(snoozeState)
                    SnoozeActionOverlay(snoozeAction)
                }
                Spacer(Modifier.width(16.dp))
                if (snoozeAction is SnoozeAction.Sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                    )
                } else {
                    Button(onClick = {
                        if (showOptions) {
                            selectedOption?.let {
                                onSnooze(it)
                                selectedOption = null
                            }
                        }
                        showOptions = !showOptions
                    }) {
                        Text(
                            if (showOptions) {
                                if (selectedOption == null) "Cancel" else "Save"
                            } else {
                                "Snooze"
                            },
                            maxLines = 2,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                }
            }
            if (showOptions) {
                RadioGroup(
                    options = options,
                    selectedOption = selectedOption,
                    onOptionSelected = { option ->
                        if (option == selectedOption) {
                            selectedOption = null
                        } else {
                            selectedOption = option
                        }
                    },
                    optionFormatter = { option ->
                        option.duration.toComponents { hours, minutes, _, _ ->
                            val components = mutableListOf<String>()
                            when (hours) {
                                0L -> {}
                                1L -> components.add("1 hour")
                                else -> components.add("$hours hours")
                            }
                            when (minutes) {
                                0 -> {}
                                1 -> components.add("1 minute")
                                else -> components.add("$minutes minutes")
                            }
                            when (components.size) {
                                0 -> components.add("Do not snooze")
                            }
                            components.joinToString(" ")
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SnoozeStatusText(snoozeState: SnoozeState) {
    when (snoozeState) {
        SnoozeState.Loading -> {
            Text("Loading snooze status...")
        }
        SnoozeState.NotSnoozing -> {
            Text("Snooze notifications")
        }
        is SnoozeState.Snoozing -> {
            val snoozeTime = snoozeState.untilEpochSeconds.toFriendlyTimeShort()
            Text("Snoozing notifications until $snoozeTime")
            Text(
                text = "or until the door moves",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun SnoozeActionOverlay(snoozeAction: SnoozeAction) {
    when (snoozeAction) {
        SnoozeAction.Idle -> { /* No overlay */ }
        SnoozeAction.Sending -> {
            Text(
                text = "Saving...",
                style = MaterialTheme.typography.labelSmall,
            )
        }
        SnoozeAction.Succeeded.Cleared -> {
            Text(
                text = "Notifications resumed",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        is SnoozeAction.Succeeded.Set -> {
            val snoozeTime = snoozeAction.untilEpochSeconds.toFriendlyTimeShort()
            Text(
                text = "Saved! Snoozing until $snoozeTime",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        SnoozeAction.Failed.NotAuthenticated -> {
            Text(
                text = "Sign in to snooze notifications",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        SnoozeAction.Failed.MissingData -> {
            Text(
                text = "No door event available",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        SnoozeAction.Failed.NetworkError -> {
            Text(
                text = "Couldn't reach server",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Preview
@Composable
fun SnoozeNotificationCardPreview() {
    SnoozeNotificationCard(
        snoozeState = SnoozeState.Snoozing(999999999999L),
        snoozeAction = SnoozeAction.Idle,
    )
}

@Preview
@Composable
fun SnoozeNotificationCardLoadingPreview() {
    SnoozeNotificationCard(
        snoozeState = SnoozeState.Loading,
        snoozeAction = SnoozeAction.Idle,
    )
}

@Preview
@Composable
fun SnoozeNotificationCardSendingPreview() {
    SnoozeNotificationCard(
        snoozeState = SnoozeState.NotSnoozing,
        snoozeAction = SnoozeAction.Sending,
    )
}

@Preview
@Composable
fun SnoozeNotificationCardSucceededPreview() {
    SnoozeNotificationCard(
        snoozeState = SnoozeState.NotSnoozing,
        snoozeAction = SnoozeAction.Succeeded.Set(999999999999L),
    )
}

@Preview
@Composable
fun SnoozeNotificationCardClearedPreview() {
    SnoozeNotificationCard(
        snoozeState = SnoozeState.NotSnoozing,
        snoozeAction = SnoozeAction.Succeeded.Cleared,
    )
}

@Preview
@Composable
fun SnoozeNotificationCardErrorPreview() {
    SnoozeNotificationCard(
        snoozeState = SnoozeState.Snoozing(999999999999L),
        snoozeAction = SnoozeAction.Failed.NetworkError,
    )
}

@Composable
fun <T> RadioGroup(
    options: List<T>,
    selectedOption: T?,
    onOptionSelected: (T) -> Unit,
    optionFormatter: (T) -> String = { it.toString() },
) {
    Column {
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selectedOption == option,
                    onClick = {
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
