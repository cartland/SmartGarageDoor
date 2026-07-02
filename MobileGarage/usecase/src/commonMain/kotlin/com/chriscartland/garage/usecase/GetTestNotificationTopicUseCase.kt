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

package com.chriscartland.garage.usecase

import com.chriscartland.garage.domain.model.TestNotificationTopic
import com.chriscartland.garage.domain.repository.TestNotificationRepository

/**
 * Get-or-generate the personal test-notification topic. Thin delegate — the
 * max-one-subscription invariant lives entirely in [TestNotificationRepository].
 */
class GetTestNotificationTopicUseCase(
    private val repository: TestNotificationRepository,
) {
    suspend operator fun invoke(): TestNotificationTopic = repository.getTopic()
}
