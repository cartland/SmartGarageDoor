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

package com.chriscartland.garage.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chriscartland.garage.GarageApplication
import com.chriscartland.garage.applogger.exportAppLogCsvToUri
import com.chriscartland.garage.di.rememberAppComponent
import com.chriscartland.garage.ui.theme.ButtonSpacing
import com.chriscartland.garage.ui.theme.CardPadding
import com.chriscartland.garage.ui.theme.DividerInset
import com.chriscartland.garage.ui.theme.PreviewScreenSurface
import com.chriscartland.garage.ui.theme.Spacing
import com.chriscartland.garage.usecase.DiagnosticsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * One row in the diagnostics counter list. Plain pair of label + count.
 * Numbers carry the meaning, label is descriptive.
 */
data class DiagnosticsCounter(
    val label: String,
    val value: Long,
)

/**
 * Production sub-screen reached from Settings → About → Diagnostics.
 * Uses the parent's `TopAppBar` for chrome (matching the FunctionList
 * pattern) — see Main.kt for title + back-arrow handling.
 */
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val component = rememberAppComponent()
    val diagnosticsViewModel: DiagnosticsViewModel = viewModel { component.diagnosticsViewModel }
    val context = LocalContext.current

    val initCurrent by diagnosticsViewModel.initCurrentDoorCount.collectAsState()
    val initRecent by diagnosticsViewModel.initRecentDoorCount.collectAsState()
    val fetchCurrent by diagnosticsViewModel.userFetchCurrentDoorCount.collectAsState()
    val fetchRecent by diagnosticsViewModel.userFetchRecentDoorCount.collectAsState()
    val fcmReceived by diagnosticsViewModel.fcmReceivedDoorCount.collectAsState()
    val fcmSubscribe by diagnosticsViewModel.fcmSubscribeTopicCount.collectAsState()
    val exceededFcm by diagnosticsViewModel.exceededExpectedTimeWithoutFcmCount.collectAsState()
    val timeWithoutFcmInRange by diagnosticsViewModel.timeWithoutFcmInExpectedRangeCount.collectAsState()

    val counters = listOf(
        DiagnosticsCounter("App init (current door)", initCurrent),
        DiagnosticsCounter("App init (recent doors)", initRecent),
        DiagnosticsCounter("Door fetch (current)", fetchCurrent),
        DiagnosticsCounter("Door fetch (recent)", fetchRecent),
        DiagnosticsCounter("FCM subscribe", fcmSubscribe),
        DiagnosticsCounter("FCM received", fcmReceived),
        DiagnosticsCounter("FCM exceeded expected timeout", exceededFcm),
        DiagnosticsCounter("FCM in expected range", timeWithoutFcmInRange),
    )

    val clearInFlight by diagnosticsViewModel.clearInFlight.collectAsState()

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        val app = context.applicationContext as GarageApplication
        CoroutineScope(Dispatchers.IO).launch {
            exportAppLogCsvToUri(app.component.appLoggerRepository, context, uri)
        }
    }

    DiagnosticsContent(
        counters = counters,
        onExportCsv = {
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"))
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/csv"
                putExtra(Intent.EXTRA_TITLE, "garage-app-log-$timestamp.csv")
            }
            csvLauncher.launch(intent)
        },
        onClearAll = { diagnosticsViewModel.clearDiagnostics() },
        clearInFlight = clearInFlight,
        modifier = modifier,
    )
    // onBack is supplied by the parent's TopAppBar back-arrow click handler;
    // not used inside DiagnosticsContent.
    @Suppress("UNUSED_EXPRESSION")
    onBack
}

/**
 * Stateless body — counters + Export CSV + Clear all (with confirmation
 * dialog). No Scaffold/TopAppBar (the parent provides the chrome).
 * Previewable on its own.
 */
@Composable
fun DiagnosticsContent(
    counters: List<DiagnosticsCounter>,
    onExportCsv: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    clearInFlight: Boolean = false,
) {
    var confirmClearOpen by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = Spacing.Screen,
                vertical = Spacing.ListVertical,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.BetweenItems),
        ) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        counters.forEachIndexed { index, c ->
                            CounterRow(c.label, c.value)
                            if (index < counters.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = DividerInset.FullWidth))
                            }
                        }
                    }
                }
            }
        }
        // Action buttons sit in a sub-Column whose Arrangement.spacedBy
        // owns the gap between buttons (parent-vs-child spacing rule:
        // children don't claim ownership of their outer gap). Horizontal
        // and bottom screen padding move from per-button modifiers to the
        // Column wrapper so the rule "single source of horizontal layout"
        // holds inside this section too.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Screen)
                .padding(vertical = Spacing.ListVertical),
            verticalArrangement = Arrangement.spacedBy(ButtonSpacing.Stacked),
        ) {
            Button(
                onClick = onExportCsv,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = null,
                )
                Text(
                    text = "Export CSV",
                    modifier = Modifier.padding(start = ButtonSpacing.IconText),
                )
            }
            OutlinedButton(
                onClick = { confirmClearOpen = true },
                enabled = !clearInFlight,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // While the clear is in flight, swap the trash icon for a small
                // spinner and the label for "Clearing…". Button is disabled so
                // a second tap can't queue another clear during the wait.
                if (clearInFlight) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Clearing…",
                        modifier = Modifier.padding(start = ButtonSpacing.IconText),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.DeleteForever,
                        contentDescription = null,
                    )
                    Text(
                        text = "Clear all diagnostics",
                        modifier = Modifier.padding(start = ButtonSpacing.IconText),
                    )
                }
            }
        }
    }

    if (confirmClearOpen) {
        ClearDiagnosticsDialog(
            onConfirm = {
                confirmClearOpen = false
                onClearAll()
            },
            onDismiss = { confirmClearOpen = false },
        )
    }
}

/**
 * Confirmation for the destructive "Clear all diagnostics" action.
 * Confirm button is error-colored so users notice it's destructive
 * before the second tap. Dismiss / outside-tap = cancel.
 */
@Composable
fun ClearDiagnosticsDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text("Clear all diagnostics?") },
        text = {
            Text(
                "Resets every counter on this screen to 0 and deletes the " +
                    "exportable event log. Door history and other app " +
                    "settings are not affected. This cannot be undone.",
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) { Text("Clear") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun CounterRow(
    label: String,
    value: Long,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(CardPadding.Compact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Preview
@Composable
fun DiagnosticsContentPreview() {
    PreviewScreenSurface {
        DiagnosticsContent(
            counters = listOf(
                DiagnosticsCounter("App init (current door)", 42),
                DiagnosticsCounter("App init (recent doors)", 17),
                DiagnosticsCounter("Door fetch (current)", 836),
                DiagnosticsCounter("Door fetch (recent)", 412),
                DiagnosticsCounter("FCM subscribe", 8),
                DiagnosticsCounter("FCM received", 1247),
                DiagnosticsCounter("FCM exceeded expected timeout", 3),
                DiagnosticsCounter("FCM in expected range", 1244),
            ),
            onExportCsv = {},
            onClearAll = {},
        )
    }
}

@Preview
@Composable
fun ClearDiagnosticsDialogPreview() {
    ClearDiagnosticsDialog(
        onConfirm = {},
        onDismiss = {},
    )
}

@Preview
@Composable
fun DiagnosticsContentClearInFlightPreview() {
    // Shows the Clear button mid-action: trash icon swapped for a small
    // spinner and the label flipped to "Clearing…", button disabled.
    PreviewScreenSurface {
        DiagnosticsContent(
            counters = listOf(
                DiagnosticsCounter("App init (current door)", 42),
                DiagnosticsCounter("App init (recent doors)", 17),
                DiagnosticsCounter("Door fetch (current)", 836),
                DiagnosticsCounter("Door fetch (recent)", 412),
                DiagnosticsCounter("FCM subscribe", 8),
                DiagnosticsCounter("FCM received", 1247),
                DiagnosticsCounter("FCM exceeded expected timeout", 3),
                DiagnosticsCounter("FCM in expected range", 1244),
            ),
            onExportCsv = {},
            onClearAll = {},
            clearInFlight = true,
        )
    }
}
