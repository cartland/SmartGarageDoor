/*
 * Copyright 2021 Chris Cartland. All rights reserved.
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

package com.chriscartland.garage

import com.google.firebase.firestore.DocumentSnapshot

data class ServerConfig(
    val buildTimestamp: String? = null,
    val remoteButtonPushKey: String? = null,
    val remoteButtonBuildTimestamp: String? = null,
    val host: String? = null,
    val path: String? = null,
    val remoteButtonEnabled: Boolean = false,
    val remoteButtonAuthorizedEmails: Array<String>? = null
)

fun DocumentSnapshot.toServerConfig(): ServerConfig {
    val data = this.data as? Map<*, *> ?: return ServerConfig()
    val body = data["body"] as? Map<*, *> ?: return ServerConfig()
    val buildTimestamp = body["buildTimestamp"] as? String?
    val remoteButtonPushKey = body["remoteButtonPushKey"] as? String?
    val remoteButtonBuildTimestamp = body["remoteButtonBuildTimestamp"] as? String?
    val host = body["host"] as? String?
    val path = body["path"] as? String?
    val remoteButtonEnabled = body["remoteButtonEnabled"] as? Boolean ?: false
    val remoteButtonAuthorizedEmails = (body["remoteButtonAuthorizedEmails"] as? ArrayList<String>)?.toTypedArray()
    return ServerConfig(
        buildTimestamp = buildTimestamp,
        remoteButtonPushKey = remoteButtonPushKey,
        remoteButtonBuildTimestamp =remoteButtonBuildTimestamp,
        host = host,
        path = path,
        remoteButtonEnabled = remoteButtonEnabled,
        remoteButtonAuthorizedEmails = remoteButtonAuthorizedEmails
    )
}