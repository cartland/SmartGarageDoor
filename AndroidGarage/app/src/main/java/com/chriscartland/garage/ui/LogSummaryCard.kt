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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chriscartland.garage.applogger.AppLoggerViewModelImpl
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LogSummaryCard(
    modifier: Modifier = Modifier,
    appLoggerViewModel: AppLoggerViewModelImpl = hiltViewModel(),
) {
    val initCurrent by appLoggerViewModel.initCurrentDoorCount.collectAsState()
    val initRecent by appLoggerViewModel.initRecentDoorCount.collectAsState()
    val fetchCurrent by appLoggerViewModel.userFetchCurrentDoorCount.collectAsState()
    val fetchRecent by appLoggerViewModel.userFetchRecentDoorCount.collectAsState()
    val fcmReceived by appLoggerViewModel.fcmReceivedDoorCount.collectAsState()
    val fcmSubscribe by appLoggerViewModel.fcmSubscribeTopicCount.collectAsState()
    val exceededExpectedTimeWithoutFcm by appLoggerViewModel.exceededExpectedTimeWithoutFcmCount.collectAsState()
    val timeWithoutFcmInExpectedRange by appLoggerViewModel.timeWithoutFcmInExpectedRangeCount.collectAsState()
    LogSummaryCard(
        modifier = modifier,
        onDownload = { context, uri ->
            appLoggerViewModel.writeToUri(context, uri)
        },
        initCurrent = initCurrent,
        initRecent = initRecent,
        fetchCurrent = fetchCurrent,
        fetchRecent = fetchRecent,
        fcmReceived = fcmReceived,
        fcmSubscribe = fcmSubscribe,
        exceededExpectedTimeWithoutFcm = exceededExpectedTimeWithoutFcm,
        timeWithoutFcmInExpectedRange = timeWithoutFcmInExpectedRange,
    )
}

@Composable
fun LogSummaryCard(
    modifier: Modifier = Modifier,
    onDownload: (context: Context, uri: Uri) -> Unit = { _, _ -> },
    initCurrent: Long = 0,
    initRecent: Long = 0,
    fetchCurrent: Long = 0,
    fetchRecent: Long = 0,
    fcmReceived: Long = 0,
    fcmSubscribe: Long = 0,
    exceededExpectedTimeWithoutFcm: Long = 0,
    timeWithoutFcmInExpectedRange: Long = 0,
) {
    ExpandableColumnCard(
        title = "Logs",
        modifier = modifier,
        startExpanded = true,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "Init current door: $initCurrent",
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = "Init recent events: $initRecent",
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = "User fetch current: $fetchCurrent",
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = "User fetch recent: $fetchRecent",
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = "FCM subscribe: $fcmSubscribe",
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = "FCM receive data: $fcmReceived",
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = "Exceeded expected time without FCM: $exceededExpectedTimeWithoutFcm",
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = "Received data after FCM delay: $timeWithoutFcmInExpectedRange",
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            DownloadAppLoggerDatabaseButton(
                onDownload = onDownload,
            ) {
                Text(text = "Download")
            }
        }
    }
}

@Composable
fun DownloadAppLoggerDatabaseButton(
    onDownload: (Context, Uri) -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                coroutineScope.launch(Dispatchers.IO) {
                    onDownload(context, uri)
                }
            }
        }
    }
    val createDocument = {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TITLE,
                LocalDateTime.now().let {
                    val formatter = DateTimeFormatter.ofPattern("yyyyMMdd.HHmmss")
                    val timestamp = it.format(formatter)
                    "$timestamp.txt"
                }
            )
        }
        launcher.launch(intent)
    }
    Button(
        onClick = { createDocument() },
    ) {
        content()
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Preview
@Composable
fun LogSummaryCardPreview() {
    LogSummaryCard(
        initCurrent = 1,
        initRecent = 2,
        fetchCurrent = 3,
        fetchRecent = 4,
        fcmReceived = 5,
        fcmSubscribe = 6,
        exceededExpectedTimeWithoutFcm = 7,
        timeWithoutFcmInExpectedRange = 8,
    )
}
