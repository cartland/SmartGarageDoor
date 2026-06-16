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

package com.chriscartland.garage.domain.model

import kotlin.jvm.JvmInline

/**
 * A personal FCM topic for the test-notification sandbox (a diagnostic
 * feature). Always of the form `testNotification-<uuid>` — disjoint from the
 * production `door_open-*` and `buttonHealth-*` namespaces, so the sandbox can
 * never touch production subscriptions.
 */
@JvmInline
value class TestNotificationTopic(
    val string: String,
)

/**
 * Observable state of the test-notification sandbox for the UI.
 *
 * @property topic the current personal topic, or null before it is generated.
 * @property isSubscribed whether the device is currently subscribed to [topic].
 */
data class TestNotificationSandboxState(
    val topic: TestNotificationTopic? = null,
    val isSubscribed: Boolean = false,
)
