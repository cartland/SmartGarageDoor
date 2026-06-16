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

package com.chriscartland.garage.testcommon

import com.chriscartland.garage.domain.model.TestNotificationSandboxState
import com.chriscartland.garage.domain.model.TestNotificationTopic
import com.chriscartland.garage.domain.repository.TestNotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Simple in-memory [TestNotificationRepository] for ViewModel tests. Does NOT
 * reproduce the real max-one-subscription invariant — that is covered by
 * `DefaultTestNotificationRepositoryTest`. This just tracks topic + subscribed
 * so VM-level state wiring can be asserted.
 */
class FakeTestNotificationRepository : TestNotificationRepository {
    private val _state = MutableStateFlow(TestNotificationSandboxState())
    override val state: StateFlow<TestNotificationSandboxState> = _state

    private var counter = 0

    override suspend fun getTopic(): TestNotificationTopic {
        val existing = _state.value.topic
        if (existing != null) return existing
        val fresh = TestNotificationTopic("testNotification-fake${counter++}")
        _state.update { it.copy(topic = fresh) }
        return fresh
    }

    override suspend fun changeTopic(): TestNotificationTopic {
        val fresh = TestNotificationTopic("testNotification-fake${counter++}")
        _state.update { it.copy(topic = fresh) }
        return fresh
    }

    override suspend fun subscribe() {
        _state.update { it.copy(isSubscribed = true) }
    }

    override suspend fun unsubscribe() {
        _state.update { it.copy(isSubscribed = false) }
    }
}
