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

import com.chriscartland.garage.domain.repository.SnoozeRepository

/**
 * Screen-entry snooze revalidate (STATUS_CACHE_PLAN.md D3). The
 * fetch-TTL policy lives in the repository; this is the VM-facing
 * pass-through. Contrast [FetchSnoozeStatusUseCase], which always hits
 * the network (pull-to-refresh / manual refresh).
 */
class RevalidateSnoozeStatusUseCase(
    private val snoozeRepository: SnoozeRepository,
) {
    suspend operator fun invoke() = snoozeRepository.revalidateSnoozeIfStale()
}
