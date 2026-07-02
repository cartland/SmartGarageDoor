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

package com.chriscartland.garage.usecase

import com.chriscartland.garage.domain.repository.FeatureAllowlistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Observes per-feature access decisions for the current user.
 *
 * Returns `Flow<Boolean?>` — null means "not yet known" (pre-fetch,
 * fetch failed, or signed out). The UI layer gates closed on null
 * so users never see a flash-of-allowed before the first fetch
 * resolves. The repository owns the underlying StateFlow (ADR-022);
 * this UseCase is a thin per-feature view of it.
 */
class ObserveFeatureAccessUseCase(
    private val featureAllowlistRepository: FeatureAllowlistRepository,
) {
    fun functionList(): Flow<Boolean?> = featureAllowlistRepository.allowlist.map { it?.functionList }

    fun developer(): Flow<Boolean?> = featureAllowlistRepository.allowlist.map { it?.developer }
}
