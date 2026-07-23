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

import com.chriscartland.garage.domain.model.WatchAppStatus
import com.chriscartland.garage.domain.repository.WearCompanionRepository
import kotlinx.coroutines.flow.Flow

/**
 * Observes whether the paired watch has the Wear OS app installed.
 * Cold pass-through of the repository flow (matches the upstream shape —
 * the repo owns polling; there is no cached StateFlow to pass by
 * reference here).
 */
class ObserveWatchAppStatusUseCase(
    private val wearCompanionRepository: WearCompanionRepository,
) {
    operator fun invoke(): Flow<WatchAppStatus> = wearCompanionRepository.observeWatchAppStatus()
}
