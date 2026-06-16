/*
 * Copyright 2026 Chris Cartland. All rights reserved.
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

package com.chriscartland.garage.fcm

/** Parsed content of a test-notification-sandbox FCM data payload. */
data class TestNotificationContent(
    val title: String,
    val body: String,
    val tag: String,
)

/**
 * Pure parser for the test-notification sandbox data payload. Lenient by design
 * — a minimal payload still renders something, so the sandbox always shows a
 * notification. `tag` drives Android's inline replacement: sending a later
 * message with the same `tag` replaces the existing notification in place
 * (the mechanism the reliable "Resolved" feature will rely on).
 */
object TestNotificationPayload {
    const val DEFAULT_TAG = "test"

    fun parse(data: Map<String, String>): TestNotificationContent =
        TestNotificationContent(
            title = data["title"]?.ifEmpty { null } ?: "Test notification",
            body = data["body"] ?: "",
            tag = data["tag"]?.ifEmpty { null } ?: DEFAULT_TAG,
        )
}
