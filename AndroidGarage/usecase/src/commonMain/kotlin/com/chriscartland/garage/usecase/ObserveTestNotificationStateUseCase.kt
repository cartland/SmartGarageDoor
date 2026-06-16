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

import com.chriscartland.garage.domain.model.TestNotificationSandboxState
import com.chriscartland.garage.domain.repository.TestNotificationRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * Observe the test-notification sandbox state (current topic + whether
 * subscribed). The read-side counterpart to the four action UseCases — the VM
 * can't inject the repository (layer-import lint), so this exposes the repo's
 * `StateFlow` by reference (ADR-022 pass-through: no `stateIn`, no rewrap).
 */
class ObserveTestNotificationStateUseCase(
    private val repository: TestNotificationRepository,
) {
    operator fun invoke(): StateFlow<TestNotificationSandboxState> = repository.state
}
