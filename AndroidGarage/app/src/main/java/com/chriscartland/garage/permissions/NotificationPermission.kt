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

package com.chriscartland.garage.permissions

import android.Manifest
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState

/**
 * Returns a [PermissionState] to request notifications permission.
 *
 * [android.Manifest.permission.POST_NOTIFICATIONS] is not available on API < 33.
 * On API < 33, notification permissions are assumed to be granted.
 * On API < 33, we return a [PermissionState] that is always granted.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberNotificationPermissionState(): PermissionState {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Requires API 33+.
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        // Assume permission is granted on older API versions.
        rememberPermissionAlwaysGranted("android.permission.POST_NOTIFICATIONS")
    }
}

/**
 * Returns a [PermissionState] that is always granted.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberPermissionAlwaysGranted(permission: String): PermissionState {
    return remember(permission) {
        object : PermissionState {
            override val permission = permission
            override val status = PermissionStatus.Granted
            override fun launchPermissionRequest() { /* Do nothing */ }
        }
    }
}

/**
 * Returns a string that explains why notifications are needed.
 *
 * If the button is clicked more times, show increasingly detailed messages
 * about managing permissions.
 */
fun notificationJustificationText(attemptCount: Int = 0): String {
    val baseText = "Please turn on notifications to be notified when the door is left open."
    return buildString {
        append(baseText)
        if (attemptCount > 2) {
            append("\nYou can manage permissions in the Android system settings.")
        }
        if (attemptCount > 3) {
            append("\nAndroid might be blocking requests because " +
                    "the permission was denied multiple times.")
        }
        if (attemptCount > 4) {
            append("\nYou have clicked the button $attemptCount times.")
        }
    }
}
