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
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class LogSummary(
    val initCurrent: Long,
    val initRecent: Long,
    val fetchCurrent: Long,
    val fetchRecent: Long,
    val fcmSubscribe: Long,
    val fcmReceived: Long,
)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LogSummaryCard(
    logSummary: LogSummary,
    modifier: Modifier = Modifier,
    onDownload: (context: Context, uri: Uri) -> Unit = { _, _ -> },
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

@Composable
fun CreateTxtFile() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                coroutineScope.launch(Dispatchers.IO) {
                    writeDataToUri(context, uri)
                }
            }
        }
    }
    val createDocument = {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "test.txt")
        }
        launcher.launch(intent)
    }

    Button(onClick = { createDocument() }) {
        Text("Create TXT File")
    }
}

private suspend fun writeDataToUri(context: Context, uri: Uri) {
    withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write("Line 1: This is a test\n".toByteArray())
                outputStream.write("Line 2: This is a second line".toByteArray())
            }
        } catch (e: Exception) {
            // Handle exceptions (e.g., file I/O errors)
            Log.d("CreateTxtFile", "Error writing to file: ${e.message}")
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
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
