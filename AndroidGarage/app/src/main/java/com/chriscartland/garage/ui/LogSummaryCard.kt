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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

data class LogSummary(
    val initCurrent: Long,
    val initRecent: Long,
    val fetchCurrent: Long,
    val fetchRecent: Long,
    val fcmSubscribe: Long,
    val fcmReceived: Long,
)

@Composable
fun LogSummaryCard(
    logSummary: LogSummary,
    modifier: Modifier = Modifier,
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = "Log Summary",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Init current door: ${logSummary.initCurrent}",
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = "Init recent events: ${logSummary.initRecent}",
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = "User fetch current: ${logSummary.fetchCurrent}",
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = "User fetch recent: ${logSummary.fetchRecent}",
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = "FCM subscribe: ${logSummary.fcmSubscribe}",
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = "FCM received door: ${logSummary.fcmReceived}",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Preview
@Composable
fun LogSummaryCardPreview() {
    LogSummaryCard(
        logSummary = LogSummary(
            initCurrent = 1,
            initRecent = 2,
            fetchCurrent = 3,
            fetchRecent = 4,
            fcmSubscribe = 5,
            fcmReceived = 6,
        )
    )
}
