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

package com.chriscartland.garage.viewmodel

import com.chriscartland.garage.usecase.DoorDataHealthManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Data health as a settable flag (ADR-017 fake conventions), for the same
 * reason as [FakeCheckInStalenessManager]: a screen ViewModel consumes only
 * the verdict. The RULE (three in a row, a minute without recovery) is
 * tested against the real manager in `DoorDataHealthManagerTest`.
 */
class FakeDoorDataHealthManager(
    initiallyUnhealthy: Boolean = false,
) : DoorDataHealthManager {
    private val flow = MutableStateFlow(initiallyUnhealthy)

    override val isDoorDataUnhealthy: StateFlow<Boolean> = flow

    override fun start() = Unit

    fun setUnhealthy(unhealthy: Boolean) {
        flow.value = unhealthy
    }
}
